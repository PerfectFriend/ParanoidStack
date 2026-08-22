/**
 * Менеджер динамических ярлыков приложения.
 *
 * Создаёт на главном экране устройства ярлыки для быстрого доступа:
 * новый чат, новая группа, настройки и сканирование QR-кода.
 * Ярлыки создаются один раз при запуске и остаются до удаления пользователем.
 */
package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Создаёт набор динамических ярлыков приложения на главном экране.
 *
 * Если устройство не поддерживает закрепление ярлыков, функция
 * завершается без ошибки. Ярлыки создаются для четырёх действий:
 * новый чат, новая группа, настройки и сканирование QR-кода.
 *
 * @param context Контекст приложения.
 * @return true, если создание ярлыков выполнено, false — не поддерживается.
 */
/** Creates and registers dynamic app shortcuts for quick access to key features */
fun createShortcuts(context: Context): Boolean {
    // Проверяем, поддерживает ли устройство закрепление ярлыков на главном экране
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        return false
    }

    // Ярлык "Новый чат" — открывает экран создания нового чата
    val newChatIntent = Intent(Intent.ACTION_MAIN).apply {
        putExtra("com.example.action.NEW_CHAT", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val newChatShortcut = ShortcutInfoCompat.Builder(context, "new_chat")
        .setShortLabel("New Chat")
        .setLongLabel("Open New Chat")
        .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_input_add))
        .setIntent(newChatIntent)
        .build()

    // Ярлык "Новая группа" — создание новой группы
    val newGroupIntent = Intent(Intent.ACTION_MAIN).apply {
        putExtra("com.example.action.NEW_GROUP", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val newGroupShortcut = ShortcutInfoCompat.Builder(context, "new_group")
        .setShortLabel("New Group")
        .setLongLabel("Create New Group")
        .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_add))
        .setIntent(newGroupIntent)
        .build()

    // Ярлык "Настройки" — быстрый переход к экрану настроек
    val settingsIntent = Intent(Intent.ACTION_MAIN).apply {
        putExtra("com.example.action.SETTINGS", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val settingsShortcut = ShortcutInfoCompat.Builder(context, "settings")
        .setShortLabel("Settings")
        .setLongLabel("Open Settings")
        .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_preferences))
        .setIntent(settingsIntent)
        .build()

    // Ярлык "Сканировать QR" — запуск камеры для сканирования QR-кода
    val scanQrIntent = Intent(Intent.ACTION_MAIN).apply {
        putExtra("com.example.action.SCAN_QR", true)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val scanQrShortcut = ShortcutInfoCompat.Builder(context, "scan_qr")
        .setShortLabel("Scan QR")
        .setLongLabel("Scan QR Code")
        .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_camera))
        .setIntent(scanQrIntent)
        .build()

    // Регистрируем все четыре ярлыка как динамические (будут показаны на главном экране)
    ShortcutManagerCompat.pushDynamicShortcut(context, newChatShortcut)
    ShortcutManagerCompat.pushDynamicShortcut(context, newGroupShortcut)
    ShortcutManagerCompat.pushDynamicShortcut(context, settingsShortcut)
    ShortcutManagerCompat.pushDynamicShortcut(context, scanQrShortcut)

    return true
}
