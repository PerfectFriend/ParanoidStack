/**
 * Менеджер профилей пользователя.
 * Позволяет создавать, удалять и переключаться между профилями.
 * Каждый профиль имеет отдельную БД и настройки.
 * Поддерживаются "ложные" (decoy) профили для защиты конфиденциальности.
 */
package com.example.data

import android.util.Base64
import android.util.Log
import java.security.SecureRandom

/**
 * A user profile with isolated database and preferences.
 *
 * @property id Unique profile identifier (random Base64).
 * @property name Display name for the profile.
 * @property createdAt Unix timestamp of profile creation.
 * @property isActive Whether this is the currently active profile.
 * @property isDecoy Decoy profile for plausible deniability under coercion.
 */
data class Profile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val isActive: Boolean,
    val isDecoy: Boolean
)

/** Синглтон-менеджер профилей */
object ProfileManager {
    private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_PROFILES = "saved_profiles"

    private val lock = Any()
    private val profiles = mutableListOf<Profile>()

    /** Загрузить профили из SecureStorage */
    fun loadFromStorage() {
        synchronized(lock) {
            val raw = SecureStorage.getString(KEY_PROFILES, "").getOrNull() ?: ""
            if (raw.isNotEmpty()) {
                try {
                    val items = raw.split("|")
                    for (item in items) {
                        val parts = item.split(",")
                        if (parts.size >= 5) {
                            profiles.add(
                                Profile(
                                    id = parts[0],
                                    name = parts[1],
                                    createdAt = parts[2].toLongOrNull() ?: 0L,
                                    isActive = parts[3].toBoolean(),
                                    isDecoy = parts[4].toBoolean()
                                )
                            )
                        }
                    }
                } catch (_: java.lang.Exception) { Log.w("ProfileManager", "ignored exception") }
            }
            val activeId = SecureStorage.getString(KEY_ACTIVE_PROFILE, "").getOrNull() ?: ""
            if (activeId.isNotEmpty() && profiles.none { it.id == activeId }) {
                // Восстанавливаем активный профиль, если он не загружен
                profiles.add(
                    Profile(activeId, "Main", System.currentTimeMillis(), isActive = true, isDecoy = false)
                )
            } else if (activeId.isEmpty() && profiles.isEmpty()) {
                // Если нет ни активного, ни сохранённых профилей — создаём Main
                val main = createProfile("Main", false)
                SecureStorage.putString(KEY_ACTIVE_PROFILE, main.id)
            }
        }
    }

    /** Сохранить профили в SecureStorage */
    private fun saveToStorage() {
        synchronized(lock) {
            val raw = profiles.joinToString("|") { "${it.id},${it.name},${it.createdAt},${it.isActive},${it.isDecoy}" }
            SecureStorage.putString(KEY_PROFILES, raw)
            val active = profiles.find { it.isActive }
            if (active != null) {
                SecureStorage.putString(KEY_ACTIVE_PROFILE, active.id)
            }
        }
    }

    /**
     * Создать новый профиль.
     * @param name имя профиля
     * @param isDecoy ложный профиль?
     * @return созданный профиль
     */
    fun createProfile(name: String, isDecoy: Boolean = false): Profile {
        synchronized(lock) {
            val id = Base64.encodeToString(
                ByteArray(8).also { SecureRandom().nextBytes(it) },
                Base64.NO_PADDING or Base64.NO_WRAP
            )
            val profile = Profile(
                id = id,
                name = name,
                createdAt = System.currentTimeMillis(),
                isActive = profiles.isEmpty(),  // первый профиль становится активным
                isDecoy = isDecoy
            )
            profiles.add(profile)
            saveToStorage()
            return profile
        }
    }

    /**
     * Удалить профиль.
     * @return true если успешно, false если профиль активен или не найден
     */
    fun deleteProfile(id: String): Boolean {
        synchronized(lock) {
            val profile = profiles.find { it.id == id } ?: return false
            if (profile.isActive) return false  // нельзя удалить активный профиль
            profiles.remove(profile)
            saveToStorage()
            return true
        }
    }

    /**
     * Переключиться на другой профиль.
     * @return true если успешно
     */
    fun switchProfile(id: String): Boolean {
        synchronized(lock) {
            val target = profiles.find { it.id == id } ?: return false
            for (p in profiles) {
                profiles[profiles.indexOf(p)] = p.copy(isActive = p.id == id)
            }
            SecureStorage.putString(KEY_ACTIVE_PROFILE, id)
            saveToStorage()
            return true
        }
    }

    /** Получить активный профиль */
    fun getActiveProfile(): Profile? = synchronized(lock) { profiles.find { it.isActive } }

    /** Получить список всех профилей */
    fun getProfiles(): List<Profile> = synchronized(lock) { profiles.toList() }

    /** Получить имя файла БД для профиля */
    fun getProfileDatabaseName(id: String): String = "profile_$id.db"

    /** Получить имя файла настроек для профиля */
    fun getProfilePrefsName(id: String): String = "profile_prefs_$id"
}
