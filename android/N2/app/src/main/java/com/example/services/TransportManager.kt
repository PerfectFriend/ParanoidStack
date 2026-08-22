package com.example.services

import android.content.Context
import android.util.Log
import com.example.config.TransportConfig
import kotlinx.coroutines.*
import okhttp3.*
import okio.Buffer
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * TransportType - Enum representing available transport protocols.
 * 
 * TOR - Standard Tor SOCKS proxy
 * SNOWFLAKE - Snowflake WebRTC-based transport
 * DOMAIN_FRONT - Domain fronting via HTTP tunnel
 * DIRECT_TCP - Direct TCP connection (no proxy)
 * WIREGUARD - WireGuard VPN tunnel
 * BRIDGE - Tor with pluggable transports (obfs4, meek, etc.)
 * CHAIN_PROXY - Chained proxy (VPN -> Tor -> etc.)
 */
enum class TransportType {
    TOR, SNOWFLAKE, DOMAIN_FRONT, DIRECT_TCP, WIREGUARD, BRIDGE, CHAIN_PROXY
}

/**
 * TransportStatus - Current status of a transport.
 * 
 * @param type The transport type
 * @param available Whether the transport is currently available
 * @param latencyMs Measured latency in milliseconds
 * @param error Error message if unavailable
 */
data class TransportStatus(
    val type: TransportType,
    val available: Boolean,
    val latencyMs: Int = 0,
    val error: String? = null
)

/**
 * TransportStats - Statistics for a transport.
 * 
 * Thread-safe counters using atomic operations via ConcurrentHashMap replace.
 */
data class TransportStats(
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val failures: Int = 0,
    val avgLatencyMs: Int = 0
)

/**
 * TransportManager - Central transport management for NexusChat.
 * 
 * This class manages multiple transport protocols, performs health checks,
 * selects the best available transport, and provides OkHttpClient instances
 * configured for each transport type.
 * 
 * Key responsibilities:
 * - Load transport configurations from assets (via TransportConfig)
 * - Build OkHttpClient for each transport with appropriate proxy settings
 * - Periodic health checks of all transports
 * - Automatic best-transport selection based on latency
 * - Traffic statistics tracking
 * - Integration with TrafficPadding for cover traffic
 * - Protocol obfuscation via TrafficObfuscatorInterceptor
 * 
 * Configuration sources (all loaded from assets/config/):
 * - bridges.json: Tor bridge configurations
 * - stun-turn.json: STUN/TURN servers for WebRTC
 * - front-domains.json: Domain fronting hosts with weights
 * - health-checks.json: Health check endpoints and schedule
 * - snowflake.json: Snowflake broker and client config
 * 
 * Thread-safe singleton with coroutine-based async operations.
 */
class TransportManager private constructor(private val ctx: Context) {

    companion object {
        private const val TAG = "NexusChat/Transport"
        @Volatile private var instance: TransportManager? = null
        
        /**
         * Get singleton instance, initializing with context on first call.
         */
        fun getInstance(ctx: Context): TransportManager {
            return instance ?: synchronized(this) {
                instance ?: TransportManager(ctx.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Initialize with context (call early in Application.onCreate).
         */
        fun initialize(ctx: Context) {
            getInstance(ctx)
        }
        
        /**
         * Reset instance (for testing or config reload).
         */
        fun reset() {
            instance?.stop()
            instance = null
        }
    }

    // TransportConfig instance for loading all configs from assets
    private val transportConfig = TransportConfig.getInstance(ctx)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val clientPool = ConcurrentHashMap<TransportType, OkHttpClient>()
    
    private val _isEnabled = AtomicBoolean(true)
    private val transportStats = ConcurrentHashMap<TransportType, TransportStats>()
    
    @Volatile private var currentTransport: TransportType = TransportType.TOR
    
    // Transport preference order (highest priority first)
    // BRIDGE > TOR > SNOWFLAKE > DOMAIN_FRONT > CHAIN_PROXY > DIRECT_TCP > WIREGUARD
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
    
    // Configuration loaded from assets
    private val healthCheckEndpoints = transportConfig.getHealthCheckEndpoints()
    private val healthCheckSchedule = transportConfig.getHealthCheckSchedule()
    private val stunServers = transportConfig.getStunServers()
    private val turnServers = transportConfig.getTurnServers()
    private val iceDefaults = transportConfig.getIceDefaults()

    /** Get currently active transport type. */
    val activeTransport: TransportType get() = currentTransport
    val isEnabled: Boolean get() = _isEnabled.get()

    /** Set bridge orchestrator for BRIDGE transport type. */
    fun setBridgeOrchestrator(orchestrator: BridgeOrchestrator) {
        bridgeOrchestrator = orchestrator
    }

    /**
     * Start transport manager - begins periodic health checks.
     * 
     * Health check interval is loaded from assets/config/health-checks.json
     * (default: 15 minutes, configurable).
     */
    fun start() {
        val intervalMinutes = healthCheckSchedule.intervalMinutes
        healthCheckJob = scope.launch {
            // Initial check on startup
            if (healthCheckSchedule.runOnStartup) {
                checkAllTransports()
                selectBestTransport()
            }
            
            while (true) {
                delay(intervalMinutes * 60 * 1000L)
                checkAllTransports()
                selectBestTransport()
            }
        }
        Log.i(TAG, "TransportManager started (health check interval: ${intervalMinutes}min)")
    }

    /** Stop transport manager and cleanup. */
    fun stop() {
        healthCheckJob?.cancel()
        scope.cancel()
        clientPool.values.forEach { it.dispatcher.executorService.shutdown() }
        clientPool.clear()
        Log.i(TAG, "TransportManager stopped")
    }

    /**
     * Get OkHttpClient for the currently active transport.
     * Creates client on first use and caches it.
     * 
     * @param timeoutSec Connection timeout in seconds
     * @return Pair of (OkHttpClient, TransportType)
     */
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

    /**
     * Get OkHttpClient for a specific transport type.
     * 
     * @param type Transport type to get client for
     * @param timeoutSec Connection timeout in seconds
     * @return Configured OkHttpClient
     */
    fun getClientForTransport(type: TransportType, timeoutSec: Int = 30): OkHttpClient {
        return clientPool.getOrPut(type) {
            buildClientForTransport(type, timeoutSec)
        }
    }

    /**
     * Build OkHttpClient configured for a specific transport type.
     * 
     * Applies:
     * - Proxy settings (SOCKS/HTTP) based on transport type
     * - Timeouts from config
     * - TrafficObfuscatorInterceptor for header/body obfuscation
     * - Retry on connection failure
     * 
     * Port numbers for local proxies are defined in companion objects
     * of respective services (TorService, ChainProxy, etc.).
     */
    private fun buildClientForTransport(type: TransportType, timeoutSec: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(timeoutSec.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(TrafficObfuscatorInterceptor())
        
        when (type) {
            TransportType.TOR -> {
                // Standard Tor SOCKS proxy
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050)))
            }
            TransportType.BRIDGE -> {
                // Tor with pluggable transports (obfs4, meek, etc.)
                val port = bridgeOrchestrator?.getBridgeConfig()?.localListenPort ?: 9050
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            }
            TransportType.CHAIN_PROXY -> {
                // Chained proxy (VPN -> Tor -> etc.)
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9051)))
            }
            TransportType.DOMAIN_FRONT -> {
                // Domain fronting via HTTP tunnel
                builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8080)))
            }
            TransportType.SNOWFLAKE -> {
                // Snowflake WebRTC proxy (runs on port 9900)
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9900)))
            }
            TransportType.WIREGUARD -> {
                // WireGuard VPN tunnel proxy
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9100)))
            }
            TransportType.DIRECT_TCP -> {
                // No proxy - direct connection
            }
        }
        return builder.build()
    }

    /**
     * Check all transports in order, updating their status.
     * Runs periodically via healthCheckJob.
     */
    private suspend fun checkAllTransports() = withContext(Dispatchers.IO) {
        for (type in transportOrder) {
            val status = checkTransport(type)
            transportStatuses[type] = status
            Log.d(TAG, "Transport $type: available=${status.available} latency=${status.latencyMs}ms ${status.error?.let { "error=$it" } ?: ""}")
        }
    }

    /**
     * Check a single transport's availability and latency.
     * 
     * Performs:
     * 1. Local proxy connectivity test (Socket connect to 127.0.0.1:port)
     * 2. Remote health check via configured endpoint (from assets/health-checks.json)
     * 
     * @param type Transport type to check
     * @return TransportStatus with availability and latency
     */
    private fun checkTransport(type: TransportType): TransportStatus {
        val start = System.currentTimeMillis()
        return try {
            val client = buildClientForTransport(type, 5)
            
            // Test local proxy connectivity
            if (type == TransportType.TOR || type == TransportType.BRIDGE || type == TransportType.CHAIN_PROXY) {
                val port = when (type) {
                    TransportType.TOR -> 9050
                    TransportType.BRIDGE -> bridgeOrchestrator?.getBridgeConfig()?.localListenPort ?: 9050
                    TransportType.CHAIN_PROXY -> 9051
                    else -> 9050
                }
                val socket = java.net.Socket()
                socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
                socket.close()
            }
            
            // Use first health check endpoint (tor-check-api) for remote verification
            val healthCheckUrl = healthCheckEndpoints.firstOrNull { it.name == "tor-check-api" }?.url 
                ?: "https://check.torproject.org/api/ip"
            
            val request = Request.Builder().url(healthCheckUrl).head().build()
            val response = client.newCall(request).execute()
            val latency = (System.currentTimeMillis() - start).toInt()
            response.close()
            TransportStatus(type, true, latency)
        } catch (e: Exception) {
            TransportStatus(type, false, error = e.message)
        }
    }

    /**
     * Select the best available transport based on priority and latency.
     * 
     * Selection logic:
     * 1. Prefer BRIDGE if orchestrator is ready and has active protocol
     * 2. Otherwise, pick lowest-latency available transport
     * 3. If none available, disable all transports
     */
    private fun selectBestTransport() {
        val available = transportStatuses.filter { it.value.available }
        if (available.isEmpty()) {
            Log.e(TAG, "No transports available")
            _isEnabled.set(false)
            return
        }
        _isEnabled.set(true)
        
        // Priority 1: BRIDGE with active protocol
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
        
        // Priority 2: Lowest latency among available
        val sorted = available.entries.sortedBy { it.value.latencyMs }
        val best = sorted.first().value
        if (best.type != currentTransport) {
            Log.i(TAG, "Switching transport: ${currentTransport.name} -> ${best.type.name} (${best.latencyMs}ms)")
            currentTransport = best.type
        }
    }

    /**
     * Record bytes sent for statistics.
     * Uses atomic compare-and-set via ConcurrentHashMap.replace for thread safety.
     */
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

    /** Record bytes received for statistics. */
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

    /** Record a failure for statistics. */
    fun recordFailure(type: TransportType) {
        while (true) {
            val current = transportStats[type] ?: TransportStats()
            val updated = current.copy(failures = current.failures + 1)
            if (transportStats.replace(type, current, updated)) break
        }
    }

    /** Get all transport statistics. */
    fun getAllStats(): Map<TransportType, TransportStats> = transportStats.toMap()
    
    /** Get all transport statuses. */
    fun getAllStatuses(): Map<TransportType, TransportStatus> = transportStatuses.toMap()

    /**
     * TrafficObfuscatorInterceptor - OkHttp interceptor for traffic obfuscation.
     * 
     * Applies multiple obfuscation techniques:
     * - Random User-Agent rotation
     * - Header obfuscation via ProtocolObfuscator
     * - Request body padding (random bytes)
     * - Timing jitter (random sleep before response)
     * 
     * This helps defeat traffic analysis and fingerprinting.
     */
    class TrafficObfuscatorInterceptor : Interceptor {
        private val rng = SecureRandom()
        private val obfuscator = ProtocolObfuscator.getInstance()
        private val padder = TrafficPadding.getInstance()
        
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val body = original.body
            
            // Get obfuscated headers from ProtocolObfuscator
            val obfuscatedHeaders = obfuscator.obfuscateHttpHeaders(emptyMap())
            
            val builder = original.newBuilder()
            
            // Random User-Agent to avoid fingerprinting
            builder.header("User-Agent", randomUserAgent())
            
            // Obfuscated standard headers
            builder.header("Accept", obfuscatedHeaders["Accept"] ?: "text/html,*/*")
            builder.header("Accept-Language", obfuscatedHeaders["Accept-Language"] ?: "en-US,en;q=0.9")
            builder.header("Accept-Encoding", obfuscatedHeaders["Accept-Encoding"] ?: "gzip, deflate, br")
            builder.header("Cache-Control", obfuscatedHeaders["Cache-Control"] ?: "no-cache")
            
            // Randomly add optional headers
            if (rng.nextFloat() > 0.3f) {
                builder.header("Pragma", "no-cache")
            }
            if (rng.nextFloat() > 0.7f) {
                builder.header("DNT", if (rng.nextBoolean()) "1" else "0")
            }
            
            // Pad request body if present (integrates TrafficPadding)
            val newBody = if (body != null && body.contentLength() > 0) {
                obfuscateRequestBody(body)
            } else body
            
            builder.method(original.method, newBody)
            val request = builder.build()
            
            // Execute request
            val response = chain.proceed(request)
            
            // Add timing jitter to defeat timing analysis
            val jitter = rng.nextInt(100)
            Thread.sleep(jitter.toLong())
            
            return response
        }

        /** Pool of realistic User-Agent strings for rotation. */
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

        /**
         * Obfuscate request body by adding random padding.
         * Uses TrafficPadding.padWithRandom() for consistent padding logic.
         */
        private fun obfuscateRequestBody(body: RequestBody): RequestBody {
            val contentLen = body.contentLength()
            if (contentLen <= 0) return body
            
            val buffer = Buffer()
            try {
                body.writeTo(buffer)
            } catch (_: Exception) {
                return body
            }
            val originalData = buffer.readByteArray()
            
            // Apply padding via TrafficPadding (integrates the previously unused class)
            val paddedData = padder.padWithRandom(
                originalData,
                minPad = TrafficPadding.PaddingConfig().minPadding,
                maxPad = TrafficPadding.PaddingConfig().maxPadding
            )
            
            return RequestBody.create(body.contentType(), paddedData)
        }
    }

    /** Get preferred transport for .onion addresses (always Tor). */
    fun getTransportForOnion(): TransportType = TransportType.TOR
    
    /** Get current transport for clearnet addresses. */
    fun getTransportForClearnet(): TransportType = currentTransport

    /** Get SOCKS proxy for Tor. */
    fun getProxyForSocks(): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
    
    /** Get STUN servers for WebRTC (from assets config). */
    fun getStunServers(): List<String> = stunServers
    
    /** Get TURN servers for WebRTC (from assets config). */
    fun getTurnServers(): List<TurnEntry> = turnServers
    
    /** Get ICE transport defaults. */
    fun getIceDefaults() = iceDefaults
    
    /** Get domain fronting front domains (from assets config). */
    fun getFrontDomains(): List<String> = transportConfig.getFrontDomains()
    
    /** Select weighted random front domain. */
    fun selectFrontDomain(): String = transportConfig.selectFrontDomain()
    
    /** Get Snowflake configuration. */
    fun getSnowflakeConfig() = transportConfig.getSnowflakeFullConfig()
    
    /** Get bridge configuration. */
    fun getBridgesConfig() = transportConfig.getBridgesConfig()
    
    /** Force reload of all configs from assets. */
    fun reloadConfigs() {
        transportConfig.reloadAll()
        // Note: OkHttpClients in pool will use new config on next creation
        clientPool.clear()
        Log.i(TAG, "Configs reloaded from assets")
    }
}