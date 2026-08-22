package com.n3.app.services

import android.content.Context
import android.util.Log
import com.n3.app.config.TransportConfig
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TorBridgeConfig - Manages Tor bridge configurations for pluggable transports.
 * 
 * This class loads bridge configurations from assets/config/bridges.json via TransportConfig,
 * supports fetching fresh bridges from the Tor Project bridge database (bridges.torproject.org),
 * and provides torrc-formatted bridge lines for Tor configuration.
 * 
 * Supports multiple bridge types: obfs4, meek, snowflake, webtunnel.
 * Thread-safe singleton with coroutine-based async operations.
 * 
 * Key features:
 * - Loads bridges from JSON config (assets) with built-in fallback
 * - Fetches fresh bridges from Tor Project bridge database
 * - Parses obfs4, meek, snowflake, and webtunnel bridge lines
 * - Generates torrc configuration with ClientTransportPlugin lines
 * - Auto-refresh capability with configurable interval
 */
class TorBridgeConfig private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NexusChat/TorBridges"
        // Tor Project bridge database URL - returns plain text bridge lines
        private const val BRIDGE_DB_URL = "https://bridges.torproject.org/bridges"
        @Volatile private var instance: TorBridgeConfig? = null
        
        /**
         * Get singleton instance, initializing with context on first call.
         */
        fun getInstance(ctx: Context): TorBridgeConfig {
            return instance ?: synchronized(this) {
                instance ?: TorBridgeConfig(ctx.applicationContext).also { instance = it }
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
            instance?.destroy()
            instance = null
        }
    }

    /** Bridge transport types supported by Tor. */
    enum class BridgeType { OBF4, MEEK, SNOWFLAKE, WEBTUNNEL, HTTPS }

    /**
     * BridgeLine represents a single Tor bridge configuration.
     * 
     * @param type Bridge transport type (obfs4, meek, snowflake, etc.)
     * @param address Bridge IP address or hostname
     * @param port Bridge port number
     * @param fingerprint Bridge identity fingerprint (SHA-1 of identity key)
     * @param args Additional arguments (cert, iat-mode for obfs4; front, url for meek)
     */
    data class BridgeLine(
        val type: BridgeType,
        val address: String,
        val port: Int,
        val fingerprint: String,
        val args: Map<String, String> = emptyMap()
    ) {
        /**
         * Convert to torrc format line for Tor configuration file.
         * Example: "Bridge obfs4 1.2.3.4:443 FINGERPRINT cert=... iat-mode=0"
         */
        fun toTorrc(): String = when (type) {
            BridgeType.OBF4 -> "Bridge obfs4 $address:$port $fingerprint ${args.entries.joinToString(" ") { "${it.key}=${it.value}" }}"
            BridgeType.MEEK -> "Bridge meek $address:$port $fingerprint ${args.entries.joinToString(" ") { "${it.key}=${it.value}" }}"
            BridgeType.SNOWFLAKE -> "Bridge snowflake $address:$port $fingerprint"
            BridgeType.WEBTUNNEL -> "Bridge webtunnel $address:$port $fingerprint"
            BridgeType.HTTPS -> "Bridge $address:$port $fingerprint"
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val bridges = mutableListOf<BridgeLine>()
    private val initialized = AtomicBoolean(false)
    private val transportConfig = TransportConfig.getInstance(context)

    /**
     * Initialize bridges from config (assets + built-in defaults).
     * Safe to call multiple times - only initializes once.
     */
    fun initializeBridges(includeTypes: Set<BridgeType> = setOf(BridgeType.OBF4, BridgeType.MEEK, BridgeType.SNOWFLAKE)) {
        if (initialized.getAndSet(true)) return // Already initialized
        
        scope.launch {
            loadBridgesFromConfig(includeTypes)
        }
    }

    /**
     * Load bridges from TransportConfig (assets/bridges.json).
     * This replaces the old hardcoded default bridges.
     */
    private fun loadBridgesFromConfig(includeTypes: Set<BridgeType>) {
        try {
            val config = transportConfig.getBridgesConfig()
            
            if (BridgeType.OBF4 in includeTypes) {
                config.obfs4.forEach { entry ->
                    bridges.add(BridgeLine(
                        type = BridgeType.OBF4,
                        address = entry.address,
                        port = entry.port,
                        fingerprint = entry.fingerprint,
                        args = entry.args
                    ))
                }
                Log.i(TAG, "Loaded ${config.obfs4.size} obfs4 bridges from config")
            }
            
            if (BridgeType.MEEK in includeTypes) {
                config.meek.forEach { entry ->
                    bridges.add(BridgeLine(
                        type = BridgeType.MEEK,
                        address = entry.address,
                        port = entry.port,
                        fingerprint = entry.fingerprint,
                        args = entry.args
                    ))
                }
                Log.i(TAG, "Loaded ${config.meek.size} meek bridges from config")
            }
            
            if (BridgeType.SNOWFLAKE in includeTypes && config.snowflake != null) {
                val snowflake = config.snowflake!!
                // Snowflake uses broker URL, not direct bridge lines
                // We add a pseudo-bridge representing the snowflake configuration
                bridges.add(BridgeLine(
                    type = BridgeType.SNOWFLAKE,
                    address = snowflake.frontDomain,
                    port = 443,
                    fingerprint = "snowflake",
                    args = mapOf(
                        "broker" to snowflake.brokerUrl,
                        "front" to snowflake.frontDomain,
                        "stun" to snowflake.stunServers.joinToString(",")
                    )
                ))
                Log.i(TAG, "Loaded Snowflake configuration from config")
            }
            
            if (BridgeType.WEBTUNNEL in includeTypes) {
                config.webtunnel.forEach { entry ->
                    bridges.add(BridgeLine(
                        type = BridgeType.WEBTUNNEL,
                        address = entry.address,
                        port = entry.port,
                        fingerprint = entry.fingerprint,
                        args = entry.args
                    ))
                }
                Log.i(TAG, "Loaded ${config.webtunnel.size} webtunnel bridges from config")
            }
            
            Log.i(TAG, "Total bridges loaded: ${bridges.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bridges from config: ${e.message}")
            // Fallback to built-in defaults (handled by TransportConfig)
        }
    }

    /** Get all loaded bridges, optionally filtered by type. */
    fun getBridges(type: BridgeType? = null): List<BridgeLine> {
        return if (type != null) bridges.filter { it.type == type } else bridges.toList()
    }

    /** Add a bridge manually (e.g., from user input). */
    fun addBridge(bridge: BridgeLine) {
        bridges.add(bridge)
        Log.i(TAG, "Bridge added: ${bridge.type.name} ${bridge.address}:${bridge.port}")
    }

    /** Remove bridge by address. */
    fun removeBridge(address: String) {
        bridges.removeAll { it.address == address }
    }

    /** Clear all bridges. */
    fun clearBridges() {
        bridges.clear()
    }

    /**
     * Fetch fresh bridges from Tor Project bridge database.
     * 
     * Parses bridge lines in format:
     * - obfs4 IP:PORT FINGERPRINT cert=... iat-mode=...
     * - meek HOST:PORT FINGERPRINT front=... url=...
     * - snowflake HOST:PORT FINGERPRINT ...
     * - webtunnel HOST:PORT FINGERPRINT ...
     * 
     * @param email Optional email for bridge delivery (for private bridges)
     * @return List of newly fetched and parsed bridges
     */
    suspend fun fetchBridgesFromServer(email: String? = null): List<BridgeLine> = withContext(Dispatchers.IO) {
        try {
            val url = if (email != null && email.isNotBlank()) "$BRIDGE_DB_URL?email=$email" else BRIDGE_DB_URL
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            
            val response = conn.inputStream.bufferedReader().readText()
            val parsed = parseBridgeResponse(response)
            
            if (parsed.isNotEmpty()) {
                bridges.addAll(parsed)
                Log.i(TAG, "Fetched ${parsed.size} bridges from server")
            } else {
                Log.w(TAG, "No bridges parsed from server response")
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Bridge fetch failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse bridge response from Tor Project bridge database.
     * Supports multiple bridge types: obfs4, meek, snowflake, webtunnel.
     */
    private fun parseBridgeResponse(response: String): List<BridgeLine> {
        return response.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            
            try {
                when {
                    trimmed.startsWith("obfs4 ") -> parseObfs4Bridge(trimmed)
                    trimmed.startsWith("meek ") -> parseMeekBridge(trimmed)
                    trimmed.startsWith("snowflake ") -> parseSnowflakeBridge(trimmed)
                    trimmed.startsWith("webtunnel ") -> parseWebtunnelBridge(trimmed)
                    else -> null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse bridge line: $trimmed - ${e.message}")
                null
            }
        }
    }

    /** Parse obfs4 bridge line: "obfs4 IP:PORT FINGERPRINT cert=... iat-mode=..." */
    private fun parseObfs4Bridge(line: String): BridgeLine? {
        val parts = line.substring(6).trim().split(" ")
        if (parts.size < 3) return null
        
        val addrParts = parts[0].split(":")
        val address = addrParts[0]
        val port = addrParts.getOrNull(1)?.toIntOrNull() ?: 443
        val fingerprint = parts[1]
        
        val args = parts.drop(2).associate {
            val kv = it.split("=")
            kv[0] to kv.getOrElse(1) { "" }
        }
        
        return BridgeLine(
            type = BridgeType.OBF4,
            address = address,
            port = port,
            fingerprint = fingerprint,
            args = args
        )
    }

    /** Parse meek bridge line: "meek HOST:PORT FINGERPRINT front=... url=..." */
    private fun parseMeekBridge(line: String): BridgeLine? {
        val parts = line.substring(5).trim().split(" ")
        if (parts.size < 3) return null
        
        val addrParts = parts[0].split(":")
        val address = addrParts[0]
        val port = addrParts.getOrNull(1)?.toIntOrNull() ?: 443
        val fingerprint = parts[1]
        
        val args = parts.drop(2).associate {
            val kv = it.split("=")
            kv[0] to kv.getOrElse(1) { "" }
        }
        
        return BridgeLine(
            type = BridgeType.MEEK,
            address = address,
            port = port,
            fingerprint = fingerprint,
            args = args
        )
    }

    /** Parse snowflake bridge line: "snowflake HOST:PORT FINGERPRINT ..." */
    private fun parseSnowflakeBridge(line: String): BridgeLine? {
        val parts = line.substring(10).trim().split(" ")
        if (parts.size < 3) return null
        
        val addrParts = parts[0].split(":")
        val address = addrParts[0]
        val port = addrParts.getOrNull(1)?.toIntOrNull() ?: 443
        val fingerprint = parts[1]
        
        val args = parts.drop(2).associate {
            val kv = it.split("=")
            kv[0] to kv.getOrElse(1) { "" }
        }
        
        return BridgeLine(
            type = BridgeType.SNOWFLAKE,
            address = address,
            port = port,
            fingerprint = fingerprint,
            args = args
        )
    }

    /** Parse webtunnel bridge line: "webtunnel HOST:PORT FINGERPRINT ..." */
    private fun parseWebtunnelBridge(line: String): BridgeLine? {
        val parts = line.substring(10).trim().split(" ")
        if (parts.size < 3) return null
        
        val addrParts = parts[0].split(":")
        val address = addrParts[0]
        val port = addrParts.getOrNull(1)?.toIntOrNull() ?: 443
        val fingerprint = parts[1]
        
        val args = parts.drop(2).associate {
            val kv = it.split("=")
            kv[0] to kv.getOrElse(1) { "" }
        }
        
        return BridgeLine(
            type = BridgeType.WEBTUNNEL,
            address = address,
            port = port,
            fingerprint = fingerprint,
            args = args
        )
    }

    /**
     * Generate torrc configuration with all loaded bridges and transport plugins.
     * Includes ClientTransportPlugin lines for obfs4proxy, meek-client, snowflake-client.
     */
    fun generateTorrcWithBridges(): String {
        val bridgeLines = bridges.joinToString("\n") { it.toTorrc() }
        val obfsPath = findPluginPath("obfs4proxy")
        val meekPath = findPluginPath("meek-client")
        val snowflakePath = findPluginPath("snowflake-client")
        val webtunnelPath = findPluginPath("webtunnel-client")
        
        return buildString {
            appendLine("UseBridges 1")
            if (obfsPath != null) appendLine("ClientTransportPlugin obfs4 exec $obfsPath")
            if (meekPath != null) appendLine("ClientTransportPlugin meek exec $meekPath")
            if (snowflakePath != null) appendLine("ClientTransportPlugin snowflake exec $snowflakePath")
            if (webtunnelPath != null) appendLine("ClientTransportPlugin webtunnel exec $webtunnelPath")
            appendLine(bridgeLines)
        }
    }

    /**
     * Find path to pluggable transport binary.
     * Search order: app files/bin/ -> /system/bin/ -> native library dir.
     */
    private fun findPluginPath(name: String): String? {
        val ctx = context
        val binDir = java.io.File(ctx.filesDir, "bin")
        val inBin = java.io.File(binDir, name)
        if (inBin.exists()) return inBin.absolutePath
        
        val systemBin = java.io.File("/system/bin/$name")
        if (systemBin.exists()) return systemBin.absolutePath
        
        val nativeDir = java.io.File(ctx.applicationInfo.nativeLibraryDir, "lib${name}.so")
        if (nativeDir.exists()) return nativeDir.absolutePath
        
        return null
    }

    /** Get a random bridge from loaded bridges (for load balancing). */
    fun getRandomBridge(): BridgeLine? {
        if (bridges.isEmpty()) return null
        return bridges[rng.nextInt(bridges.size)]
    }

    /** Get count of loaded bridges by type. */
    fun getBridgeCount(): Map<BridgeType, Int> {
        return bridges.groupBy { it.type }.mapValues { it.value.size }
    }

    /** Check if bridges are loaded. */
    fun hasBridges(): Boolean = bridges.isNotEmpty()

    /** Destroy and cleanup resources. */
    fun destroy() {
        scope.cancel()
        bridges.clear()
        initialized.set(false)
    }
}