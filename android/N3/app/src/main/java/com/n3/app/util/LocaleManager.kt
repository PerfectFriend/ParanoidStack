package com.n3.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleManager {
    private const val PREFS_NAME = "n3_locale"
    private const val KEY_OVERRIDE = "locale_override"

    private val SUPPORTED = listOf("en", "ru")
    private const val DEFAULT = "en"

    fun detectAndApply(ctx: Context): Context {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val override = prefs.getString(KEY_OVERRIDE, null)

        val lang = override ?: detectSystemLang(ctx)
        return if (lang != getCurrentLang(ctx)) setLocale(ctx, lang) else ctx
    }

    private fun detectSystemLang(ctx: Context): String {
        val config = ctx.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0)
        } else { @Suppress("DEPRECATION") config.locale }
        val lang = locale.language
        return if (lang in SUPPORTED) lang else DEFAULT
    }

    fun setLocale(ctx: Context, lang: String): Context {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_OVERRIDE, lang).apply()
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(lang))
        return ctx.createConfigurationContext(cfg)
    }

    fun getCurrentLang(ctx: Context): String {
        val config = ctx.resources.configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales.get(0).language
        } else { @Suppress("DEPRECATION") config.locale.language }
    }
}
