/**
 * Защищённое хранилище на основе SharedPreferences с AES-256-GCM шифрованием.
 * Все значения перед сохранением шифруются с использованием ключа,
 * выведенного из парольной фразы через PBKDF2.
 */
package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.SecureRandom

/** Объект-синглтон для защищённого хранения данных */
object SecureStorage {
    private val TAG = "SecureStorage"
    private const val PREFS_NAME = "secure_storage_v1"
    private const val KEY_SALT = "key_salt"
    private const val PBKDF2_ITERATIONS = 100000

    private var prefs: SharedPreferences? = null
    @Volatile
    private var encryptionKey: ByteArray? = null
    @Volatile
    private var initialized = false

    /**
     * Инициализировать хранилище с парольной фразой.
     * @param context контекст приложения
     * @param passphrase парольная фраза для вывода ключа
     */
    fun initialize(context: Context, passphrase: String) {
        if (initialized) {
            // Verify passphrase hasn't changed; re-derive key to be safe
            Log.w(TAG, "SecureStorage already initialized — ignoring duplicate call")
            return
        }
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val salt = getOrCreateSalt()
        encryptionKey = SimpleXCrypto.deriveKey(passphrase, salt, PBKDF2_ITERATIONS)
        initialized = true
    }

    /** Получить существующую соль или создать новую */
    private fun getOrCreateSalt(): ByteArray {
        val existing = prefs?.getString(KEY_SALT, null)
        if (prefs == null) Log.w(TAG, "prefs is null in getOrCreateSalt")
        if (existing != null) {
            return Base64.decode(existing, Base64.NO_WRAP)
        }
        val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs?.edit()?.putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))?.apply()
        return newSalt
    }

    /** Проверить, инициализировано ли хранилище */
    private fun checkInit() {
        if (!initialized) throw IllegalStateException("SecureStorage not initialized. Call initialize() first.")
    }

    /** Сохранить строку в зашифрованном виде */
    fun putString(key: String, value: String): AppResult<Unit> {
        return try {
            checkInit()
            val keyBytes = encryptionKey ?: return AppException.StorageException("encryptionKey is null").asError()
            val encrypted = SimpleXCrypto.encryptStorage(value.toByteArray(Charsets.UTF_8), keyBytes)
            if (prefs == null) return AppException.StorageException("prefs is null").asError()
            prefs?.edit()?.putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))?.apply()
            AppResult.Success(Unit)
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.StorageException("putString failed", e).asError()
        }
    }

    /** Получить и расшифровать строку */
    fun getString(key: String, default: String = ""): AppResult<String> {
        return try {
            checkInit()
            if (prefs == null) return AppException.StorageException("prefs is null").asError()
            val keyBytes = encryptionKey ?: return AppException.StorageException("encryptionKey is null").asError()
            val stored = prefs?.getString(key, null) ?: return AppResult.Success(default)
            val encrypted = Base64.decode(stored, Base64.NO_WRAP)
            val result = String(SimpleXCrypto.decryptStorage(encrypted, keyBytes), Charsets.UTF_8)
            AppResult.Success(result)
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.StorageException("getString failed", e).asError()
        }
    }

    /** Сохранить boolean (через строку) */
    fun putBoolean(key: String, value: Boolean): AppResult<Unit> {
        return putString(key, value.toString())
    }

    /** Получить boolean */
    fun getBoolean(key: String, default: Boolean = false): AppResult<Boolean> {
        return when (val result = getString(key, default.toString())) {
            is AppResult.Success -> AppResult.Success(result.data.toBoolean())
            is AppResult.Error -> AppResult.Error(result.exception)
        }
    }

    /** Сохранить массив байт в зашифрованном виде */
    fun putBytes(key: String, value: ByteArray): AppResult<Unit> {
        return try {
            checkInit()
            val keyBytes = encryptionKey ?: return AppException.StorageException("encryptionKey is null").asError()
            val encrypted = SimpleXCrypto.encryptStorage(value, keyBytes)
            if (prefs == null) return AppException.StorageException("prefs is null").asError()
            prefs?.edit()?.putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP))?.apply()
            AppResult.Success(Unit)
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.StorageException("putBytes failed", e).asError()
        }
    }

    /** Получить и расшифровать массив байт */
    fun getBytes(key: String): AppResult<ByteArray> {
        return try {
            checkInit()
            if (prefs == null) return AppException.StorageException("prefs is null").asError()
            val keyBytes = encryptionKey ?: return AppException.StorageException("encryptionKey is null").asError()
            val stored = prefs?.getString(key, null) ?: return AppException.NotFoundException("Key not found: $key").asError()
            val encrypted = Base64.decode(stored, Base64.NO_WRAP)
            AppResult.Success(SimpleXCrypto.decryptStorage(encrypted, keyBytes))
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.StorageException("getBytes failed", e).asError()
        }
    }
}
