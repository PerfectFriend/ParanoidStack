/**
 * Менеджер push-уведомлений.
 * Управляет запуском/остановкой сервиса уведомлений,
 * отображением новых сообщений и регистрацией устройств
 * для получения push через SMP-протокол.
 */
package com.example.data

import android.content.Context
import android.util.Log
import com.example.service.SmpNotificationService

/** Менеджер push-уведомлений приложения */
class PushNotificationManager(private val context: Context) {
    private val tag = "PushNotificationManager"

    /** Запустить сервис уведомлений */
    fun startNotificationService() {
        try {
            SmpNotificationService.start(context)
            Log.i(tag, "Push notification service started")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start notification service", e)
        }
    }

    /** Показать уведомление о новом сообщении */
    fun notifyNewMessage(contactName: String, text: String) {
        SmpNotificationService.notifyMessage(context, contactName, text)
    }

    /** Зарегистрировать устройство для push-уведомлений через SMP */
    fun registerDeviceForPush(agent: SMPAgent, contactId: String): Boolean {
        return agent.enablePushNotifications(contactId)
    }
}
