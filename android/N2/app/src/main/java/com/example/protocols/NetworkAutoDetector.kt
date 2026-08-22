package com.example.protocols

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.net.*

data class NetworkState(
    val isOnline: Boolean = false,
    val hasDns: Boolean = false,
    val hasWebSocket: Boolean = false,
    val publicIp: String = "",
    val blockedDomains: List<String> = emptyList(),
    val blockedProtocols: List<String> = emptyList(),
    val detectedFirewall: String = "none",
    val latencyMs: Int = 0,
    val mtu: Int = 1500,
    val availableInterfaces: List<String> = emptyList(),
    val lastScanTime: Long = 0L,
    val scanCount: Int = 0,
    val connectivityQuality: ConnectivityQuality = ConnectivityQuality.UNKNOWN
)

data class BypassStrategy(
    val name: String,
    val priority: Int,
    val enabled: Boolean = false,
    val protocolStack: List<String> = emptyList(),
    val estimatedOverhead: Int = 0,
    val description: String = ""
)

data class TransportConfig(
    val transportId: String,
    val transportName: String,
    val score: Int,
    val latencyMs: Int,
    val protocolOrder: List<String>,
    val description: String = ""
)

enum class ConnectivityQuality { UNKNOWN, POOR, FAIR, GOOD, EXCELLENT }

data class TransportRecommendation(val transportId: String, val reason: String, val confidence: Int)

class NetworkAutoDetector(
    private val context: Context,
    private val registry: ProtocolRegistry,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _selectedStrategy = MutableStateFlow<BypassStrategy?>(null)
    val selectedStrategy: StateFlow<BypassStrategy?> = _selectedStrategy.asStateFlow()

    private val _optimalTransport = MutableStateFlow<TransportConfig?>(null)
    val optimalTransport: StateFlow<TransportConfig?> = _optimalTransport.asStateFlow()

    private var scanJob: Job? = null

    private val testDomains = listOf(
        "google.com", "github.com", "torproject.org",
        "duckduckgo.com", "wikipedia.org", "matrix.org"
    )

    private val censorshipTestDomains = listOf(
        "en.wikipedia.org", "telegram.org", "torproject.org",
        "proton.me", "duckduckgo.com", "cloudflare.com"
    )

    /// Полный цикл детекции
    suspend fun runFullDetection(): NetworkState {
        onLog("[AutoDetect] Starting full network detection...")

        val state = withContext(Dispatchers.IO) {
            val online = checkOnline()
            val dns = checkDns()
            val ws = checkWebSocket()
            val ip = getPublicIp()
            val blocked = checkBlockedDomains()
            val firewall = detectFirewall()
            val latency = measureLatency("1.1.1.1")
            val mtu = detectMtu()
            val ifaces = listNetworkInterfaces()
            val count = _networkState.value.scanCount + 1
            val quality = assessQuality(online, latency, blocked.size, firewall)

            onLog("[AutoDetect] Online=$online DNS=$dns WebSocket=$ws IP=$ip Blocked=${blocked.size} Firewall=$firewall Latency=${latency}ms Quality=$quality")
            NetworkState(
                isOnline = online, hasDns = dns, hasWebSocket = ws, publicIp = ip,
                blockedDomains = blocked, blockedProtocols = emptyList(), detectedFirewall = firewall,
                latencyMs = latency, mtu = mtu, availableInterfaces = ifaces,
                lastScanTime = System.currentTimeMillis(), scanCount = count,
                connectivityQuality = quality
            )
        }
        _networkState.value = state
        return state
    }

    /// Автоматический выбор стратегии обхода цензуры
    suspend fun selectBypassStrategy(state: NetworkState = _networkState.value): BypassStrategy {
        onLog("[AutoDetect] Selecting bypass strategy...")

        val strategies = buildStrategies(state)
        val scored = strategies.sortedByDescending { it.priority }

        val selected = scored.firstOrNull() ?: BypassStrategy(
            "direct", 0, true, emptyList(), 0, "Прямое подключение (нет блокировок)"
        )

        _selectedStrategy.value = selected
        onLog("[AutoDetect] Selected: ${selected.name} (${selected.protocolStack})")
        return selected
    }

    /// Выбор оптимального транспортного протокола
    suspend fun selectOptimalTransport(state: NetworkState = _networkState.value): TransportConfig {
        onLog("[AutoDetect] Selecting optimal transport...")

        val candidates = benchmarkTransports(state)
        val best = candidates.maxByOrNull { it.score } ?: TransportConfig(
            "tor", "Tor", 50, 0, listOf("tor"), "Fallback: Tor"
        )

        _optimalTransport.value = best
        onLog("[AutoDetect] Optimal: ${best.transportName} (score=${best.score})")
        return best
    }

    /// Полный автопилот: детекция → обход → транспорт
    suspend fun autoConfigure(): Triple<NetworkState, BypassStrategy, TransportConfig> {
        val state = runFullDetection()
        val strategy = selectBypassStrategy(state)
        val transport = selectOptimalTransport(state)
        return Triple(state, strategy, transport)
    }

    // ========== PRIVATE METHODS ==========

    private fun buildStrategies(state: NetworkState): List<BypassStrategy> {
        val list = mutableListOf<BypassStrategy>()

        // 1. Прямое подключение (если нет блокировок)
        if (state.blockedDomains.size <= 1 && state.isOnline) {
            list.add(BypassStrategy("direct", 100, false, emptyList(), 0,
                "Прямое — без прокси"))
        }

        // 2. Tor (если доступен)
        if (registry.get("tor")?.status == ProtocolStatus.INSTALLED || state.isOnline) {
            list.add(BypassStrategy("tor_only", 85, false, listOf("tor"), 30,
                "Tor SOCKS5 — луковая маршрутизация"))
        }

        // 3. V2Ray/Tor цепочка
        list.add(BypassStrategy("v2ray_tor", 80, false, listOf("v2ray", "tor"), 50,
            "Xray → Tor — многослойный прокси"))

        // 4. Shadowsocks
        list.add(BypassStrategy("shadowsocks", 75, false, listOf("shadowsocks"), 20,
            "Shadowsocks — лёгкий шифрованный прокси"))

        // 5. WireGuard
        list.add(BypassStrategy("wireguard", 70, false, listOf("wireguard"), 10,
            "WireGuard — быстрый VPN-туннель"))

        // 6. I2P
        list.add(BypassStrategy("i2p", 60, false, listOf("i2p"), 60,
            "I2P — чесночная маршрутизация"))

        // 7. Yggdrasil mesh
        list.add(BypassStrategy("yggdrasil", 55, false, listOf("yggdrasil"), 20,
            "Yggdrasil — IPv6 mesh-сеть"))

        // 8. Многослойная цепочка (макс анонимность)
        list.add(BypassStrategy("max_anonymity", 40, false,
            listOf("tor", "v2ray", "i2p"), 120,
            "Tor → V2Ray → I2P — максимальная анонимность"))

        return list
    }

    private suspend fun benchmarkTransports(state: NetworkState): List<TransportConfig> {
        val results = mutableListOf<TransportConfig>()
        val baseLatency = state.latencyMs.coerceAtLeast(1)

        // Тест Tor
        val torLatency = measureTransportLatency("127.0.0.1", 9050)
        results.add(TransportConfig("tor", "Tor",
            score = (85 - torLatency / 50).coerceIn(0, 100),
            torLatency, listOf("tor"), "Tor SOCKS5"))

        // Тест V2Ray
        val v2rayLatency = measureTransportLatency("127.0.0.1", 10808)
        results.add(TransportConfig("v2ray", "Xray/V2Ray",
            score = (75 - v2rayLatency / 50).coerceIn(0, 100),
            v2rayLatency, listOf("v2ray"), "Xray SOCKS5"))

        // Если нет запущенных транспортов, оцениваем по состоянию сети
        if (results.all { it.latencyMs <= 0 }) {
            results.clear()
            results.add(TransportConfig("direct", "Direct (no proxy)",
                score = if (state.isOnline) 100 else 0, 0, emptyList(), "Прямое"))

            if (state.isOnline) {
                results.add(TransportConfig("tor", "Tor (estimated)",
                    80, baseLatency * 3, listOf("tor"), "Tor (оценка)"))
                results.add(TransportConfig("shadowsocks", "Shadowsocks (estimated)",
                    70, baseLatency * 2, listOf("shadowsocks"), "SS (оценка)"))
            }
        }

        return results
    }

    private fun checkOnline(): Boolean = try {
        val addr = InetAddress.getByName("1.1.1.1")
        addr.isReachable(3000)
    } catch (_: Exception) { false }

    private fun checkDns(): Boolean = try {
        InetAddress.getByName("google.com")
        true
    } catch (_: Exception) { false }

    private fun getPublicIp(): String = try {
        val conn = URL("https://api.ipify.org").openConnection() as? HttpURLConnection ?: return ""
        conn.connectTimeout = 3000; conn.readTimeout = 3000
        val ip = conn.inputStream.bufferedReader().readText().trim()
        conn.disconnect(); ip
    } catch (_: Exception) {
        try {
            val conn = URL("https://ifconfig.me").openConnection() as? HttpURLConnection ?: return ""
            conn.connectTimeout = 3000; conn.readTimeout = 3000
            val ip = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect(); ip
        } catch (_: Exception) { "" }
    }

    private fun checkBlockedDomains(): List<String> {
        val blocked = mutableListOf<String>()
        for (domain in censorshipTestDomains) {
            try {
                val addr = InetAddress.getByName(domain)
                if (!addr.isReachable(2000)) blocked.add(domain)
            } catch (_: Exception) { blocked.add(domain) }
        }
        return blocked
    }

    private fun detectFirewall(): String = try {
        val domains = mapOf(
            "google.com" to "Clean",
            "twitter.com" to "Clean",
            "youtube.com" to "Clean",
            "telegram.org" to "Clean"
        )
        val blockedCount = domains.keys.count { domain ->
            try {
                !InetAddress.getByName(domain).isReachable(2000)
            } catch (_: Exception) { true }
        }
        when {
            blockedCount >= 3 -> "GFW-like (Deep Packet Inspection)"
            blockedCount >= 1 -> "Restricted (Selective blocking)"
            else -> "none"
        }
    } catch (_: Exception) { "unknown" }

    private suspend fun measureLatency(host: String): Int = withContext(Dispatchers.IO) {
        val times = (1..3).map {
            try {
                val start = System.nanoTime()
                InetAddress.getByName(host).isReachable(2000)
                (System.nanoTime() - start) / 1_000_000
            } catch (_: Exception) { 9999 }
        }
        times.filter { it < 9999 }.let { if (it.isEmpty()) 9999 else it.average().toInt() }
    }

    private fun detectMtu(): Int = try {
        val ifaces = NetworkInterface.getNetworkInterfaces()
        var mtu = 1500
        while (ifaces.hasMoreElements()) {
            val iface = ifaces.nextElement()
            if (iface.isUp && !iface.isLoopback) {
                mtu = iface.mtu
                break
            }
        }
        mtu.coerceIn(1280, 9000)
    } catch (_: Exception) { 1500 }

    private fun listNetworkInterfaces(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().asSequence().iterator().asSequence()
            .filter { it.isUp }
            .map { "${it.name} (${it.displayName})" }
            .toList()
    } catch (_: Exception) { emptyList() }

    private suspend fun measureTransportLatency(host: String, port: Int): Int =
        withContext(Dispatchers.IO) {
            try {
                val start = System.nanoTime()
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 2000)
                sock.close()
                ((System.nanoTime() - start) / 1_000_000).toInt()
            } catch (_: Exception) { -1 }
        }

    private suspend fun checkWebSocket(): Boolean = withContext(Dispatchers.IO) {
        try {
            val host = "echo.websocket.org"
            val sock = Socket()
            sock.connect(InetSocketAddress(host, 80), 3000)
            val writer = sock.getOutputStream().bufferedWriter()
            val reader = sock.getInputStream().bufferedReader()
            val key = "dGhlIHNhbXBsZSBub25jZQ=="
            writer.write("GET / HTTP/1.1\r\n")
            writer.write("Host: $host\r\n")
            writer.write("Upgrade: websocket\r\n")
            writer.write("Connection: Upgrade\r\n")
            writer.write("Sec-WebSocket-Key: $key\r\n")
            writer.write("Sec-WebSocket-Version: 13\r\n")
            writer.write("\r\n")
            writer.flush()
            val response = reader.readLine()
            sock.close()
            response?.contains("101") == true
        } catch (_: Exception) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress("echo.websocket.org", 443), 3000)
                sock.close()
                true
            } catch (_: Exception) { false }
        }
    }

    private fun assessQuality(online: Boolean, latency: Int, blockedCount: Int, firewall: String): ConnectivityQuality {
        return when {
            online && latency < 100 && blockedCount == 0 && firewall == "none" -> ConnectivityQuality.EXCELLENT
            online && latency < 300 && blockedCount <= 2 && firewall == "none" -> ConnectivityQuality.GOOD
            online && latency < 1000 && blockedCount <= 4 -> ConnectivityQuality.FAIR
            !online -> ConnectivityQuality.POOR
            else -> ConnectivityQuality.UNKNOWN
        }
    }

    fun startBackgroundScanning(intervalMinutes: Long = 5) {
        stopBackgroundScanning()
        registerNetworkCallback()
        scanJob = scope.launch {
            while (isActive) {
                runFullDetection()
                delay(intervalMinutes * 60_000)
            }
        }
        onLog("[AutoDetect] Background scanning started every ${intervalMinutes}min")
    }

    fun stopBackgroundScanning() {
        scanJob?.cancel()
        scanJob = null
        unregisterNetworkCallback()
        onLog("[AutoDetect] Background scanning stopped")
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onLog("[AutoDetect] Network available: $network")
                scope.launch { runFullDetection() }
            }
            override fun onLost(network: Network) {
                onLog("[AutoDetect] Network lost: $network")
                _networkState.value = _networkState.value.copy(isOnline = false, connectivityQuality = ConnectivityQuality.POOR)
                scope.launch { runFullDetection() }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                val speed = when {
                    caps.linkDownstreamBandwidthKbps >= 50_000 -> ConnectivityQuality.EXCELLENT
                    caps.linkDownstreamBandwidthKbps >= 10_000 -> ConnectivityQuality.GOOD
                    caps.linkDownstreamBandwidthKbps >= 1_000 -> ConnectivityQuality.FAIR
                    else -> ConnectivityQuality.POOR
                }
                onLog("[AutoDetect] Capabilities changed: internet=$hasInternet validated=$hasValidated speed=$speed")
                if (hasInternet && hasValidated) {
                    _networkState.value = _networkState.value.copy(isOnline = true, connectivityQuality = speed)
                }
            }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build(), callback)
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        try { cm?.unregisterNetworkCallback(cb) } catch (_: Exception) { Log.w("NetworkAutoDetector", "unregisterNetworkCallback failed") }
        networkCallback = null
    }

    fun recommendTransport(): TransportRecommendation {
        val state = _networkState.value
        return when {
            state.connectivityQuality == ConnectivityQuality.EXCELLENT ->
                TransportRecommendation("direct", "Network is excellent — no proxy needed", 95)
            state.connectivityQuality == ConnectivityQuality.GOOD ->
                TransportRecommendation("shadowsocks", "Good network — shadowsocks is light and fast", 80)
            state.connectivityQuality == ConnectivityQuality.FAIR ->
                TransportRecommendation("tor", "Fair network — Tor provides reliable bypass", 65)
            !state.isOnline ->
                TransportRecommendation("none", "No connectivity — wait for network", 0)
            state.detectedFirewall != "none" && state.hasWebSocket ->
                TransportRecommendation("v2ray", "Firewall detected with WebSocket support — V2Ray+WS recommended", 85)
            state.detectedFirewall != "none" ->
                TransportRecommendation("tor", "Firewall detected — Tor is the safest option", 75)
            else ->
                TransportRecommendation("tor", "Unknown conditions — falling back to Tor", 50)
        }
    }

    fun dispose() {
        stopBackgroundScanning()
        scope.cancel()
    }
}
