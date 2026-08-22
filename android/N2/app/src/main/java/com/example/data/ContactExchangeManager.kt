/**
 * Менеджер обмена контактами через приглашения SimpleX.
 * Генерирует и парсит JSON-инвайты и ссылки для приглашений.
 */
package com.example.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Управление созданием и обработкой приглашений контактов.
 * Поддерживает два формата: JSON-инвайт (для передачи через сторонние каналы)
 * и share-ссылку вида simplex://contact/...
 */
class ContactExchangeManager {

    /** Данные приглашения контакта */
    data class ContactInvite(
        val displayName: String,     // отображаемое имя
        val publicKeyB64: String,    // публичный ключ в Base64
        val smpServer: String,       // адрес SMP-сервера
        val queueId: String          // идентификатор очереди
    ) {
        /** Сериализовать приглашение в JSON */
        fun toJson(): String = JSONObject().apply {
            put("displayName", displayName)
            put("publicKey", publicKeyB64)
            put("smpServer", smpServer)
            put("queueId", queueId)
        }.toString()

        companion object {
            /** Десериализовать приглашение из JSON */
            fun fromJson(jsonStr: String): ContactInvite? = try {
                val json = JSONObject(jsonStr)
                ContactInvite(
                    displayName = json.getString("displayName"),
                    publicKeyB64 = json.getString("publicKey"),
                    smpServer = json.getString("smpServer"),
                    queueId = json.getString("queueId")
                )
            } catch (e: Exception) { null }
        }
    }

    /**
     * Создать JSON-код приглашения для отправки контакту.
     * @return JSON-строка с параметрами приглашения
     */
    fun generateInviteCode(displayName: String, publicKeyB64: String, smpServer: String, queueId: String): String {
        return ContactInvite(displayName, publicKeyB64, smpServer, queueId).toJson()
    }

    /**
     * Разобрать JSON-код приглашения.
     * @param code JSON-строка приглашения
     * @return объект ContactInvite или null при ошибке парсинга
     */
    fun parseInviteCode(code: String): ContactInvite? {
        return ContactInvite.fromJson(code)
    }

    /**
     * Создать share-ссылку для приглашения контакта.
     * Формат: simplex://contact/имя@сервер/очередь
     */
    fun generateShareLink(displayName: String, serverHost: String, queueId: String): String {
        return "simplex://contact/$displayName@$serverHost/$queueId"
    }
}
