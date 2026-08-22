/**
 * Пакет безопасности — Duress PIN.
 * Реализует механизм «экстренного PIN-кода» (duress pin): при вводе такого кода
 * приложение делает вид, что разблокировано, но на самом деле запускает процедуру
 * сброса/зачистки чувствительных данных.
 */
package com.example.security

import android.content.Context
import android.util.Base64
import com.example.data.SecureStorage
import com.example.data.getOrNull
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Результат проверки PIN-кода.
 * [MATCH_MAIN] — основной PIN совпал,
 * [MATCH_DURESS] — экстренный PIN совпал,
 * [INVALID] — неверный PIN.
 */
enum class PinResult {
    MATCH_MAIN,
    MATCH_DURESS,
    INVALID
}

/**
 * Конфигурация PIN-кодов: хеш основного PIN, хеш duress PIN и флаг активности duress-режима.
 * @property mainPinHash хеш основного PIN (с солью).
 * @property duressPinHash хеш экстренного PIN (может быть null, если не задан).
 * @property isDuressActive флаг, указывающий, что режим принуждения активен.
 */
data class PinConfig(
    val mainPinHash: String,
    val duressPinHash: String?,
    val isDuressActive: Boolean
)

/**
 * Менеджер для работы с основным и экстренным (duress) PIN-кодами.
 * Хеши хранятся в [SecureStorage] с солью (SHA-256).
 */
object DuressPinManager {
    private const val PREFS_KEY = "duress_pin_config"
    private const val HASH_ALGO = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 10000
    private const val PBKDF2_KEY_LENGTH = 256
    private var currentConfig: PinConfig? = null

    /**
     * Инициализирует менеджер: загружает сохранённую конфигурацию.
     * @param context контекст приложения.
     */
    fun initialize(context: Context) {
        loadConfig()
    }

    /** Загружает конфигурацию из [SecureStorage]. */
    private fun loadConfig() {
        val stored = SecureStorage.getString(PREFS_KEY).getOrNull() ?: ""
        if (stored.isEmpty()) {
            currentConfig = null
            return
        }
        try {
            val json = JSONObject(stored)
            val duressHash = if (json.has("duressPinHash") && !json.isNull("duressPinHash"))
                json.getString("duressPinHash") else null
            currentConfig = PinConfig(
                mainPinHash = json.getString("mainPinHash"),
                duressPinHash = duressHash,
                isDuressActive = json.optBoolean("isDuressActive", false)
            )
        } catch (e: Exception) {
            currentConfig = null
        }
    }

    /** Сохраняет текущую конфигурацию в [SecureStorage]. */
    private fun saveConfig() {
        val config = currentConfig ?: return
        val json = JSONObject()
        json.put("mainPinHash", config.mainPinHash)
        if (config.duressPinHash != null) json.put("duressPinHash", config.duressPinHash)
        json.put("isDuressActive", config.isDuressActive)
        SecureStorage.putString(PREFS_KEY, json.toString())
    }

    /**
     * Вычисляет хеш PIN-кода с солью.
     * Формат: base64(salt):base64(hash)
     * @param pin исходный PIN.
     * @return строка с солью и хешем.
     */
    /** Computes a salted SHA-256 hash of the PIN, returned as "base64(salt):base64(hash)" */
    private fun hashWithSalt(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = javax.crypto.SecretKeyFactory.getInstance(HASH_ALGO)
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
               Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Проверяет PIN-код по сохранённому хешу с солью.
     * @param pin проверяемый PIN.
     * @param stored сохранённая строка вида "salt:hash".
     * @return true, если PIN совпадает.
     */
    /** Verifies a PIN against the stored "salt:hash" string using constant-time-ish comparison */
    private fun verifyHash(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)
            val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
            val factory = javax.crypto.SecretKeyFactory.getInstance(HASH_ALGO)
            val actualHash = factory.generateSecret(spec).encoded
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (e: Exception) { false }
    }

    /**
     * Устанавливает основной и экстренный PIN-коды.
     * @param mainPin новый основной PIN.
     * @param duressPin новый экстренный PIN (null для отключения).
     */
    fun configurePins(mainPin: String, duressPin: String?) {
        val mainHash = hashWithSalt(mainPin)
        val duressHash = duressPin?.let { hashWithSalt(it) }
        currentConfig = PinConfig(
            mainPinHash = mainHash,
            duressPinHash = duressHash,
            isDuressActive = false
        )
        saveConfig()
    }

    /**
     * Проверяет введённый PIN и возвращает результат.
     * @param pin введённый PIN.
     * @return [PinResult.MATCH_MAIN], [PinResult.MATCH_DURESS] или [PinResult.INVALID].
     */
    /** Checks the PIN against the main hash first, then the duress hash; returns the match result */
    fun verifyPin(pin: String): PinResult {
        val config = currentConfig ?: return PinResult.INVALID
        if (verifyHash(pin, config.mainPinHash)) {
            return PinResult.MATCH_MAIN
        }
        if (config.duressPinHash != null && verifyHash(pin, config.duressPinHash)) {
            return PinResult.MATCH_DURESS
        }
        return PinResult.INVALID
    }

    /**
     * Проверяет, является ли введённый PIN экстренным.
     * @param pin введённый PIN.
     * @return true, если это duress PIN.
     */
    fun isDuressPin(pin: String): Boolean {
        val config = currentConfig ?: return false
        return config.duressPinHash != null && verifyHash(pin, config.duressPinHash)
    }

    /** Активирует режим принуждения (duress). */
    fun triggerDuressMode(): Boolean {
        val config = currentConfig ?: return false
        currentConfig = config.copy(isDuressActive = true)
        saveConfig()
        return true
    }

    /** @return true, если режим принуждения активен. */
    fun isInDuressMode(): Boolean = currentConfig?.isDuressActive == true

    /** Сбрасывает режим принуждения. */
    fun clearDuressMode() {
        val config = currentConfig ?: return
        currentConfig = config.copy(isDuressActive = false)
        saveConfig()
    }
}
