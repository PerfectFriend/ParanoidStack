/**
 * Local push notification receiver for in-app messages.
 *
 * Listens for broadcast intents with action [PushReceiver.ACTION_PUSH]
 * and displays notifications in the FCM notification channel.
 * A simplified push mechanism that does not require
 * the full Firebase Cloud Messaging dependency.
 */
package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Сервис push-уведомлений (не-FCM версия).
 * Получает push-уведомления через локальный BroadcastReceiver
 * и отображает их в канале "smp_messages".
 * Для реального FCM требуется подключение библиотеки firebase-messaging.
 */
/**
 * BroadcastReceiver that handles locally broadcast push intents and displays notifications.
 *
 * Receives pushes via [ACTION_PUSH], extracts title/body/chatId extras,
 * and posts a system notification using [NotificationChannels.FCM_CHANNEL_ID].
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "N2"
        val body = intent.getStringExtra("body") ?: ""
        val chatId = intent.getStringExtra("chatId")

        showNotification(context, title, body, chatId)
    }

    companion object {
        /** Intent action for local push broadcasts */
        const val ACTION_PUSH = "com.example.action.PUSH_NOTIFICATION"
    }

    /** Builds and posts a push notification with a PendingIntent back to MainActivity */
    private fun showNotification(context: Context, title: String, body: String, chatId: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("contactId", chatId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // FLAG_ONE_SHOT ensures the PendingIntent fires only once; FLAG_IMMUTABLE for Android 12+
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.FCM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Uses current time as a unique notification ID to avoid overwriting
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

/**
 * Fallback FCM-like push service that works without the firebase-messaging library.
 * Provides [onMessageReceived] for handling notification payloads and
 * [showLocalPushNotification] for internal use (e.g. when messages arrive via SMP/Tor).
 */
class FcmPushService {

    companion object {
        private const val TAG = "FcmPushService"

        /** Handles a push payload (simulates Firebase's onMessageReceived) */
        fun onMessageReceived(context: Context, data: Map<String, String>) {
            val title = data["title"] ?: data["messageTitle"] ?: "N2"
            val body = data["body"] ?: data["messageBody"] ?: ""
            val chatId = data["chatId"]
            Log.d(TAG, "Push received: title=$title chatId=$chatId")
            showLocalPushNotification(context, title, body, chatId)
        }

        /**
         * Helper to show a local push notification.
         * Can be called by the app when network messages arrive via SMP/Tor
         * without requiring Firebase at all.
         */
        fun showLocalPushNotification(
            context: Context,
            title: String,
            body: String,
            chatId: String? = null
        ) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("contactId", chatId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.FCM_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
