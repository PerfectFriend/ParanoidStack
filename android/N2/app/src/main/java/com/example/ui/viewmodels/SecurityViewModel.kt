package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import com.example.data.Bip39Helper
import com.example.security.DuressPinManager
import com.example.security.PinResult

/**
 * ViewModel для управления криптоконтейнером, Duress PIN и seed-фразами.
 *
 * Извлечена из [com.example.ui.GameViewModel] как часть декомпозиции.
 */
class SecurityViewModel(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    /** Смонтирован ли криптоконтейнер */
    var isCryptocontainerMounted by mutableStateOf(prefs.getBoolean("cryptocontainer_mounted", false))
        internal set

    /** Текущая BIP39 seed-фраза */
    var currentSeedPhrase by mutableStateOf("")
        private set

    /** PIN-код доступа */
    var pinCode by mutableStateOf(prefs.getString("pin_code", "123456") ?: "123456")
        private set

    init {
        val stored = prefs.getString("cryptocontainer_seed_phrase", "") ?: ""
        currentSeedPhrase = stored.ifEmpty { Bip39Helper.generateMnemonic(context) }
        if (stored.isEmpty()) {
            prefs.edit().putString("cryptocontainer_seed_phrase", currentSeedPhrase).apply()
        }
    }

    // ── PIN / Duress ──────────────────────────────────────

    /** Обновляет PIN-код доступа и синхронизирует с DuressPinManager. */
    fun updatePinCode(newPin: String) {
        pinCode = newPin
        prefs.edit().putString("pin_code", newPin).apply()
        val existingDuressPin = prefs.getString("duress_pin", null)
        DuressPinManager.configurePins(newPin, existingDuressPin)
    }

    /** Устанавливает Duress PIN для экстренного сброса. */
    fun setDuressPin(duressPin: String?) {
        prefs.edit().putString("duress_pin", duressPin).apply()
        DuressPinManager.configurePins(pinCode, duressPin)
    }

    /** Проверяет PIN с учётом режима Duress. */
    fun verifyPinWithDuressCheck(pin: String): PinResult {
        val result = DuressPinManager.verifyPin(pin)
        if (result == PinResult.MATCH_DURESS) {
            handleDuressTrigger()
        }
        return result
    }

    /** Обрабатывает срабатывание Duress: очищает сессионные данные. */
    fun handleDuressTrigger() {
        DuressPinManager.triggerDuressMode()
        isCryptocontainerMounted = false
        prefs.edit().putBoolean("cryptocontainer_mounted", false).apply()
    }

    // ── Seed / Key ────────────────────────────────────────

    /** Вычисляет производный ключ SHA-256 из seed-фразы. */
    fun getDerivedKey(seed: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(seed.trim().lowercase().toByteArray(Charsets.UTF_8))
            digest.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            "CRAZY-DEFAULT-SALT-KEY-2026"
        }
    }

    /** Заменяет seed-фразу и сохраняет в SharedPreferences. */
    fun regenerateSeed(context: Context) {
        currentSeedPhrase = Bip39Helper.generateMnemonic(context)
        prefs.edit().putString("cryptocontainer_seed_phrase", currentSeedPhrase).apply()
    }
}
