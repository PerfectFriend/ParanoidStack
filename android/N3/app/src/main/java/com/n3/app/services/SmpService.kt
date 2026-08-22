package com.n3.app.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.n3.app.MainActivity
import com.n3.app.N3App
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class SmpService : Service() {
    companion object {
        private const val TAG = "N3/SMP"
        private const val NOTIF_ID = 1002
    }

    inner class SmpBinder : Binder() { fun getService(): SmpService = this@SmpService }

    private val binder = SmpBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var ws: WebSocket? = null
    private var serverHost = ""
    private var serverPort = 5223
    @Volatile var isConnected = false; private set
    val messageListeners = CopyOnWriteArrayList<(String) -> Unit>()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, N3App.CH_SMP)
            .setContentTitle("N3 SMP").setContentText("Idle")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            serverHost = it.getStringExtra("host") ?: ""
            serverPort = it.getIntExtra("port", 5223)
            if (serverHost.isNotEmpty()) scope.launch { connect(serverHost, serverPort) }
        }
        return START_STICKY
    }

    private suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT)))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true).build()
        val url = if (host.endsWith(".onion")) "ws://$host:$port/simplex"
                  else "wss://$host:$port/simplex"
        Log.i(TAG, "Connecting to $url")
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true; Log.i(TAG, "Connected to $host")
                updateNotification("SMP: $host")
                webSocket.send(gson.toJson(mapOf("cmd" to "PING", "corrId" to java.util.UUID.randomUUID().toString())))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Frame: ${text.take(120)}")
                messageListeners.forEach { try { it(text) } catch (e: Exception) {} }
                try {
                    val obj = com.google.gson.JsonParser.parseString(text).asJsonObject
                    val cmd = if (obj.has("cmd")) obj.get("cmd").asString else ""
                    if (cmd == "MSG" || cmd == "FILE") {
                        showMessageNotification("$cmd: ${text.take(60)}")
                    }
                } catch (e: Exception) {}
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false; Log.e(TAG, "WS error: ${t.message}")
                updateNotification("SMP: reconnecting...")
                reconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                if (code != 1000) reconnect()
            }
        })
    }

    private fun reconnect() {
        val h = serverHost; val p = serverPort
        if (h.isNotEmpty()) scope.launch { delay(5000); connect(h, p) }
    }

    fun send(json: String) { ws?.send(json) }

    override fun onDestroy() {
        ws?.close(1000, "Service stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, N3App.CH_SMP)
            .setContentTitle("N3 SMP").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build())
    }

    private fun showMessageNotification(preview: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, N3App.CH_MSG)
            .setContentTitle("N3 Message")
            .setContentText(preview)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
