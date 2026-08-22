/**
 * Пакет безопасности приложения.
 * Содержит компоненты для защиты экрана, буфера обмена и аутентификации.
 */
package com.example.security

import android.app.Activity
import android.view.WindowManager
import java.lang.ref.WeakReference

/**
 * Менеджер защиты экрана от скриншотов и записи экрана.
 * Использует флаг [WindowManager.LayoutParams.FLAG_SECURE] для запрета
 * отображения содержимого окна в списке последних приложений и скриншотах.
 */
object ScreenSecurityManager {
    /** Слабая ссылка на текущую Activity (чтобы не создавать утечку памяти). */
    private var activityRef: WeakReference<Activity>? = null

    /**
     * Регистрирует Activity для управления защитой экрана.
     * @param activity целевая Activity.
     */
    fun registerActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** Включает защиту экрана (FLAG_SECURE). */
    /** Applies FLAG_SECURE to prevent screenshots and screen recording */
    fun enableScreenSecurity() {
        val activity = activityRef?.get() ?: return
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    /** Отключает защиту экрана (снимает FLAG_SECURE). */
    /** Removes FLAG_SECURE to allow screenshots and screen recording */
    fun disableScreenSecurity() {
        val activity = activityRef?.get() ?: return
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Проверяет, активна ли защита экрана.
     * @return true, если FLAG_SECURE установлен.
     */
    /** Checks whether FLAG_SECURE is currently set on the registered activity's window */
    fun isScreenSecure(): Boolean {
        val activity = activityRef?.get() ?: return false
        return (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }

    /**
     * Возвращает true, если скриншоты разрешены (флаг FLAG_SECURE не установлен).
     */
    fun isScreenshotAllowed(): Boolean = !isScreenSecure()

    /**
     * Включает или отключает защиту от скриншотов для указанной Activity.
     * @param activity целевая Activity.
     * @param prevent true — установить FLAG_SECURE, false — снять.
     */
    /** Convenience method to toggle FLAG_SECURE on/off for a given activity */
    fun preventScreenshot(activity: Activity, prevent: Boolean) {
        if (prevent) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Освобождает ссылку на Activity. */
    fun release() {
        activityRef?.clear()
        activityRef = null
    }
}
