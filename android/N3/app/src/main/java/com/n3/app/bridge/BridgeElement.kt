package com.n3.app.bridge

import org.json.JSONObject

enum class BridgeType { OPENVPN, WIREGUARD, V2RAY, TOR, DIRECT }

data class BridgeElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: BridgeType,
    val name: String = "",
    val config: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val tags: List<String> = emptyList()
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id); put("type", type.name)
        put("name", name); put("enabled", enabled)
        put("priority", priority); put("config", config)
        put("tags", JSONObject().apply { tags.forEachIndexed { i, t -> put(i.toString(), t) } })
    }.toString()

    companion object {
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
