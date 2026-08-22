/**
 * Фоновый сервис уведомлений SimpleX.
 *
 * Работает как foreground-сервис для обеспечения получения
 * push-уведомлений о новых сообщениях. Отображает постоянное
 * уведомление о работе сервиса и создаёт отдельные уведомления
 * для каждого входящего сообщения.
 */
package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.*

/**
 * Foreground-сервис для отображения уведомлений SimpleX.
 *
 * Сервис поддерживает постоянное уведомление о своей работе
 * и создаёт отдельные уведомления для входящих сообщений
 * от контактов. Использует канал уведомлений "Сообщения".
 * Для Android 8+ запускается через startForegroundService().
 */
class SmpNotificationService : Service() {

    private val tag = "SmpNotificationService"
    /** ID постоянного уведомления foreground-сервиса */
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        // Запускаем сервис как foreground с постоянным уведомлением
        startForeground(NOTIFICATION_ID, createNotification())
        Log.i(tag, "Notification service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Если в Intent переданы данные о сообщении, показываем уведомление
        intent?.extras?.let { extras ->
            val contactName = extras.getString("contact_name", "Unknown")
            val messageText = extras.getString("message_text", "")
            showMessageNotification(contactName, messageText)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(tag, "Notification service stopped")
        super.onDestroy()
    }

    /**
     * Создаёт постоянное уведомление для foreground-сервиса.
     *
     * Уведомление отображается в строке состояния всё время,
     * пока сервис активен. При нажатии открывает MainActivity.
     *
     * @return Notification для foreground-сервиса.
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_MESSAGES)
            .setContentTitle("SimpleX Secure")
            .setContentText("Сервис уведомлений активен")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Показывает уведомление о входящем сообщении от контакта.
     *
     * Использует hashCode имени контакта как ID уведомления,
     * чтобы уведомления от разных контактов не перезаписывали
     * друг друга. Текст сообщения обрезается до 200 символов.
     *
     * @param contactName Имя контакта-отправителя.
     * @param text        Текст входящего сообщения.
     */
    /** Posts a notification for an incoming message, keyed by contact name hash for deduplication */
    private fun showMessageNotification(contactName: String, text: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("contact", contactName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, contactName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CHANNEL_MESSAGES)
            .setContentTitle(contactName)
            .setContentText(text.take(200)) // Обрезаем длинные сообщения
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Уведомление исчезает при нажатии
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Используем hashCode имени как ID — разные контакты = разные уведомления
        // This prevents notifications from the same contact from overwriting each other
        manager.notify(contactName.hashCode(), notification)
    }

    companion object {
        /**
         * Запускает foreground-сервис уведомлений.
         *
         * @param context Контекст приложения.
         */
        fun start(context: Context) {
            val intent = Intent(context, SmpNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Отправляет команду сервису показать уведомление о сообщении.
         *
         * @param context      Контекст приложения.
         * @param contactName  Имя контакта-отправителя.
         * @param text         Текст сообщения.
         */
        fun notifyMessage(context: Context, contactName: String, text: String) {
            val intent = Intent(context, SmpNotificationService::class.java).apply {
                putExtra("contact_name", contactName)
                putExtra("message_text", text)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
