package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

enum class TransportType {
    TOR, SNOWFLAKE, DOMAIN_FRONT, DIRECT_TCP, WIREGUARD, BRIDGE, CHAIN_PROXY
}

data class TransportStatus(
    val type: TransportType,
    val available: Boolean,
    val latencyMs: Int = 0,
    val error: String? = null
)

data class TransportStats(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val failures: Int = 0,
    val avgLatencyMs: Int = 0
)

class TransportManager private constructor(private val ctx: Context) {
    companion object {
        private const val TAG = "NexusChat/Transport"
        private const val CHECK_URL = "https://check.torproject.org/api/ip"
        private const val SNOWFLAKE_BROKER = "https://snowflake-broker.torproject.net/"
        @Volatile private var instance: TransportManager? = null
        fun getInstance(ctx: Context): TransportManager =
            instance ?: synchronized(this) {
                instance ?: TransportManager(ctx.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val clientPool = ConcurrentHashMap<TransportType, OkHttpClient>()

    private val _isEnabled = AtomicBoolean(true)
    private val transportStats = ConcurrentHashMap<TransportType, TransportStats>()
    @Volatile private var currentTransport: TransportType = TransportType.TOR
    private val transportOrder = listOf(
        TransportType.BRIDGE,
        TransportType.TOR,
        TransportType.SNOWFLAKE,
        TransportType.DOMAIN_FRONT,
        TransportType.CHAIN_PROXY,
        TransportType.DIRECT_TCP,
        TransportType.WIREGUARD
    )
    private val transportStatuses = ConcurrentHashMap<TransportType, TransportStatus>()
    private var healthCheckJob: Job? = null
    @Volatile private var bridgeOrchestrator: BridgeOrchestrator? = null
    private val obfuscator = ProtocolObfuscator.getInstance()
    private val padder = TrafficPadding.getInstance()

    val activeTransport: TransportType get() = currentTransport
    val isEnabled: Boolean get() = _isEnabled.get()

    fun setBridgeOrchestrator(orchestrator: BridgeOrchestrator) {
        bridgeOrchestrator = orchestrator
    }

    fun start() {
        healthCheckJob = scope.launch {
            while (true) {
                checkAllTransports()
                selectBestTransport()
                delay(15000)
            }
        }
        Log.i(TAG, "TransportManager started")
    }

    fun stop() {
        healthCheckJob?.cancel()
        scope.cancel()
        Log.i(TAG, "TransportManager stopped")
    }

    fun getClient(timeoutSec: Int = 30): Pair<OkHttpClient, TransportType> {
        if (!_isEnabled.get()) {
            throw IllegalStateException("All transports unavailable")
        }
        val transport = currentTransport
        val client = clientPool.getOrPut(transport) {
            buildClientForTransport(transport, timeoutSec)
        }
        return Pair(client, transport)
    }

    fun getClientForTransport(type: TransportType, timeoutSec: Int = 30): OkHttpClient {
        return clientPool.getOrPut(type) {
            buildClientForTransport(type, timeoutSec)
        }
    }

    private fun buildClientForTransport(type: TransportType, timeoutSec: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(TrafficObfuscatorInterceptor())
        when (type) {
            TransportType.TOR -> {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT)))
            }
            TransportType.BRIDGE -> {
                val port = bridgeOrchestrator?.getBridgeConfig()?.localListenPort ?: TorService.SOCKS_PORT
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            }
            TransportType.CHAIN_PROXY -> {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", ChainProxy.CHAIN_LOCAL_PORT)))
            }
            TransportType.DOMAIN_FRONT -> {
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", TorService.HTTP_TUNNEL_PORT)))
            }
            TransportType.SNOWFLAKE -> {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9900)))
            }
            TransportType.WIREGUARD -> {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9100)))
            }
            TransportType.DIRECT_TCP -> {}
        }
        return builder.build()
    }

    private suspend fun checkAllTransports() = withContext(Dispatchers.IO) {
        for (type in transportOrder) {
            val status = checkTransport(type)
            transportStatuses[type] = status
            Log.d(TAG, "Transport $type: available=${status.available} latency=${status.latencyMs}ms")
        }
    }

    private fun checkTransport(type: TransportType): TransportStatus {
        val start = System.currentTimeMillis()
        return try {
            val client = buildClientForTransport(type, 5)
            if (type == TransportType.TOR || type == TransportType.BRIDGE || type == TransportType.CHAIN_PROXY) {
                val port = when (type) {
                    TransportType.TOR -> TorService.SOCKS_PORT
                    TransportType.BRIDGE -> bridgeOrchestrator?.getBridgeConfig()?.localListenPort ?: TorService.SOCKS_PORT
                    TransportType.CHAIN_PROXY -> ChainProxy.CHAIN_LOCAL_PORT
                    else -> TorService.SOCKS_PORT
                }
                val s = java.net.Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), 2000)
                s.close()
            }
            val request = Request.Builder().url("https://check.torproject.org/").head().build()
            val response = client.newCall(request).execute()
            val latency = (System.currentTimeMillis() - start).toInt()
            response.close()
            TransportStatus(type, true, latency)
        } catch (e: Exception) {
            TransportStatus(type, false, error = e.message)
        }
    }

    private fun selectBestTransport() {
        val available = transportStatuses.filter { it.value.available }
        if (available.isEmpty()) {
            Log.e(TAG, "No transports available")
            _isEnabled.set(false)
            return
        }
        _isEnabled.set(true)
        if (available.containsKey(TransportType.BRIDGE) &&
            bridgeOrchestrator?.isReady == true &&
            bridgeOrchestrator?.activeProtocol != null) {
            val bridge = available[TransportType.BRIDGE]!!
            if (currentTransport != TransportType.BRIDGE) {
                Log.i(TAG, "Bridge transport active via ${bridgeOrchestrator?.activeProtocol}")
                currentTransport = TransportType.BRIDGE
            }
            return
        }
        val sorted = available.entries.sortedBy { it.value.latencyMs }
        val best = sorted.first().value
        if (best.type != currentTransport) {
            Log.i(TAG, "Switching transport: ${currentTransport.name} -> ${best.type.name} (${best.latencyMs}ms)")
            currentTransport = best.type
        }
    }

    fun recordBytesSent(type: TransportType, bytes: Long) {
        while (true) {
            val current = transportStats[type] ?: TransportStats()
            val updated = current.copy(
                bytesSent = current.bytesSent + bytes,
                packetsSent = current.packetsSent + 1
            )
            if (transportStats.replace(type, current, updated)) break
        }
    }

    fun recordBytesReceived(type: TransportType, bytes: Long) {
        while (true) {
            val current = transportStats[type] ?: TransportStats()
            val updated = current.copy(
                bytesReceived = current.bytesReceived + bytes,
                packetsReceived = current.packetsReceived + 1
            )
            if (transportStats.replace(type, current, updated)) break
        }
    }

    fun recordFailure(type: TransportType) {
        while (true) {
            val current = transportStats[type] ?: TransportStats()
            val updated = current.copy(failures = current.failures + 1)
            if (transportStats.replace(type, current, updated)) break
        }
    }

    fun getAllStats(): Map<TransportType, TransportStats> = transportStats.toMap()
    fun getAllStatuses(): Map<TransportType, TransportStatus> = transportStatuses.toMap()

    class TrafficObfuscatorInterceptor : Interceptor {
        private val rng = SecureRandom()
        private val obfuscator = ProtocolObfuscator.getInstance()
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val body = original.body
            val obfuscatedHeaders = obfuscator.obfuscateHttpHeaders(emptyMap())
            val builder = original.newBuilder()
            builder.header("User-Agent", randomUserAgent())
            builder.header("Accept", obfuscatedHeaders["Accept"] ?: "text/html,*/*")
            builder.header("Accept-Language", obfuscatedHeaders["Accept-Language"] ?: "en-US,en;q=0.9")
            builder.header("Accept-Encoding", obfuscatedHeaders["Accept-Encoding"] ?: "gzip, deflate, br")
            builder.header("Cache-Control", obfuscatedHeaders["Cache-Control"] ?: "no-cache")
            if (rng.nextFloat() > 0.3f) {
                builder.header("Pragma", "no-cache")
            }
            if (rng.nextFloat() > 0.7f) {
                builder.header("DNT", if (rng.nextBoolean()) "1" else "0")
            }
            val newBody = if (body != null && body.contentLength() > 0) {
                obfuscateRequestBody(body)
            } else body
            builder.method(original.method, newBody)
            val request = builder.build()
            val response = chain.proceed(request)
            val jitter = rng.nextInt(100)
            Thread.sleep(jitter.toLong())
            return response
        }

        private fun randomUserAgent(): String {
            val uas = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/119.0.0.0 Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.6099.230 Mobile Safari/537.36",
                "Mozilla/5.0 (Linux; Android 13; SM-S908B) AppleWebKit/537.36 Chrome/120.0.6099.144 Mobile Safari/537.36",
            )
            return uas[rng.nextInt(uas.size)]
        }

        private fun obfuscateRequestBody(body: RequestBody): RequestBody {
            val contentLen = body.contentLength()
            if (contentLen <= 0) return body
            val paddingLen = rng.nextInt(256) + 32
            val padding = ByteArray(paddingLen)
            rng.nextBytes(padding)
            val buffer = okio.Buffer()
            try { body.writeTo(buffer) } catch (_: Exception) { return body }
            val originalData = buffer.readByteArray()
            val padded = originalData + obfuscator.normalizePacketSize(padding, 256)
            return RequestBody.create(body.contentType(), padded)
        }
    }

    fun getTransportForOnion(): TransportType = TransportType.TOR
    fun getTransportForClearnet(): TransportType = currentTransport

    fun getProxyForSocks(): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT))
}
