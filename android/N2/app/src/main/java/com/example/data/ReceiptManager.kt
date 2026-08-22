/**
 * Менеджер квитанций (receipts) о доставке/прочтении сообщений.
 * Позволяет создавать, кодировать, декодировать и проверять
 * квитанции, передаваемые через JSON в SMP-сообщениях.
 */
package com.example.data

import android.util.Log
import org.json.JSONObject

/** Менеджер квитанций для отслеживания статуса сообщений */
class ReceiptManager {
    private val tag = "ReceiptManager"

    /** Тип квитанции: доставлено, прочитано, ошибка */
    enum class ReceiptType { DELIVERY, READ, FAILED }

    /** Квитанция о статусе сообщения */
    data class Receipt(
        val messageId: String,                    // ID сообщения
        val type: ReceiptType,                    // тип квитанции
        val timestamp: Long = System.currentTimeMillis()  // время
    )

    /**
     * Закодировать квитанцию в JSON.
     * @param receipt объект квитанции
     * @return JSON-строка
     */
    fun encodeReceipt(receipt: Receipt): String {
        return JSONObject().apply {
            put("type", "receipt")
            put("messageId", receipt.messageId)
            put("receiptType", receipt.type.name.lowercase())
            put("timestamp", receipt.timestamp)
        }.toString()
    }

    /**
     * Декодировать квитанцию из JSON.
     * @param jsonStr JSON-строка
     * @return Receipt или null при ошибке
     */
    fun decodeReceipt(jsonStr: String): Receipt? {
        return try {
            val json = JSONObject(jsonStr)
            if (json.optString("type") != "receipt") return null
            Receipt(
                messageId = json.getString("messageId"),
                type = ReceiptType.valueOf(json.getString("receiptType").uppercase()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse receipt", e)
            null
        }
    }

    /** Проверить, является ли строка квитанцией */
    fun isReceipt(jsonStr: String): Boolean {
        return try {
            JSONObject(jsonStr).optString("type") == "receipt"
        } catch (e: Exception) { false }
    }
}
