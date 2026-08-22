/**
 * Модели и утилиты для пересылки (forwarding) сообщений.
 * Позволяет пересылать чужие сообщения с сохранением информации
 * об оригинальном отправителе через JSON-обёртку.
 */
package com.example.data

import org.json.JSONObject

/** Информация о пересланном сообщении */
data class ForwardedMessage(
    val originalId: String,         // ID оригинального сообщения
    val originalText: String,       // оригинальный текст
    val forwardedFrom: String       // отправитель оригинального сообщения
)

/**
 * Wrap a forwarded message into a JSON envelope for SMP transport.
 * The JSON structure includes the original message ID, original text,
 * and the original sender's display name under a "forward" type key.
 */
fun formatForwarded(msg: ForwardedMessage): String {
    return JSONObject().apply {
        put("type", "forward")
        put("originalId", msg.originalId)
        put("originalText", msg.originalText)
        put("forwardedFrom", msg.forwardedFrom)
    }.toString()
}

/**
 * Check whether a received message text is a forwarded message envelope.
 * Parses the text as JSON and looks for a "type" field equal to "forward".
 */
fun isForwarded(text: String): Boolean {
    return try {
        val json = JSONObject(text)
        json.optString("type") == "forward"
    } catch (_: Exception) {
        false
    }
}
