/**
 * Model and utilities for SimpleX invite link encoding and decoding.
 *
 * Format: simplex://&lt;base64_public_key&gt;@&lt;server_address&gt;/&lt;queueId&gt;?name=&lt;name&gt;
 * Supports URI encoding/decoding and seed-based generation via BIP-39.
 *
 * @see Bip39Utils
 */
package com.paranoidx.sdk.security

import java.util.Base64

/**
 * Файл: InviteLink.kt
 * Пакет: com.example.data.security
 * Назначение: Модель и утилиты для работы с пригласительными ссылками SimpleX.
 * Формат ссылки: simplex://<base64_публичный_ключ>@<адрес_сервера>/<queueId>?name=<имя>
 * Поддерживает кодирование/декодирование URI, а также генерацию из seed (BIP-39).
 *
 * @see Bip39Utils
 */

/**
 * Модель пригласительной ссылки SimpleX.
 * Содержит открытый ключ, адрес сервера, идентификатор очереди и отображаемое имя контакта.
 * @property pubKey открытый ключ контакта (32 байта)
 * @property serverAddress адрес SMP-сервера
 * @property queueId идентификатор очереди сообщений
 * @property displayName отображаемое имя контакта
 */
data class InviteLink(
    val pubKey: ByteArray,
    val serverAddress: String,
    val queueId: String,
    val displayName: String = "SimpleX Contact"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InviteLink) return false
        return pubKey.contentEquals(other.pubKey) && serverAddress == other.serverAddress && queueId == other.queueId
    }

    override fun hashCode(): Int {
        var result = pubKey.contentHashCode()
        result = 31 * result + serverAddress.hashCode()
        result = 31 * result + queueId.hashCode()
        return result
    }

    /**
     * Кодирует пригласительную ссылку в URI-строку формата:
     * simplex://<pubKey_b64>@<serverAddress>/<queueId>?name=<displayName>
     * @return строка URI для отправки контакту
     */
    fun toUri(): String {
        val keyB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey)
        return "simplex://$keyB64@$serverAddress/$queueId?name=${java.net.URLEncoder.encode(displayName, "UTF-8")}"
    }

    companion object {
        /**
         * Декодирует URI-строку в объект InviteLink.
         * @param uri строка вида simplex://<key>@<host>/<queueId>?name=<name>
         * @return InviteLink или null при некорректном формате
         */
        fun fromUri(uri: String): InviteLink? {
            return try {
                val withoutScheme = uri.removePrefix("simplex://")
                val atIdx = withoutScheme.indexOf('@')
                val slashIdx = withoutScheme.indexOf('/')
                val qmarkIdx = withoutScheme.indexOf('?')
                if (atIdx < 0 || slashIdx < 0) return null
                val keyB64 = withoutScheme.substring(0, atIdx)
                val hostPart = withoutScheme.substring(atIdx + 1, slashIdx)
                val queryPart = if (qmarkIdx >= 0) withoutScheme.substring(qmarkIdx + 1) else ""
                val queuePart = if (qmarkIdx >= 0) withoutScheme.substring(slashIdx + 1, qmarkIdx) else withoutScheme.substring(slashIdx + 1)
                val pubKey = Base64.getUrlDecoder().decode(keyB64)
                val name = if (queryPart.startsWith("name=")) {
                    java.net.URLDecoder.decode(queryPart.removePrefix("name="), "UTF-8")
                } else "SimpleX Contact"
                InviteLink(pubKey, hostPart, queuePart, name)
            } catch (_: Exception) { null }
        }

        /**
         * Генерирует пригласительную ссылку из seed и настроек сервера.
         * @param seed BIP-39 seed (64 байта)
         * @param server модель сервера SimpleX
         * @param displayName отображаемое имя контакта
         * @return сгенерированная InviteLink
         */
        fun generate(seed: ByteArray, serverAddress: String, displayName: String = "SimpleX Contact"): InviteLink {
            val identityKey = Bip39Utils.deriveSimpleXIdentityKey(seed)
            return InviteLink(identityKey, serverAddress, "", displayName)
        }
    }
}
