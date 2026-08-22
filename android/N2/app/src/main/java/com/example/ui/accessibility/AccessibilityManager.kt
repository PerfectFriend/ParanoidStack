package com.example.ui.accessibility

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/**
 * Менеджер специальных возможностей.
 * Позволяет адаптировать UI под текущие настройки
 * доступности: размер шрифта, контрастность, TalkBack.
 */
class AccessibilityUtils(private val context: Context) {

    fun isTalkBackEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    fun getFontScale(): Float {
        return context.resources.configuration.fontScale
    }

    fun isHighContrastEnabled(): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.FONT_SCALE, 0
        ) > 1.0f
    }

    fun getScaledSp(baseSp: Int): Int {
        return (baseSp * getFontScale()).toInt()
    }

    /**
     * Добавляет contentDescription к UI элементу на основе контекста.
     */
    fun describeElement(elementType: String, name: String, state: String = ""): String {
        return when (elementType) {
            "button" -> "$name. $state"
            "input" -> "$name. Поле ввода"
            "image" -> "$name. Изображение"
            "message" -> "Сообщение от $name. $state"
            "contact" -> "Контакт: $name. $state"
            else -> "$name $state"
        }
    }
}
