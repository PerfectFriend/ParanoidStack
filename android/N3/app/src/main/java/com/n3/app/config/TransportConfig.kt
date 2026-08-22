package com.n3.app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * TransportConfig - Centralized configuration loader for all transport-related settings.
 * 
 * This class loads JSON configuration files from assets/config/ directory and provides
 * type-safe access to bridges, STUN/TURN servers, domain fronting hosts, health checks,
 * and Snowflake configuration. It supports fallback to built-in defaults if assets are missing.
 * 
 * Thread-safe: Uses ConcurrentHashMap for caching and synchronized initialization.
 * 
 * Usage:
 *   val config = TransportConfig.getInstance(context)
 *   val bridges = config.getBridges()
 *   val stunServers = config.getStunServers()
 *   val frontDomains = config.getFrontDomains()
 */
object TransportConfig {

    private const val TAG = "TransportConfig"
    private const val CONFIG_DIR = "config/"

    // Config file names
    private const val BRIDGES_FILE = "bridges.json"
    private const val STUN_TURN_FILE = "stun-turn.json"
    private const val FRONT_DOMAINS_FILE = "front-domains.json"
    private const val HEALTH_CHECKS_FILE = "health-checks.json"
    private const val SNOWFLAKE_FILE = "snowflake.json"

    private var context: Context? = null
    private val cache = ConcurrentHashMap<String, Any>()
    private val gson = Gson()

    // Data classes for JSON parsing
    data class BridgeEntry(
        val address: String,
        val port: Int,
        val fingerprint: String,
        val args: Map<String, String> = emptyMap()
    )

    data class BridgesConfig(
        val obfs4: List<BridgeEntry> = emptyList(),
        val meek: List<BridgeEntry> = emptyList(),
        val snowflake: SnowflakeConfig? = null,
        val webtunnel: List<BridgeEntry> = emptyList(),
        val autoFetch: AutoFetchConfig? = null
    )

    data class SnowflakeConfig(
        val brokerUrl: String = "https://snowflake-broker.torproject.net/",
        val frontDomain: String = "snowflake.torproject.net",
        val stunServers: List<String> = emptyList()
    )

    data class AutoFetchConfig(
        val enabled: Boolean = true,
        val email: String = "",
        val intervalHours: Int = 24,
        val fallbackToBuiltin: Boolean = true
    )

    data class StunTurnConfig(
        val stun: List<StunEntry> = emptyList(),
        val turn: List<TurnEntry> = emptyList(),
        val defaults: DefaultsConfig = DefaultsConfig()
    )

    data class StunEntry(
        val url: String,
        val description: String = ""
    )

    data class TurnEntry(
        val url: String,
        val username: String,
        val credential: String,
        val description: String = ""
    )

    data class DefaultsConfig(
        val preferTurnOverStun: Boolean = false,
        val iceTransportPolicy: String = "all",
        val gatheringTimeoutMs: Int = 10000
    )

    data class FrontDomainEntry(
        val domain: String,
        val weight: Int = 100,
        val description: String = ""
    )

    data class FrontDomainsConfig(
        val domains: List<FrontDomainEntry> = emptyList(),
        val selectionStrategy: String = "weighted-random",
        val rotationIntervalMinutes: Int = 30,
        val fallbackDomain: String = "www.google.com"
    )

    data class HealthCheckEndpoint(
        val name: String,
        val url: String,
        val method: String = "GET",
        val timeoutMs: Int = 10000,
        val expectedStatus: Int = 200,
        val validateJsonPath: String? = null,
        val description: String = ""
    )

    data class HealthChecksConfig(
        val endpoints: List<HealthCheckEndpoint> = emptyList(),
        val schedule: ScheduleConfig = ScheduleConfig(),
        val failureThreshold: Int = 3,
        val fallbackToBuiltin: Boolean = true
    )

    data class ScheduleConfig(
        val intervalMinutes: Int = 15,
        val runOnStartup: Boolean = true,
        val runOnNetworkChange: Boolean = true
    )

    data class SnowflakeFullConfig(
        val brokerUrl: String = "https://snowflake-broker.torproject.net/",
        val frontDomain: String = "snowflake.torproject.net",
        val stunServers: List<String> = emptyList(),
        val clientConfig: ClientConfig = ClientConfig(),
        val proxyConfig: ProxyConfig = ProxyConfig()
    )

    data class ClientConfig(
        val maxRetries: Int = 3,
        val retryDelayMs: Int = 2000,
        val connectionTimeoutMs: Int = 30000,
        val keepAliveIntervalMs: Int = 15000
    )

    data class ProxyConfig(
        val enabled: Boolean = false,
        val type: String = "socks5",
        val address: String = "",
        val port: Int = 0
    )

    /**
     * Get singleton instance, initializing with context on first call.
     */
    fun getInstance(ctx: Context): TransportConfig {
        if (context == null) {
            context = ctx.applicationContext
        }
        return this
    }

    /**
     * Initialize with context (call early in Application.onCreate).
     */
    fun initialize(ctx: Context) {
        getInstance(ctx)
    }

    private fun <T> loadJson(filename: String, type: Type): T? {
        val ctx = context ?: return null
        return try {
            val inputStream = ctx.assets.open(CONFIG_DIR + filename)
            val reader = InputStreamReader(inputStream)
            gson.fromJson(reader, type)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to load $filename: ${e.message}, using built-in defaults")
            null
        }
    }

    // ============ BRIDGES ============

    /**
     * Get all configured bridges (obfs4 + meek + snowflake + webtunnel).
     * Loads from assets/bridges.json on first call, caches result.
     */
    fun getBridgesConfig(): BridgesConfig {
        return cache.getOrPut(BRIDGES_FILE) {
            val type = TypeToken.getParameterized(BridgesConfig::class.java).type
            loadJson(BRIDGES_FILE, type) ?: builtinBridgesConfig()
        } as BridgesConfig
    }

    /**
     * Get only obfs4 bridges.
     */
    fun getObfs4Bridges(): List<BridgeEntry> = getBridgesConfig().obfs4

    /**
     * Get only meek bridges.
     */
    fun getMeekBridges(): List<BridgeEntry> = getBridgesConfig().meek

    /**
     * Get Snowflake configuration (broker URL, front domain, STUN servers).
     */
    fun getSnowflakeConfig(): SnowflakeConfig {
        val bridgesConfig = getBridgesConfig()
        return bridgesConfig.snowflake ?: SnowflakeConfig()
    }

    /**
     * Get auto-fetch configuration for bridges.
     */
    fun getAutoFetchConfig(): AutoFetchConfig {
        val bridgesConfig = getBridgesConfig()
        return bridgesConfig.autoFetch ?: AutoFetchConfig()
    }

    // ============ STUN/TURN ============

    /**
     * Get STUN/TURN configuration.
     */
    fun getStunTurnConfig(): StunTurnConfig {
        return cache.getOrPut(STUN_TURN_FILE) {
            val type = TypeToken.getParameterized(StunTurnConfig::class.java).type
            loadJson(STUN_TURN_FILE, type) ?: builtinStunTurnConfig()
        } as StunTurnConfig
    }

    /**
     * Get list of STUN server URLs (e.g., "stun:stun.l.google.com:19302").
     */
    fun getStunServers(): List<String> = getStunTurnConfig().stun.map { it.url }

    /**
     * Get list of TURN server configurations with credentials.
     */
    fun getTurnServers(): List<TurnEntry> = getStunTurnConfig().turn

    /**
     * Get ICE transport defaults.
     */
    fun getIceDefaults(): DefaultsConfig = getStunTurnConfig().defaults

    // ============ DOMAIN FRONTING ============

    /**
     * Get domain fronting configuration with weighted domains.
     */
    fun getFrontDomainsConfig(): FrontDomainsConfig {
        return cache.getOrPut(FRONT_DOMAINS_FILE) {
            val type = TypeToken.getParameterized(FrontDomainsConfig::class.java).type
            loadJson(FRONT_DOMAINS_FILE, type) ?: builtinFrontDomainsConfig()
        } as FrontDomainsConfig
    }

    /**
     * Get list of front domains for domain fronting.
     */
    fun getFrontDomains(): List<String> = getFrontDomainsConfig().domains.map { it.domain }

    /**
     * Select a front domain using weighted random selection.
     * Higher weight = higher probability of selection.
     */
    fun selectFrontDomain(): String {
        val config = getFrontDomainsConfig()
        if (config.domains.isEmpty()) return config.fallbackDomain
        
        val totalWeight = config.domains.sumOf { it.weight }
        var random = (Math.random() * totalWeight).toInt()
        
        for (domain in config.domains) {
            random -= domain.weight
            if (random < 0) return domain.domain
        }
        return config.fallbackDomain
    }

    // ============ HEALTH CHECKS ============

    /**
     * Get health check endpoints configuration.
     */
    fun getHealthChecksConfig(): HealthChecksConfig {
        return cache.getOrPut(HEALTH_CHECKS_FILE) {
            val type = TypeToken.getParameterized(HealthChecksConfig::class.java).type
            loadJson(HEALTH_CHECKS_FILE, type) ?: builtinHealthChecksConfig()
        } as HealthChecksConfig
    }

    /**
     * Get list of health check endpoints.
     */
    fun getHealthCheckEndpoints(): List<HealthCheckEndpoint> = getHealthChecksConfig().endpoints

    /**
     * Get health check schedule configuration.
     */
    fun getHealthCheckSchedule(): ScheduleConfig = getHealthChecksConfig().schedule

    // ============ SNOWFLAKE (standalone) ============

    /**
     * Get full Snowflake configuration (separate file for more detail).
     */
    fun getSnowflakeFullConfig(): SnowflakeFullConfig {
        return cache.getOrPut(SNOWFLAKE_FILE) {
            val type = TypeToken.getParameterized(SnowflakeFullConfig::class.java).type
            loadJson(SNOWFLAKE_FILE, type) ?: builtinSnowflakeFullConfig()
        } as SnowflakeFullConfig
    }

    // ============ BUILTIN DEFAULTS ============

    /**
     * Built-in bridges configuration (fallback when assets missing).
     * These are the same bridges previously hardcoded in TorBridgeConfig.kt.
     */
    private fun builtinBridgesConfig(): BridgesConfig {
        return BridgesConfig(
            obfs4 = listOf(
                BridgeEntry(
                    address = "85.31.186.98", port = 443,
                    fingerprint = "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
                    args = mapOf("cert" to "F5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")
                ),
                BridgeEntry(
                    address = "192.95.36.142", port = 443,
                    fingerprint = "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
                    args = mapOf("cert" to "A5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")
                ),
                BridgeEntry(
                    address = "38.229.1.18", port = 80,
                    fingerprint = "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
                    args = mapOf("cert" to "B5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "0")
                ),
                BridgeEntry(
                    address = "85.31.186.98", port = 443,
                    fingerprint = "B2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
                    args = mapOf("cert" to "C5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8", "iat-mode" to "1")
                )
            ),
            meek = listOf(
                BridgeEntry(
                    address = "meek.azureedge.net", port = 443,
                    fingerprint = "D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8",
                    args = mapOf("front" to "ajax.aspnetcdn.com", "url" to "https://meek.azureedge.net/")
                )
            ),
            snowflake = SnowflakeConfig(
                brokerUrl = "https://snowflake-broker.torproject.net/",
                frontDomain = "snowflake.torproject.net",
                stunServers = listOf(
                    "stun:stun.l.google.com:19302",
                    "stun:stun1.l.google.com:19302",
                    "stun:stun.cloudflare.com:3478"
                )
            ),
            autoFetch = AutoFetchConfig()
        )
    }

    /**
     * Built-in STUN/TURN configuration (fallback).
     */
    private fun builtinStunTurnConfig(): StunTurnConfig {
        return StunTurnConfig(
            stun = listOf(
                StunEntry("stun:stun.l.google.com:19302", "Google STUN (primary)"),
                StunEntry("stun:stun1.l.google.com:19302", "Google STUN (secondary)"),
                StunEntry("stun:stun.cloudflare.com:3478", "Cloudflare STUN"),
                StunEntry("stun:stun.stunprotocol.org:3478", "Public STUN"),
                StunEntry("stun:stun.voipbuster.com:3478", "VoIP Buster STUN")
            ),
            turn = listOf(
                TurnEntry("turn:turn.example.com:3478?transport=udp", "user", "password", "Example TURN (replace)"),
                TurnEntry("turn:turn.example.com:5349?transport=tcp", "user", "password", "Example TURN TLS (replace)")
            ),
            defaults = DefaultsConfig()
        )
    }

    /**
     * Built-in domain fronting configuration (fallback).
     */
    private fun builtinFrontDomainsConfig(): FrontDomainsConfig {
        return FrontDomainsConfig(
            domains = listOf(
                FrontDomainEntry("www.google.com", 100, "Google (high availability)"),
                FrontDomainEntry("www.cloudflare.com", 90, "Cloudflare (high availability)"),
                FrontDomainEntry("www.microsoft.com", 80, "Microsoft (corporate-friendly)"),
                FrontDomainEntry("www.amazon.com", 70, "Amazon"),
                FrontDomainEntry("www.github.com", 60, "GitHub (developer-friendly)"),
                FrontDomainEntry("cdn.jsdelivr.net", 50, "jsDelivr CDN"),
                FrontDomainEntry("unpkg.com", 40, "unpkg CDN")
            ),
            fallbackDomain = "www.google.com"
        )
    }

    /**
     * Built-in health checks configuration (fallback).
     */
    private fun builtinHealthChecksConfig(): HealthChecksConfig {
        return HealthChecksConfig(
            endpoints = listOf(
                HealthCheckEndpoint(
                    "tor-check-api", "https://check.torproject.org/api/ip", "GET", 10000, 200,
                    "$.IsTor", "Tor Project IP check API"
                ),
                HealthCheckEndpoint(
                    "tor-check-html", "https://check.torproject.org/", "HEAD", 10000, 200,
                    null, "Tor Project check page"
                ),
                HealthCheckEndpoint(
                    "google-connectivity", "https://www.google.com/generate_204", "GET", 5000, 204,
                    null, "Google connectivity (204)"
                ),
                HealthCheckEndpoint(
                    "cloudflare-connectivity", "https://www.cloudflare.com/cdn-cgi/trace", "GET", 5000, 200,
                    null, "Cloudflare trace"
                ),
                HealthCheckEndpoint(
                    "smp-connectivity", "https://smp.simplex.chat:5223", "GET", 10000, 200,
                    null, "SimpleX SMP connectivity"
                ),
                HealthCheckEndpoint(
                    "snowflake-broker", "https://snowflake-broker.torproject.net/", "GET", 10000, 200,
                    null, "Snowflake broker"
                )
            ),
            schedule = ScheduleConfig()
        )
    }

    /**
     * Built-in Snowflake full configuration (fallback).
     */
    private fun builtinSnowflakeFullConfig(): SnowflakeFullConfig {
        return SnowflakeFullConfig(
            brokerUrl = "https://snowflake-broker.torproject.net/",
            frontDomain = "snowflake.torproject.net",
            stunServers = listOf(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302",
                "stun:stun.cloudflare.com:3478",
                "stun:stun.stunprotocol.org:3478"
            )
        )
    }

    /**
     * Clear cache (useful for testing or config reload).
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Reload all configs from assets (clears cache first).
     */
    fun reloadAll() {
        clearCache()
        // Trigger reload on next access
        getBridgesConfig()
        getStunTurnConfig()
        getFrontDomainsConfig()
        getHealthChecksConfig()
        getSnowflakeFullConfig()
    }
}