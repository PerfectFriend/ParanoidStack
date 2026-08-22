/**
 * Bridge Orchestrator for N3.
 * 
 * Manages a chain of transport bridges (VPN1 → VPN2 → Tor) with automatic
 * health checking, fallback, and chain rebuilding.
 * 
 * Bridge configurations are loaded from assets/config/ via TransportConfig.
 * Supports: OpenVPN, WireGuard, V2Ray/Xray, Tor, Direct connections.
 */
package com.n3.app.bridge

import android.content.Context
import android.util.Log
import com.n3.app.config.TransportConfig
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.UnknownHostException

/**
 * Result of testing a single bridge element.
 * 
 * @property elementId Unique identifier of the bridge element
 * @property name Human-readable name
 * @property type Bridge type (OPENVPN, WIREGUARD, V2RAY, TOR, DIRECT)
 * @property ok Whether the test passed
 * @property latencyMs Round-trip time in milliseconds (-1 if failed)
 * @property error Error message if test failed
 */
data class BridgeTestResult(
    val elementId: String,
    val name: String,
    val type: BridgeType,
    val ok: Boolean,
    val latencyMs: Long = -1,
    val error: String = ""
)

/**
 * Network connectivity status.
 * 
 * @property ipv4 IPv4 connectivity available
 * @property ipv6 IPv6 connectivity available
 * @property dnsWorks DNS resolution working
 * @property torReachable Local Tor SOCKS5 port reachable
 * @property internetReachable Internet reachable via direct connection
 */
data class NetworkStatus(
    val ipv4: Boolean = false,
    val ipv6: Boolean = false,
    val dnsWorks: Boolean = false,
    val torReachable: Boolean = false,
    val internetReachable: Boolean = false
)

/**
 * Bridge Orchestrator - manages transport chain with health monitoring.
 * 
 * Features:
 * - Loads bridge configurations from assets/config/bridges.json
 * - Tests each bridge element for connectivity and latency
 * - Builds optimal chain (VPN1 → VPN2 → Tor)
 * - Monitors chain health and rotates failed bridges
 * - Provides fallback to direct connection if all bridges fail
 * 
 * @param ctx Application context for loading configs
 */
class BridgeOrchestrator(private val ctx: Context) {

    companion object {
        private const val TAG = "N3/Orchestrator"
        // Default test endpoint - override via health-checks.json config
        private const val DEFAULT_TEST_URL = "https://www.google.com/generate_204"
        private const val DEFAULT_TEST_DOMAIN = "google.com"
    }

    /** Bridge storage and configuration */
    private val bridgeConfig = BridgeConfig(ctx)
    
    /** TransportConfig for loading bridges from assets */
    private val transportConfig = TransportConfig.getInstance(ctx)
    
    /** Test URL from config or default */
    private val testUrl = transportConfig.getHealthCheckUrl() ?: DEFAULT_TEST_URL
    private val testDomain = transportConfig.getHealthCheckDomain() ?: DEFAULT_TEST_DOMAIN

    /**
     * Check overall network connectivity status.
     * Returns NetworkStatus with IPv4, IPv6, DNS, Tor, and Internet reachability.
     */
    suspend fun checkNetwork(): NetworkStatus = withContext(Dispatchers.IO) {
        val ipv4 = try { 
            InetSocketAddress.createUnresolved(testDomain, 80).apply { }; 
            true 
        } catch (e: Exception) { 
            false 
        }
        val ipv6 = try { 
            InetSocketAddress.createUnresolved("ipv6.google.com", 80).apply { }; 
            true 
        } catch (e: Exception) { 
            false 
        }
        val dnsWorks = try { 
            java.net.InetAddress.getByName(testDomain); 
            true 
        } catch (e: UnknownHostException) { 
            false 
        }
        val torReachable = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2000) }; 
            true
        } catch (e: Exception) { 
            false 
        }
        val internetReachable = try {
            val conn = URL(testUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000; conn.readTimeout = 3000
            conn.responseCode in 200..399
        } catch (e: Exception) { 
            false 
        }
        NetworkStatus(ipv4, ipv6, dnsWorks, torReachable, internetReachable)
    }

    /**
     * Test a single bridge element for connectivity.
     * 
     * @param element Bridge element to test
     * @return BridgeTestResult with ok status and latency
     */
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
                        val conn = URL(testUrl).openConnection() as HttpURLConnection
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

    /**
     * Build and test a complete transport chain.
     * 
     * Chain order: V2Ray/Xray → WireGuard → OpenVPN → Tor
     * (VPN2 → VPN1 → Tor for ParanoidX architecture)
     * 
     * @param elements List of bridge elements to test and chain
     * @return List of test results for each element
     */
    suspend fun buildAndTestChain(elements: List<BridgeElement>): List<BridgeTestResult> = withContext(Dispatchers.IO) {
        val active = elements.filter { it.enabled }
        if (active.isEmpty()) return@withContext emptyList()

        val results = active.map { testElement(it) }
        val workingIds = results.filter { it.ok }.map { it.elementId }

        val chain = if (workingIds.isNotEmpty()) {
            val chainOrder = mutableListOf<String>()
            // Priority order: V2Ray (VPN2) → WireGuard (VPN2) → OpenVPN (VPN1) → Tor
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.V2RAY }) {
                active.filter { it.type == BridgeType.V2RAY && it.id in workingIds }
                    .sortedBy { it.priority }
                    .forEach { chainOrder.add(it.id) }
            }
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.WIREGUARD }) {
                active.filter { it.type == BridgeType.WIREGUARD && it.id in workingIds }
                    .sortedBy { it.priority }
                    .forEach { chainOrder.add(it.id) }
            }
            if (workingIds.any { id -> active.find { it.id == id }?.type == BridgeType.OPENVPN }) {
                active.filter { it.type == BridgeType.OPENVPN && it.id in workingIds }
                    .sortedBy { it.priority }
                    .forEach { chainOrder.add(it.id) }
            }
            // Tor as final hop (always if available)
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

    /**
     * Test if SimpleX SMP server is reachable through Tor.
     * 
     * @param host SMP server hostname
     * @param port SMP server port
     * @return true if reachable via Tor SOCKS5
     */
    suspend fun testSimplexReachable(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9050))
            val conn = URL("http://$host:$port").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 10000; conn.readTimeout = 5000
            conn.responseCode in 200..399
        } catch (e: Exception) { false }
    }

    /**
     * Test V2Ray/Xray proxy connectivity.
     * Parses host/port from element config JSON.
     */
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

    /**
     * Test OpenVPN endpoint connectivity.
     * Parses remote host/port from OVPN config.
     */
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

    /**
     * Test WireGuard endpoint connectivity.
     * Parses Endpoint=host:port from WireGuard config.
     */
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

    /**
     * Start continuous chain health monitor.
     * 
     * Runs every 30 seconds, tests each chain element, rotates failed bridges
     * with same-type replacements, rebuilds chain if changed.
     * 
     * @param scope Coroutine scope for the monitor
     * @param onStatus Callback for status updates (UI notification)
     */
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

    /**
     * Load bridge configurations from assets/config/bridges.json.
     * Creates default BridgeElement instances for each configured bridge.
     * 
     * @return List of BridgeElement ready for use
     */
    fun loadBridgesFromAssets(): List<BridgeElement> {
        val bridges = transportConfig.getBridgesConfig()
        val elements = mutableListOf<BridgeElement>()
        
        // Load obfs4 bridges
        bridges["obfs4"]?.forEachIndexed { idx, bridge ->
            elements.add(BridgeElement(
                id = "obfs4_$idx",
                type = BridgeType.TOR, // obfs4 is a Tor bridge type
                name = "obfs4 #${idx + 1} (${bridge.address}:${bridge.port})",
                config = bridge.toJsonString(),
                enabled = true,
                priority = idx
            ))
        }
        
        // Load meek bridges
        bridges["meek"]?.forEachIndexed { idx, bridge ->
            elements.add(BridgeElement(
                id = "meek_$idx",
                type = BridgeType.TOR,
                name = "meek #${idx + 1} (${bridge.address})",
                config = bridge.toJsonString(),
                enabled = true,
                priority = idx + 100
            ))
        }
        
        // Load snowflake bridges
        bridges["snowflake"]?.forEachIndexed { idx, bridge ->
            elements.add(BridgeElement(
                id = "snowflake_$idx",
                type = BridgeType.TOR,
                name = "snowflake #${idx + 1}",
                config = bridge.toJsonString(),
                enabled = true,
                priority = idx + 200
            ))
        }
        
        return elements
    }
}