package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class TransportChainBuilder private constructor() {
    companion object {
        private const val TAG = "NexusChat/ChainBuilder"
        private const val CHAIN_LOCAL_PORT = 14500
        @Volatile private var instance: TransportChainBuilder? = null
        fun getInstance(): TransportChainBuilder =
            instance ?: synchronized(this) {
                instance ?: TransportChainBuilder().also { instance = it }
            }
    }

    data class ChainLink(
        val type: String,
        val host: String,
        val port: Int,
        val priority: Int = 0,
        val weight: Int = 1
    )

    data class TransportChain(
        val name: String,
        val links: List<ChainLink>,
        val healthCheckUrl: String = "https://check.torproject.org/",
        val timeoutMs: Long = 15000,
        val circuitPadding: Boolean = true,
        val obfuscateTraffic: Boolean = true
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val chains = mutableListOf<TransportChain>()
    private val chainHealth = ConcurrentHashMap<String, Boolean>()
    private var activeChain: TransportChain? = null
    private var healthJob: Job? = null

    private val builtinChains = listOf(
        TransportChain("tor-direct", listOf(
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 10)
        ), "https://check.torproject.org/"),
        TransportChain("v2ray-direct", listOf(
            ChainLink("v2ray", "127.0.0.1", V2RayService.SOCKS5_PORT, 10)
        ), "https://www.google.com/"),
        TransportChain("tor-v2ray", listOf(
            ChainLink("v2ray", "127.0.0.1", V2RayService.SOCKS5_PORT, 5),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 5)
        ), circuitPadding = true, obfuscateTraffic = true),
        TransportChain("v2ray-tor", listOf(
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 5),
            ChainLink("v2ray", "127.0.0.1", V2RayService.SOCKS5_PORT, 5)
        ), circuitPadding = true, obfuscateTraffic = true),
        TransportChain("snowflake-tor", listOf(
            ChainLink("snowflake", "127.0.0.1", 9900, 5),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 5)
        )),
        TransportChain("obfs4-tor", listOf(
            ChainLink("obfs4", "127.0.0.1", 9443, 5),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 5)
        )),
        TransportChain("meek-tor", listOf(
            ChainLink("meek", "127.0.0.1", 9555, 5),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 5)
        )),
        TransportChain("tor-v2ray-tor", listOf(
            ChainLink("v2ray", "127.0.0.1", V2RayService.SOCKS5_PORT, 4),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 4),
            ChainLink("tor", "127.0.0.1", TorService.SOCKS_PORT, 4)
        ), circuitPadding = true),
    )

    fun start() {
        chains.clear()
        chains.addAll(builtinChains)
        activeChain = chains.first()
        healthJob = scope.launch {
            while (true) {
                checkAllChains()
                selectBestChain()
                delay(30000)
            }
        }
        Log.i(TAG, "ChainBuilder started: ${chains.size} chains")
    }

    private suspend fun checkAllChains() = withContext(Dispatchers.IO) {
        for (chain in chains) {
            val healthy = checkChain(chain)
            chainHealth[chain.name] = healthy
            val status = if (healthy) "healthy" else "dead"
            Log.d(TAG, "Chain ${chain.name}: $status")
        }
    }

    private fun checkChain(chain: TransportChain): Boolean {
        return try {
            val proxy = resolveChainProxy(chain)
            if (proxy == null) return false
            val socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", proxy), 3000)
            socket.close()
            true
        } catch (e: Exception) { false }
    }

    private fun resolveChainProxy(chain: TransportChain): Int? {
        return when (chain.links.firstOrNull()?.type) {
            "tor" -> TorService.SOCKS_PORT
            "v2ray" -> V2RayService.SOCKS5_PORT
            "snowflake" -> 9900
            "obfs4" -> 9443
            "meek" -> 9555
            "xray" -> XRaySubprocess.XRAY_SOCKS_PORT
            else -> null
        }
    }

    private fun selectBestChain() {
        val healthy = chains.filter { chainHealth[it.name] == true }
        if (healthy.isEmpty()) return
        val sorted = healthy.sortedByDescending { it.links.firstOrNull()?.priority ?: 0 }
        val best = sorted.first()
        if (best.name != activeChain?.name) {
            Log.i(TAG, "Chain switch: ${activeChain?.name} -> ${best.name}")
            activeChain = best
        }
    }

    fun getActiveChain(): TransportChain? = activeChain

    fun getChainProxy(): Proxy? {
        val chain = activeChain ?: return null
        val port = resolveChainProxy(chain) ?: return null
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))
    }

    fun setActiveChain(name: String): Boolean {
        val chain = chains.find { it.name == name } ?: return false
        activeChain = chain
        return true
    }

    fun addCustomChain(chain: TransportChain) {
        chains.add(chain)
        Log.i(TAG, "Custom chain added: ${chain.name}")
    }

    fun getChains(): List<TransportChain> = chains.toList()
    fun getChainStatuses(): Map<String, Boolean> = chainHealth.toMap()

    fun stop() {
        healthJob?.cancel()
        Log.i(TAG, "ChainBuilder stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
