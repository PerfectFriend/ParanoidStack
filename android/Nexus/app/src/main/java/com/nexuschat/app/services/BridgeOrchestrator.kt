package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class BridgeOrchestrator private constructor(private val ctx: Context) {
    companion object {
        private const val TAG = "NexusChat/BridgeOrch"
        private const val PROBE_TIMEOUT_MS = 8000L
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L
        private const val OBFS4_PORT = 9443
        private const val MEEK_PORT = 9555
        private const val SNOWFLAKE_PORT = 9900

        @Volatile private var instance: BridgeOrchestrator? = null
        fun getInstance(ctx: Context): BridgeOrchestrator =
            instance ?: synchronized(this) {
                instance ?: BridgeOrchestrator(ctx.applicationContext).also { instance = it }
            }
    }

    enum class BridgeProtocol {
        OBFS4, MEEK, SNOWFLAKE, DOMAIN_FRONT, DIRECT_TOR
    }

    data class BridgeStatus(
        val protocol: BridgeProtocol,
        val available: Boolean,
        val latencyMs: Int = 0,
        val error: String = "",
        val localPort: Int = 0
    )

    data class BridgeConfig(
        val protocol: BridgeProtocol = BridgeProtocol.DIRECT_TOR,
        val address: String = "",
        val port: Int = 443,
        val fingerprint: String = "",
        val transportPlugin: String = "",
        val localListenPort: Int = 0,
        val args: Map<String, String> = emptyMap()
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val statuses = ConcurrentHashMap<BridgeProtocol, BridgeStatus>()
    private var activeBridge: BridgeProtocol? = null
    private var healthJob: Job? = null
    private var bridgeConfigs = listOf<BridgeConfig>()
    private var onStatusChange: ((BridgeProtocol, Boolean) -> Unit)? = null
    private val _isReady = AtomicBoolean(false)
    private var obfs4Transport: Obfs4Transport? = null
    private var meekTransport: MeekTransport? = null

    val isReady: Boolean get() = _isReady.get()
    val activeProtocol: BridgeProtocol? get() = activeBridge
    val allStatuses: Map<BridgeProtocol, BridgeStatus> get() = statuses.toMap()

    fun setStatusListener(listener: (BridgeProtocol, Boolean) -> Unit) {
        onStatusChange = listener
    }

    fun start() {
        Log.i(TAG, "BridgeOrchestrator starting — launching transports")
        scope.launch {
            startLocalTransports()
            probeAllTransports()
            selectBestBridge()
            _isReady.set(true)
            healthJob = scope.launch {
                while (true) {
                    delay(HEALTH_CHECK_INTERVAL_MS)
                    ensureLocalTransports()
                    probeAllTransports()
                    selectBestBridge()
                }
            }
        }
    }

    fun stop() {
        healthJob?.cancel()
        obfs4Transport?.stop()
        meekTransport?.stop()
        scope.cancel()
        _isReady.set(false)
        Log.i(TAG, "BridgeOrchestrator stopped")
    }

    private fun startLocalTransports() {
        val obfsCfg = bridgeConfigs.find { it.protocol == BridgeProtocol.OBFS4 }
        if (obfsCfg != null) {
            obfs4Transport = Obfs4Transport.getInstance { ok, msg ->
                Log.i(TAG, "Obfs4 status: $ok $msg")
            }
            obfs4Transport?.start(Obfs4Transport.Obfs4Config(
                bridgeAddress = obfsCfg.address,
                bridgePort = obfsCfg.port,
                nodeId = obfsCfg.fingerprint,
                localPort = OBFS4_PORT
            ))
            Log.i(TAG, "Obfs4 transport launched on :$OBFS4_PORT")
        }
        val meekCfg = bridgeConfigs.find { it.protocol == BridgeProtocol.MEEK }
        if (meekCfg != null) {
            meekTransport = MeekTransport.getInstance { ok, msg ->
                Log.i(TAG, "Meek status: $ok $msg")
            }
            meekTransport?.start(MeekTransport.MeekConfig(
                frontDomain = meekCfg.address,
                frontPort = meekCfg.port,
                localPort = MEEK_PORT
            ))
            Log.i(TAG, "Meek transport launched on :$MEEK_PORT")
        }
    }

    private fun ensureLocalTransports() {
        if (obfs4Transport == null) {
            val obfsCfg = bridgeConfigs.find { it.protocol == BridgeProtocol.OBFS4 }
            if (obfsCfg != null) {
                obfs4Transport = Obfs4Transport.getInstance { ok, msg -> }
                obfs4Transport?.start(Obfs4Transport.Obfs4Config(
                    bridgeAddress = obfsCfg.address, bridgePort = obfsCfg.port,
                    nodeId = obfsCfg.fingerprint, localPort = OBFS4_PORT
                ))
            }
        }
        if (meekTransport == null) {
            val meekCfg = bridgeConfigs.find { it.protocol == BridgeProtocol.MEEK }
            if (meekCfg != null) {
                meekTransport = MeekTransport.getInstance { ok, msg -> }
                meekTransport?.start(MeekTransport.MeekConfig(
                    frontDomain = meekCfg.address, frontPort = meekCfg.port,
                    localPort = MEEK_PORT
                ))
            }
        }
    }

    fun getBridgeConfig(): BridgeConfig? {
        val proto = activeBridge ?: return null
        return bridgeConfigs.find { it.protocol == proto }
    }

    fun getBridgeTorrcLines(): String {
        val cfg = bridgeConfigs.find { it.protocol == activeBridge } ?: return ""
        return buildString {
            appendLine("UseBridges 1")
            if (cfg.transportPlugin.isNotEmpty() && cfg.localListenPort > 0) {
                appendLine("ClientTransportPlugin ${cfg.protocol.name.lowercase()} exec $cfg.transportPlugin")
            }
            when (cfg.protocol) {
                BridgeProtocol.OBFS4 -> {
                    val argsStr = cfg.args.entries.joinToString(" ") { "${it.key}=${it.value}" }
                    appendLine("Bridge obfs4 ${cfg.address}:${cfg.port} ${cfg.fingerprint} $argsStr")
                }
                BridgeProtocol.MEEK -> {
                    appendLine("Bridge meek ${cfg.address}:${cfg.port} ${cfg.fingerprint}")
                }
                BridgeProtocol.SNOWFLAKE -> {
                    appendLine("Bridge snowflake ${cfg.address}:${cfg.port} ${cfg.fingerprint}")
                }
                BridgeProtocol.DOMAIN_FRONT -> {
                    appendLine("Bridge ${cfg.address}:${cfg.port} ${cfg.fingerprint}")
                }
                BridgeProtocol.DIRECT_TOR -> {}
            }
        }
    }

    fun forceBridge(protocol: BridgeProtocol) {
        scope.launch {
            val cfg = bridgeConfigs.find { it.protocol == protocol }
            if (cfg != null) {
                if (protocol == BridgeProtocol.OBFS4 && obfs4Transport == null) {
                    startLocalTransports()
                }
                activeBridge = protocol
                Log.i(TAG, "Bridge manually set: ${protocol.name}")
                onStatusChange?.invoke(protocol, true)
            }
        }
    }

    fun addBridge(bridge: BridgeConfig) {
        bridgeConfigs = bridgeConfigs + bridge
    }

    fun clearBridges() {
        bridgeConfigs = emptyList()
        activeBridge = null
        statuses.clear()
    }

    private suspend fun probeAllTransports() = withContext(Dispatchers.IO) {
        val probes = listOf(
            { probeDirectTor() },
            { probeObfs4() },
            { probeMeek() },
            { probeSnowflake() },
            { probeDomainFront() },
        )
        probes.forEach { probe ->
            try { probe() } catch (e: Exception) {
                Log.w(TAG, "Probe error: ${e.message}")
            }
        }
    }

    private fun probeDirectTor() {
        val start = System.currentTimeMillis()
        try {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT), PROBE_TIMEOUT_MS.toInt())
            s.close()
            val latency = (System.currentTimeMillis() - start).toInt()
            statuses[BridgeProtocol.DIRECT_TOR] = BridgeStatus(
                BridgeProtocol.DIRECT_TOR, true, latency, localPort = TorService.SOCKS_PORT
            )
            Log.d(TAG, "Direct Tor: available (${latency}ms)")
        } catch (e: Exception) {
            statuses[BridgeProtocol.DIRECT_TOR] = BridgeStatus(
                BridgeProtocol.DIRECT_TOR, false, error = e.message ?: "timeout"
            )
        }
    }

    private fun probeObfs4() {
        try {
            val start = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", OBFS4_PORT), PROBE_TIMEOUT_MS.toInt())
            s.close()
            val latency = (System.currentTimeMillis() - start).toInt()
            statuses[BridgeProtocol.OBFS4] = BridgeStatus(
                BridgeProtocol.OBFS4, true, latency, localPort = OBFS4_PORT
            )
            Log.d(TAG, "obfs4: available (${latency}ms)")
        } catch (e: Exception) {
            statuses[BridgeProtocol.OBFS4] = BridgeStatus(
                BridgeProtocol.OBFS4, false, error = e.message ?: "not running"
            )
        }
    }

    private fun probeMeek() {
        try {
            val start = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", MEEK_PORT), PROBE_TIMEOUT_MS.toInt())
            s.close()
            val latency = (System.currentTimeMillis() - start).toInt()
            statuses[BridgeProtocol.MEEK] = BridgeStatus(
                BridgeProtocol.MEEK, true, latency, localPort = MEEK_PORT
            )
        } catch (e: Exception) {
            statuses[BridgeProtocol.MEEK] = BridgeStatus(
                BridgeProtocol.MEEK, false, error = e.message ?: "not running"
            )
        }
    }

    private fun probeSnowflake() {
        try {
            val start = System.currentTimeMillis()
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", SNOWFLAKE_PORT), PROBE_TIMEOUT_MS.toInt())
            s.close()
            val latency = (System.currentTimeMillis() - start).toInt()
            statuses[BridgeProtocol.SNOWFLAKE] = BridgeStatus(
                BridgeProtocol.SNOWFLAKE, true, latency, localPort = SNOWFLAKE_PORT
            )
        } catch (e: Exception) {
            statuses[BridgeProtocol.SNOWFLAKE] = BridgeStatus(
                BridgeProtocol.SNOWFLAKE, false, error = e.message ?: "not running"
            )
        }
    }

    private fun probeDomainFront() {
        try {
            val start = System.currentTimeMillis()
            val url = java.net.URL("https://cloudflare.com/cdn-cgi/trace")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = PROBE_TIMEOUT_MS.toInt()
            conn.readTimeout = PROBE_TIMEOUT_MS.toInt()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            val latency = (System.currentTimeMillis() - start).toInt()
            statuses[BridgeProtocol.DOMAIN_FRONT] = BridgeStatus(
                BridgeProtocol.DOMAIN_FRONT, code == 200, latency, localPort = 443
            )
        } catch (e: Exception) {
            statuses[BridgeProtocol.DOMAIN_FRONT] = BridgeStatus(
                BridgeProtocol.DOMAIN_FRONT, false, error = e.message ?: "failed"
            )
        }
    }

    private fun selectBestBridge() {
        val available = statuses.filter { it.value.available }
        if (available.isEmpty()) {
            Log.w(TAG, "No bridge transports available — falling back to direct Tor")
            activeBridge = BridgeProtocol.DIRECT_TOR
            return
        }
        val sorted = available.entries.sortedBy { it.value.latencyMs }
        val best = sorted.first().value
        val prev = activeBridge
        activeBridge = best.protocol
        if (prev != best.protocol) {
            Log.i(TAG, "Bridge switch: ${prev?.name ?: "none"} -> ${best.protocol.name} (${best.latencyMs}ms)")
            onStatusChange?.invoke(best.protocol, true)
        }
    }
}
