package com.n3.app.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.UnknownHostException

data class BridgeTestResult(
    val elementId: String,
    val name: String,
    val type: BridgeType,
    val ok: Boolean,
    val latencyMs: Long = -1,
    val error: String = ""
)

data class NetworkStatus(
    val ipv4: Boolean = false,
    val ipv6: Boolean = false,
    val dnsWorks: Boolean = false,
    val torReachable: Boolean = false,
    val internetReachable: Boolean = false
)

class BridgeOrchestrator(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/Orchestrator"
        private const val TEST_URL = "https://www.google.com/generate_204"
        private const val TEST_DOMAIN = "google.com"
    }

    private val bridgeConfig = BridgeConfig(ctx)

    suspend fun checkNetwork(): NetworkStatus = withContext(Dispatchers.IO) {
        val ipv4 = try { InetSocketAddress.createUnresolved(TEST_DOMAIN, 80).apply { }; true } catch (e: Exception) { false }
        val ipv6 = try { InetSocketAddress.createUnresolved("ipv6.google.com", 80).apply { }; true } catch (e: Exception) { false }
        val dnsWorks = try { java.net.InetAddress.getByName(TEST_DOMAIN); true } catch (e: UnknownHostException) { false }
        val torReachable = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2000) }; true
        } catch (e: Exception) { false }
        val internetReachable = try {
            val conn = URL(TEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 3000
            conn.responseCode in 200..399
        } catch (e: Exception) { false }
        NetworkStatus(ipv4, ipv6, dnsWorks, torReachable, internetReachable)
    }

    suspend fun testElement(element: BridgeElement): BridgeTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            when (element.type) {
                BridgeType.TOR -> {
                    val ok = try {
                        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2000) }; true
                    } catch (e: Exception) { false }
                    BridgeTestResult(element.id, element.name, element.type, ok, System.currentTimeMillis() - start)
                }
                BridgeType.DIRECT -> {
                    val ok = try {
                        val conn = URL(TEST_URL).openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000; conn.readTimeout = 3000; conn.responseCode in 200..399
                    } catch (e: Exception) { false }
                    BridgeTestResult(element.id, element.name, element.type, ok, System.currentTimeMillis() - start)
                }
                BridgeType.V2RAY -> {
                    val result = testV2RayProxy(element)
                    BridgeTestResult(element.id, element.name, element.type, result.first, result.second, if (result.first) "" else "v2ray_unreachable")
                }
                BridgeType.OPENVPN -> {
                    val result = testOvpnEndpoint(element)
                    BridgeTestResult(element.id, element.name, element.type, result.first, result.second, if (result.first) "" else "openvpn_unreachable")
                }
                BridgeType.WIREGUARD -> {
                    val result = testWgEndpoint(element)
                    BridgeTestResult(element.id, element.name, element.type, result.first, result.second, if (result.first) "" else "wg_unreachable")
                }
            }
        } catch (e: Exception) {
            BridgeTestResult(element.id, element.name, element.type, false, -1, e.message ?: "unknown")
        }
    }

    suspend fun buildAndTestChain(elements: List<BridgeElement>): List<BridgeTestResult> = withContext(Dispatchers.IO) {
        val active = elements.filter { it.enabled }
        if (active.isEmpty()) return@withContext emptyList()

        val results = active.map { testElement(it) }
        val workingIds = results.filter { it.ok }.map { it.elementId }

        val chain = if (workingIds.isNotEmpty()) {
            val chainOrder = mutableListOf<String>()
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.V2RAY }) {
                active.filter { it.type == BridgeType.V2RAY && it.id in workingIds }
                    .forEach { chainOrder.add(it.id) }
            }
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.WIREGUARD }) {
                active.filter { it.type == BridgeType.WIREGUARD && it.id in workingIds }
                    .forEach { chainOrder.add(it.id) }
            }
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.OPENVPN }) {
                active.filter { it.type == BridgeType.OPENVPN && it.id in workingIds }
                    .forEach { chainOrder.add(it.id) }
            }
            val torReachable = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 1000) }; true
            } catch (e: Exception) { false }
            if (torReachable) { chainOrder.add("tor") }
            bridgeConfig.setActiveChain(chainOrder)
            chainOrder
        } else {
            bridgeConfig.clearChain()
            emptyList()
        }
        Log.i(TAG, "Active chain: $chain")
        results
    }

    suspend fun testSimplexReachable(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
            val conn = URL("http://$host:$port").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 5000
            conn.responseCode in 200..399
        } catch (e: Exception) { false }
    }

    private fun testV2RayProxy(element: BridgeElement): Pair<Boolean, Long> = try {
        val start = System.currentTimeMillis()
        val cfg = org.json.JSONObject(element.config)
        val host = cfg.optString("host", "").ifEmpty { cfg.optString("address", "") }
        val port = cfg.optInt("port", 0)
        if (host.isNotEmpty() && port > 0) {
            val ok = try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 3000) }; true
            } catch (e: Exception) { false }
            Pair(ok, System.currentTimeMillis() - start)
        } else {
            Pair(false, 0)
        }
    } catch (e: Exception) { Pair(false, 0) }

    private fun testOvpnEndpoint(element: BridgeElement): Pair<Boolean, Long> = try {
        val start = System.currentTimeMillis()
        val lines = element.config.lines()
        val remote = lines.find { it.trim().startsWith("remote", ignoreCase = true) }
        if (remote != null) {
            val parts = remote.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val host = parts[1]
                val port = if (parts.size >= 3) parts[2].toIntOrNull() ?: 1194 else 1194
                val ok = try {
                    java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 3000) }; true
                } catch (e: Exception) { false }
                Pair(ok, System.currentTimeMillis() - start)
            } else Pair(false, 0)
        } else Pair(false, 0)
    } catch (e: Exception) { Pair(false, 0) }

    private fun testWgEndpoint(element: BridgeElement): Pair<Boolean, Long> = try {
        val start = System.currentTimeMillis()
        val lines = element.config.lines()
        val ep = lines.find { it.trim().startsWith("Endpoint", ignoreCase = true) }
        if (ep != null) {
            val addr = ep.substringAfter("=").trim()
            val parts = addr.split(":")
            if (parts.size >= 2) {
                val host = parts[0].trim()
                val port = parts[1].trim().toIntOrNull() ?: 51820
                val ok = try {
                    java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 3000) }; true
                } catch (e: Exception) { false }
                Pair(ok, System.currentTimeMillis() - start)
            } else Pair(false, 0)
        } else Pair(false, 0)
    } catch (e: Exception) { Pair(false, 0) }

    fun startChainMonitor(scope: CoroutineScope, onStatus: (String) -> Unit) {
        scope.launch {
            while (isActive) {
                delay(30000)
                val chain = bridgeConfig.getActiveChain().toMutableList()
                if (chain.isEmpty()) continue
                val allElements = bridgeConfig.getAll().filter { it.enabled }
                var changed = false
                for (i in chain.indices) {
                    val id = chain[i]
                    if (id == "tor") {
                        val torOk = try {
                            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 3000) }; true
                        } catch (e: Exception) { false }
                        if (!torOk) { changed = true; onStatus("Tor unreachable, rebuilding chain") }
                        continue
                    }
                    val element = allElements.find { it.id == id } ?: continue
                    val result = testElement(element)
                    if (!result.ok) {
                        val sameType = allElements.filter { it.type == element.type && it.id != id }
                        val replacement = sameType.firstOrNull()
                        if (replacement != null) {
                            chain[i] = replacement.id
                            onStatus("Rotated ${element.name} -> ${replacement.name}")
                        } else {
                            chain.removeAt(i)
                            onStatus("Removed failed ${element.name}, no replacement")
                        }
                        changed = true
                    }
                }
                if (changed) {
                    val rebuildResult = buildAndTestChain(allElements)
                    val ok = rebuildResult.any { it.ok }
                    onStatus(if (ok) "Chain rebuilt" else "Chain degraded")
                }
            }
        }
    }
}
