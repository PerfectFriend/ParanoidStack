/**
 * Менеджер резервного копирования и восстановления профилей.
 * Экспортирует профили в зашифрованный AES-256-GCM файл
 * и импортирует их обратно. Использует парольную фразу для шифрования.
 */
package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.SecureRandom

/** Менеджер бэкапа профилей */
class ProfileBackupManager(private val context: Context) {
    private val tag = "ProfileBackupManager"

    /** Заголовок файла бэкапа */
    data class BackupHeader(
        val version: Int = 1,                          // версия формата
        val createdAt: Long = System.currentTimeMillis(), // дата создания
        val profileCount: Int,                          // количество профилей
        val encryptedWith: String = "AES-256-GCM"      // алгоритм шифрования
    )

    /**
     * Экспортировать профили в зашифрованный ByteArray.
     * Формат: соль (16 байт) + зашифрованные данные.
     *
     * @param password пароль для шифрования
     * @return зашифрованные данные (соль + шифротекст)
     */
    fun exportProfiles(password: String): ByteArray {
        val profiles = ProfileManager.getProfiles()
        val activeId = ProfileManager.getActiveProfile()?.id ?: ""

        val json = JSONObject().apply {
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("activeProfileId", activeId)
            put("profileCount", profiles.size)

            val profilesArray = JSONArray()
            for (p in profiles) {
                profilesArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("createdAt", p.createdAt)
                    put("isDecoy", p.isDecoy)
                })
            }
            put("profiles", profilesArray)
        }

        val plaintext = json.toString(2).encodeToByteArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = SimpleXCrypto.deriveKey(password, salt)
        val encrypted = SimpleXCrypto.encryptStorage(plaintext, key)

        return salt + encrypted
    }

    /**
     * Импортировать профили из зашифрованного ByteArray.
     * @param data данные (соль + шифротекст)
     * @param password пароль для расшифровки
     * @return true если импорт успешен
     */
    fun importProfiles(data: ByteArray, password: String): Boolean {
        return try {
            val salt = data.copyOfRange(0, 16)
            val encrypted = data.copyOfRange(16, data.size)
            val key = SimpleXCrypto.deriveKey(password, salt)
            val plaintext = SimpleXCrypto.decryptStorage(encrypted, key)
            val json = JSONObject(String(plaintext, Charsets.UTF_8))

            // Verify backup format version
            val backupVersion = json.optInt("version", 1)
            if (backupVersion > 1) {
                Log.w(tag, "Backup format version $backupVersion is newer than supported (1) — some data may be lost")
            }

            val profilesArray = json.getJSONArray("profiles")
            for (i in 0 until profilesArray.length()) {
                val p = profilesArray.getJSONObject(i)
                ProfileManager.createProfile(
                    name = p.getString("name"),
                    isDecoy = p.optBoolean("isDecoy", false)
                )
            }

            // Восстанавливаем активный профиль, если указан
            val activeId = json.optString("activeProfileId", "")
            if (activeId.isNotEmpty()) {
                ProfileManager.switchProfile(activeId)
            }

            Log.i(tag, "Imported ${profilesArray.length()} profiles")
            true
        } catch (e: Exception) {
            Log.e(tag, "Import failed", e)
            false
        }
    }

    /** Сохранить данные бэкапа в файл кэша */
    fun saveBackupToFile(data: ByteArray, fileName: String): Uri? {
        return try {
            val file = File(context.cacheDir, fileName)
            file.writeBytes(data)
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(tag, "Save backup failed", e)
            null
        }
    }

    /** Загрузить данные бэкапа из файла */
    fun loadBackupFromFile(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.readBytes()
        } catch (e: Exception) {
            Log.e(tag, "Load backup failed", e)
            null
        }
    }
}
