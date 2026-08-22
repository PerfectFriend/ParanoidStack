package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class PushNotificationForegroundService : Service() {

    private val tag = "PushForegroundService"
    private val NOTIFICATION_ID = 2001
    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private val maxReconnectDelay = 30000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        scope.launch { connectWebSocket() }
        heartbeatJob = scope.launch { heartbeatLoop() }
        Log.i(tag, "Push foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        intent?.extras?.let { extras ->
            val title = extras.getString("title") ?: "N2"
            val body = extras.getString("body") ?: ""
            val chatId = extras.getString("chatId")
            FcmPushService.showLocalPushNotification(this, title, body, chatId)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatJob?.cancel()
        webSocket?.close(1000, "Service stopping")
        scope.cancel()
        Log.i(tag, "Push foreground service stopped")
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SERVICE)
            .setContentTitle("Push Service")
            .setContentText("Push-уведомления активны")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun connectWebSocket() {
        val pushServerUrl = "wss://n2.example.com/push"
        val request = Request.Builder()
            .url(pushServerUrl)
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@PushNotificationForegroundService.webSocket = webSocket
                reconnectAttempt = 0
                Log.i(tag, "WebSocket connected to push server")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    handlePushMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@PushNotificationForegroundService.webSocket = null
                scope.launch { reconnect() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(tag, "WebSocket failure: ${t.message}")
                this@PushNotificationForegroundService.webSocket = null
                scope.launch { reconnect() }
            }
        })
    }

    private fun handlePushMessage(text: String) {
        try {
            val parts = text.split("|", limit = 3)
            if (parts.size >= 2) {
                val title = parts[0].ifBlank { "N2" }
                val body = parts[1].ifBlank { "" }
                val chatId = parts.getOrNull(2)
                FcmPushService.showLocalPushNotification(this, title, body, chatId)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse push message: ${e.message}")
        }
    }

    private suspend fun reconnect() {
        val delayMs = minOf(1000L shl reconnectAttempt, maxReconnectDelay)
        reconnectAttempt++
        Log.i(tag, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
        delay(delayMs)
        connectWebSocket()
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            delay(30000)
            try { webSocket?.send("ping") } catch (_: java.lang.Exception) { Log.w("PushNotificationForegroundService", "ignored exception") }
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.action.STOP_PUSH_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, PushNotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PushNotificationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
