package com.example.ui.viewmodels

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.util.Log
import com.example.ui.UserTier
import java.util.Locale

/**
 * ViewModel для управления настройками темы, языка и уровня подписки.
 *
 * Извлечена из [com.example.ui.GameViewModel] как часть декомпозиции.
 */
class SettingsViewModel(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    var selectedTheme by mutableStateOf(prefs.getString("selected_theme", "warm") ?: "warm")
        private set

    var selectedLanguage by mutableStateOf(prefs.getString("selected_language", "EN") ?: "EN")
        private set

    var selectedChatLanguage by mutableStateOf(prefs.getString("selected_chat_language", "EN") ?: "EN")
        private set

    var currentUserTier by mutableStateOf(
        UserTier.valueOf(prefs.getString("user_tier", "FREE") ?: "FREE")
    )
        private set

    fun updateUserTier(tier: UserTier) {
        currentUserTier = tier
        prefs.edit().putString("user_tier", tier.name).apply()
    }

    fun updateTheme(themeId: String) {
        selectedTheme = themeId
        prefs.edit().putString("selected_theme", themeId).apply()
    }

    fun updateLanguage(lang: String, context: Context) {
        selectedLanguage = lang
        prefs.edit().putString("selected_language", lang).apply()
        try {
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val resources = context.resources
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            Log.e("SettingsViewModel", "language switch failed", e)
        }
    }

    fun updateChatLanguage(lang: String) {
        selectedChatLanguage = lang
        prefs.edit().putString("selected_chat_language", lang).apply()
    }
}
