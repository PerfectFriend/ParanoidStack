/**
 * Пакет навигации — обработчик deep link'ов.
 * Содержит [DeepLinkHandler], отвечающий за разбор внешних ссылок
 * по схеме `simplex://` и построение пригласительных ссылок.
 *
 * ## Схема ссылок
 * - `simplex://contact/<код>` — приглашение контакта
 * - `simplex://group/<id>` — приглашение в группу
 */
package com.example.ui.navigation

import android.content.Intent
import android.net.Uri
import com.example.data.ContactExchangeManager

/**
 * Обработчик deep link'ов для схемы simplex://.
 * Позволяет открывать приглашения контактов и группы
 * через ссылки вида simplex://contact/... или simplex://group/...
 *
 * @property DeepLinkResult результат парсинга deep link'а.
 * @property DeepLinkType тип приглашения (контакт, группа, неизвестный).
 */
class DeepLinkHandler {

    /**
     * Результат разбора deep link'а.
     * @property type тип приглашения.
     * @property contactId идентификатор контакта (для CONTACT_INVITE).
     * @property groupId идентификатор группы (для GROUP_INVITE).
     * @property inviteCode код приглашения.
     */
    data class DeepLinkResult(
        val type: DeepLinkType,
        val contactId: String? = null,
        val groupId: String? = null,
        val inviteCode: String? = null
    )

    /** Тип deep link'а. */
    enum class DeepLinkType { CONTACT_INVITE, GROUP_INVITE, UNKNOWN }

    /**
     * Обрабатывает входящий Intent и извлекает deep link, если он присутствует.
     * @param intent входящий Intent.
     * @return результат разбора или null, если данных нет.
     */
    fun handleIntent(intent: Intent): DeepLinkResult? {
        val data = intent.data ?: return null
        return parseUri(data)
    }

    /**
     * Разбирает URI схемы simplex:// на структурированный результат.
     * @param uri входящий URI.
     * @return результат разбора или null, если схема не simplex://.
     */
    fun parseUri(uri: Uri): DeepLinkResult? {
        if (uri.scheme != "simplex") return null

        return when (uri.host) {
            "contact" -> {
                val code = uri.pathSegments.firstOrNull()
                DeepLinkResult(DeepLinkType.CONTACT_INVITE, inviteCode = code)
            }
            "group" -> {
                val groupId = uri.pathSegments.firstOrNull()
                DeepLinkResult(DeepLinkType.GROUP_INVITE, groupId = groupId)
            }
            else -> DeepLinkResult(DeepLinkType.UNKNOWN)
        }
    }

    /**
     * Строит пригласительную ссылку контакта формата simplex://contact/... .
     * @param displayName отображаемое имя контакта.
     * @param smpServer адрес SMP-сервера.
     * @param queueId идентификатор очереди.
     * @return готовая ссылка.
     */
    fun buildContactInviteLink(displayName: String, smpServer: String, queueId: String): String {
        return "simplex://contact/${Uri.encode(displayName)}@${Uri.encode(smpServer)}/${Uri.encode(queueId)}"
    }

    /**
     * Строит пригласительную ссылку группы формата simplex://group/... .
     * @param groupId идентификатор группы.
     * @param server адрес сервера.
     * @return готовая ссылка.
     */
    fun buildGroupInviteLink(groupId: String, server: String): String {
        return "simplex://group/${Uri.encode(groupId)}@${Uri.encode(server)}"
    }
}
