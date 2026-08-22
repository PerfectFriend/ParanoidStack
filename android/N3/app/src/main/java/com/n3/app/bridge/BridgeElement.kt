/**
 * Bridge Element data class for transport chain configuration.
 * 
 * Represents a single bridge/VPN/proxy element in the transport chain.
 * Configurations can be loaded from assets/config/bridges.json or entered manually.
 * 
 * @property id Unique identifier (auto-generated UUID if not provided)
 * @property type Bridge type: OPENVPN, WIREGUARD, V2RAY, TOR, DIRECT
 * @property name Human-readable name for UI display
 * @property config JSON configuration string (host, port, keys, etc.)
 * @property enabled Whether this bridge is active in the chain
 * @property priority Priority order (lower = higher priority)
 * @property tags Optional tags for categorization
 */
package com.n3.app.bridge

import org.json.JSONObject

/**
 * Bridge type enumeration.
 * OPENVPN = VPN1 (first hop), WIREGUARD = VPN1/VPN2, V2RAY = VPN2 (second hop), TOR = final hop, DIRECT = no bridge
 */
enum class BridgeType { OPENVPN, WIREGUARD, V2RAY, TOR, DIRECT }

/**
 * Bridge element representing a single transport bridge/proxy.
 */
data class BridgeElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: BridgeType,
    val name: String = "",
    val config: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val tags: List<String> = emptyList()
) {
    /**
     * Convert to JSON string for storage.
     * @return JSON representation
     */
    fun toJson(): String = JSONObject().apply {
        put("id", id); put("type", type.name)
        put("name", name); put("enabled", enabled)
        put("priority", priority); put("config", config)
        put("tags", JSONObject().apply { tags.forEachIndexed { i, t -> put(i.toString(), t) } })
    }.toString()

    /**
     * Convert config to JSON string for transport layer.
     * Used when creating BridgeConfig objects for Tor/bridges.
     * @return JSON string with address, port, fingerprint, etc.
     */
    fun toJsonString(): String = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("name", name)
        put("config", config)
        put("enabled", enabled)
        put("priority", priority)
    }.toString()

    companion object {
        /**
         * Create BridgeElement from JSON string.
         * @param json JSON string from storage
         * @return BridgeElement instance
         */
        fun fromJson(json: String): BridgeElement = JSONObject(json).let { o ->
            BridgeElement(
                id = o.getString("id"),
                type = BridgeType.valueOf(o.getString("type")),
                name = o.getString("name"),
                config = o.optString("config", ""),
                enabled = o.optBoolean("enabled", true),
                priority = o.optInt("priority", 0),
                tags = o.optJSONObject("tags")?.let { t ->
                    (0 until t.length()).map { t.getString(it.toString()) }
                } ?: emptyList()
            )
        }
    }
}
