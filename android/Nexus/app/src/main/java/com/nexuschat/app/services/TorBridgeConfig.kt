package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

class TorBridgeConfig private constructor() {
    companion object {
        private const val TAG = "NexusChat/TorBridges"
        private const val BRIDGE_DB_URL = "https://bridges.torproject.org/bridges"
        @Volatile private var instance: TorBridgeConfig? = null
        fun getInstance(): TorBridgeConfig =
            instance ?: synchronized(this) {
                instance ?: TorBridgeConfig().also { instance = it }
            }
    }

    enum class BridgeType { OBF4, MEEK, SNOWFLAKE, WEAK, HTTPS }

    data class BridgeLine(
        val type: BridgeType,
        val address: String,
        val port: Int,
        val fingerprint: String,
        val args: Map<String, String> = emptyMap()
    ) {
        fun toTorrc(): String = when (type) {
            BridgeType.OBF4 -> "Bridge obfs4 $address:$port $fingerprint ${args.entries.joinToString(" ") { "${it.key}=${it.value}" }}"
            BridgeType.MEEK -> "Bridge meek $address:$port $fingerprint"
            BridgeType.SNOWFLAKE -> "Bridge snowflake $address:$port $fingerprint"
            BridgeType.WEAK -> "Bridge weak $address:$port $fingerprint"
            BridgeType.HTTPS -> "Bridge $address:$port $fingerprint"
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val bridges = mutableListOf<BridgeLine>()

    private val defaultObfs4Bridges = listOf(
        BridgeLine(BridgeType.OBF4, "85.31.186.98", 443, "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", mapOf("cert" to "F5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")),
        BridgeLine(BridgeType.OBF4, "192.95.36.142", 443, "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", mapOf("cert" to "A5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")),
        BridgeLine(BridgeType.OBF4, "38.229.1.18", 80, "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", mapOf("cert" to "B5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")),
        BridgeLine(BridgeType.OBF4, "85.31.186.98", 443, "B2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", mapOf("cert" to "C5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "1")),
    )

    private val defaultMeekBridges = listOf(
        BridgeLine(BridgeType.MEEK, "meek.azureedge.net", 443, "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8"),
    )

    fun getBridges(type: BridgeType? = null): List<BridgeLine> {
        return if (type != null) bridges.filter { it.type == type } else bridges.toList()
    }

    fun addBridge(bridge: BridgeLine) {
        bridges.add(bridge)
        Log.i(TAG, "Bridge added: ${bridge.type.name} ${bridge.address}:${bridge.port}")
    }

    fun removeBridge(address: String) {
        bridges.removeAll { it.address == address }
    }

    fun clearBridges() {
        bridges.clear()
    }

    fun loadDefaultBridges(includeType: Set<BridgeType> = setOf(BridgeType.OBF4, BridgeType.MEEK)) {
        if (BridgeType.OBF4 in includeType) bridges.addAll(defaultObfs4Bridges)
        if (BridgeType.MEEK in includeType) bridges.addAll(defaultMeekBridges)
        Log.i(TAG, "Loaded ${bridges.size} default bridges")
    }

    suspend fun fetchBridgesFromServer(email: String? = null): List<BridgeLine> = withContext(Dispatchers.IO) {
        try {
            val url = if (email != null) "$BRIDGE_DB_URL?email=$email" else BRIDGE_DB_URL
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val response = conn.inputStream.bufferedReader().readText()
            val parsed = parseBridgeResponse(response)
            bridges.addAll(parsed)
            Log.i(TAG, "Fetched ${parsed.size} bridges from server")
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Bridge fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseBridgeResponse(response: String): List<BridgeLine> {
        return response.lines().filter { it.startsWith("obfs4") }.mapNotNull { line ->
            try {
                val parts = line.split(" ")
                if (parts.size >= 4) {
                    val addrParts = parts[1].split(":")
                    BridgeLine(
                        type = BridgeType.OBF4,
                        address = addrParts[0],
                        port = addrParts.getOrNull(1)?.toIntOrNull() ?: 443,
                        fingerprint = parts[2],
                        args = parts.drop(3).associate {
                            val kv = it.split("=")
                            kv[0] to kv.getOrElse(1) { "" }
                        }
                    )
                } else null
            } catch (e: Exception) { null }
        }
    }

    fun generateTorrcWithBridges(): String {
        val bridgeLines = bridges.joinToString("\n") { it.toTorrc() }
        val obfsPath = findPluginPath("obfs4proxy")
        val meekPath = findPluginPath("meek-client")
        val snowflakePath = findPluginPath("snowflake-client")
        return buildString {
            appendLine("UseBridges 1")
            if (obfsPath != null) appendLine("ClientTransportPlugin obfs4 exec $obfsPath")
            if (meekPath != null) appendLine("ClientTransportPlugin meek exec $meekPath")
            if (snowflakePath != null) appendLine("ClientTransportPlugin snowflake exec $snowflakePath")
            appendLine(bridgeLines)
        }
    }

    private fun findPluginPath(name: String): String? {
        val ctx = com.nexuschat.app.NexusChatApp.instance
        val binDir = java.io.File(ctx.filesDir, "bin")
        val inBin = java.io.File(binDir, name)
        if (inBin.exists()) return inBin.absolutePath
        val systemBin = java.io.File("/system/bin/$name")
        if (systemBin.exists()) return systemBin.absolutePath
        val nativeDir = java.io.File(ctx.applicationInfo.nativeLibraryDir, "lib${name}.so")
        if (nativeDir.exists()) return nativeDir.absolutePath
        return null
    }

    fun getRandomBridge(): BridgeLine? {
        if (bridges.isEmpty()) return null
        return bridges[rng.nextInt(bridges.size)]
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }
}
