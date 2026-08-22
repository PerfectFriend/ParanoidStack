/**
 * Каналы уведомлений приложения SimpleX.
 *
 * Определяет и создаёт каналы уведомлений для Android 8+:
 * сообщения, звонки, фоновый сервис, синхронизация и тревоги.
 * Каждый канал имеет свой уровень важности, звуковые настройки
 * и отображение на экране блокировки.
 */
package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Управление каналами уведомлений.
 */
object NotificationChannels {

    /** Канал входящих сообщений */
    const val CHANNEL_MESSAGES = "smp_messages"
    /** Канал входящих звонков */
    const val CHANNEL_CALLS = "smp_calls"
    /** Канал фонового сервиса уведомлений */
    const val CHANNEL_SERVICE = "smp_service"
    /** Канал синхронизации сообщений */
    const val CHANNEL_SYNC = "smp_sync"
    /** Канал критических уведомлений безопасности */
    const val CHANNEL_ALERTS = "smp_alerts"
    /** Канал push-уведомлений FCM */
    const val FCM_CHANNEL_ID = "fcm_messages"

    /**
     * Создаёт все каналы уведомлений (только для Android 8+).
     *
     * Каналы:
     * - Сообщения: высокий приоритет, вибрация, значки, свет, скрытый текст
     * - Звонки: высокий приоритет, вибрация, значки, скрытый текст
     * - Сервис: низкий приоритет, без значка
     * - Синхронизация: низкий приоритет, без значка
     * - Тревоги: высокий приоритет, вибрация, специальный звук (будильник)
     *
     * @param context Контекст приложения.
     */
    /** Registers all notification channels with the system (no-op on Android < O) */
    fun createAll(context: Context) {
        // На Android 7 и ниже каналы не поддерживаются
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Канал входящих сообщений — высокая важность, вибрация, свет, значки
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES, "Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Входящие сообщения SimpleX"
                enableVibration(true)
                setShowBadge(true)
                enableLights(true)
                lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
            }
        )

        // Канал входящих звонков — высокая важность, вибрация, значки
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CALLS, "Звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Входящие вызовы SimpleX"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
            }
        )

        // Канал фонового сервиса — низкая важность, без значка
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Сервис",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый сервис уведомлений"
                setShowBadge(false)
            }
        )

        // Канал синхронизации — низкая важность, без значка
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC, "Синхронизация",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Синхронизация сообщений"
                setShowBadge(false)
            }
        )

        // Канал тревог — высокая важность, вибрация, звук будильника
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS, "Тревоги",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Критические уведомления безопасности"
                enableVibration(true)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            }
        )

        // Канал push-уведомлений — высокая важность
        manager.createNotificationChannel(
            NotificationChannel(
                FCM_CHANNEL_ID, "Push-уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push-уведомления от сервера"
                enableVibration(true)
                setShowBadge(true)
                enableLights(true)
            }
        )
    }


}
