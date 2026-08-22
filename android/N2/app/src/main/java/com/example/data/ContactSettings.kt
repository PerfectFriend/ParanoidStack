/**
 * Настройки контакта (мут, блокировка) и менеджер для управления ими.
 * Сохраняется в SecureStorage в виде JSON.
 */
package com.example.data

/**
 * Настройки конкретного контакта.
 *
 * @property contactId идентификатор контакта
 * @property isMuted отключены ли уведомления
 * @property isBlocked заблокирован ли контакт
 * @property muteUntil временная метка окончания мута (null = навсегда)
 */
data class ContactSettings(
    val contactId: String,
    val isMuted: Boolean = false,
    val isBlocked: Boolean = false,
    val muteUntil: Long? = null
)

/**
 * Менеджер настроек контактов.
 * Позволяет мутить/блокировать контакты с персистентностью через SecureStorage.
 */
class ContactSettingsManager(private val secureStorage: SecureStorage) {
    private val settings = mutableMapOf<String, ContactSettings>()

    /**
     * Замутить контакт на указанное время.
     * @param contactId ID контакта
     * @param durationMs длительность мута (мс), null = навсегда
     */
    fun mute(contactId: String, durationMs: Long?) {
        val current = settings.getOrDefault(contactId, ContactSettings(contactId = contactId))
        val muteUntil = if (durationMs != null) System.currentTimeMillis() + durationMs else null
        val updated = current.copy(isMuted = true, muteUntil = muteUntil, isBlocked = current.isBlocked)
        settings[contactId] = updated
        persist(contactId, updated)
    }

    /** Снять мут с контакта */
    fun unmute(contactId: String) {
        val current = settings.getOrDefault(contactId, ContactSettings(contactId = contactId))
        val updated = current.copy(isMuted = false, muteUntil = null)
        settings[contactId] = updated
        persist(contactId, updated)
    }

    /**
     * Проверить, замучен ли контакт.
     * Автоматически снимает мут, если время истекло.
     */
    fun isMuted(contactId: String): Boolean {
        val current = settings[contactId] ?: return false
        if (!current.isMuted) return false
        val muteUntil = current.muteUntil ?: return true
        // Если время мута истекло — автоматически размучиваем
        if (System.currentTimeMillis() >= muteUntil) {
            unmute(contactId)
            return false
        }
        return true
    }

    /** Заблокировать контакт */
    fun block(contactId: String) {
        val current = settings.getOrDefault(contactId, ContactSettings(contactId = contactId))
        val updated = current.copy(isBlocked = true)
        settings[contactId] = updated
        persist(contactId, updated)
    }

    /** Разблокировать контакт */
    fun unblock(contactId: String) {
        val current = settings.getOrDefault(contactId, ContactSettings(contactId = contactId))
        val updated = current.copy(isBlocked = false)
        settings[contactId] = updated
        persist(contactId, updated)
    }

    /** Проверить, заблокирован ли контакт */
    fun isBlocked(contactId: String): Boolean {
        return settings[contactId]?.isBlocked ?: false
    }

    /** Сохранить настройки контакта в SecureStorage */
    private fun persist(contactId: String, cs: ContactSettings) {
        val json = """{"contactId":"${cs.contactId}","isMuted":${cs.isMuted},"isBlocked":${cs.isBlocked},"muteUntil":${cs.muteUntil}}"""
        secureStorage.putString("contact_settings_$contactId", json)
    }
}
