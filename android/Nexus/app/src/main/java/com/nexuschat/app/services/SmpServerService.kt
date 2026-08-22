package com.nexuschat.app.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nexuschat.app.MainActivity
import com.nexuschat.app.NexusChatApp
import com.nexuschat.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class SmpServerService : Service() {
    companion object {
        private const val TAG = "NexusChat/SMP"
        private const val NOTIF_ID = 1002
        private const val SMP_PROTOCOL_VERSION = 3
    }

    inner class SmpBinder : Binder() {
        fun getService(): SmpServerService = this@SmpServerService
    }

    private val binder = SmpBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private val rng = SecureRandom()

    private var wsClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var serverHost = ""
    private var serverPort = 5223
    private var localServerSocket: java.net.ServerSocket? = null
    var isConnected = false
        private set

    private val queues = ConcurrentHashMap<String, SmpQueue>()
    private val subscribers = ConcurrentHashMap<String, MutableList<String>>()
    private val pendingAcks = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val messageListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val incomingFrames = Channel<String>(Channel.UNLIMITED)

    private data class SmpQueue(
        val id: String,
        val recipientKey: String = "",
        val ratchetKey: String = "",
        val createdAt: Long = System.currentTimeMillis(),
        val messages: MutableList<SmpMessage> = Collections.synchronizedList(mutableListOf())
    )
    private data class SmpMessage(
        val id: String,
        val encrypted: String,
        val nonce: String,
        val timestamp: Long,
        val acked: Boolean = false
    )

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("SMP server starting..."))
        scope.launch { startLocalSmpServer() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            serverHost = it.getStringExtra("host") ?: ""
            serverPort = it.getIntExtra("port", 5223)
            if (serverHost.isNotEmpty()) scope.launch { connectToRemoteSmp() }
        }
        return START_STICKY
    }

    private suspend fun startLocalSmpServer() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting local SMP server (embedded protocol handler)")
        try {
            val serverSocket = java.net.ServerSocket(serverPort)
            localServerSocket = serverSocket
            scope.launch {
                while (isActive) {
                    try {
                        val client = serverSocket.accept()
                        scope.launch {
                            handleLocalClient(client)
                        }
                    } catch (e: java.io.IOException) { break }
                }
            }
            Log.i(TAG, "Local SMP server listening on port $serverPort")
            updateNotification("SMP local: 127.0.0.1:$serverPort")
        } catch (e: Exception) {
            Log.e(TAG, "Local SMP server failed: ${e.message}")
            updateNotification("SMP: local server error")
        }
        startFrameProcessor()
    }

    private fun handleLocalClient(client: java.net.Socket) {
        try {
            client.soTimeout = 60000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val magic = ByteArray(4)
            val magicRead = input.read(magic)
            val isBinary = magicRead == 4 && magic[0] == 'S'.code.toByte() && magic[1] == 'M'.code.toByte()
                && magic[2] == 'P'.code.toByte() && magic[3] == 0x03.toByte()

            if (isBinary) {
                output.write(byteArrayOf('S'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 0x03.toByte()))
                output.flush()
                handleBinaryClient(client, input, output)
            } else {
                val textReader = if (magicRead > 0) {
                    java.io.BufferedReader(java.io.InputStreamReader(
                        java.io.SequenceInputStream(
                            java.io.ByteArrayInputStream(magic.copyOf(magicRead)),
                            input
                        ), Charsets.UTF_8
                    ))
                } else {
                    java.io.BufferedReader(java.io.InputStreamReader(input, Charsets.UTF_8))
                }
                val writer = java.io.PrintWriter(output, true)
                while (client.isConnected) {
                    val line = textReader.readLine() ?: break
                    val response = processSmpFrame(line)
                    writer.println(response)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Local client disconnected: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleBinaryClient(client: java.net.Socket, input: java.io.InputStream, output: java.io.OutputStream) {
        client.soTimeout = 60000
        val dataInput = java.io.DataInputStream(input)
        val dataOutput = java.io.DataOutputStream(output)
        while (true) {
            try {
                val frameType = dataInput.readByte().toInt() and 0xFF
                val frameLen = dataInput.readInt()
                if (frameLen < 0 || frameLen > 65536) break
                val payload = ByteArray(frameLen)
                readFully(input, payload, frameLen)
                val json = when (frameType) {
                    0x01 -> { val qLen = dataInput.readInt(); val qBytes = ByteArray(qLen); dataInput.readFully(qBytes); String(qBytes, Charsets.UTF_8) }
                    0x02 -> String(payload, Charsets.UTF_8)
                    else -> String(payload, Charsets.UTF_8)
                }
                val response = processSmpFrame(json)
                if (response.isNotEmpty()) {
                    val respBytes = response.toByteArray(Charsets.UTF_8)
                    dataOutput.writeByte(0x02)
                    dataOutput.writeInt(respBytes.size)
                    dataOutput.write(respBytes)
                    dataOutput.flush()
                }
            } catch (e: java.io.EOFException) { break }
            catch (e: Exception) { Log.w(TAG, "Binary client error: ${e.message}"); break }
        }
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray, len: Int) {
        var offset = 0
        val deadline = System.currentTimeMillis() + 120000
        while (offset < len) {
            if (System.currentTimeMillis() > deadline) throw java.net.SocketTimeoutException("bounded read timeout")
            val read = input.read(buf, offset, len - offset)
            if (read < 0) throw java.io.EOFException()
            offset += read
        }
    }

    private fun buildTorClient(): OkHttpClient {
        val torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT))
        return OkHttpClient.Builder()
            .proxy(torProxy)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(TransportManager.TrafficObfuscatorInterceptor())
            .build()
    }

    private suspend fun connectToRemoteSmp() = withContext(Dispatchers.IO) {
        if (serverHost.isEmpty()) return@withContext
        wsClient = buildTorClient()
        val url = if (serverHost.endsWith(".onion"))
            "ws://$serverHost:$serverPort/simplex"
        else
            "wss://$serverHost:$serverPort/simplex"
        Log.i(TAG, "SMP connecting to $url via Tor SOCKS5")
        val request = Request.Builder().url(url).build()
        webSocket = wsClient!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                Log.i(TAG, "SMP WebSocket connected to $serverHost")
                updateNotification("SMP: connected to $serverHost")
                ws.send(gson.toJson(mapOf("cmd" to "PING", "corrId" to genId())))
            }
            override fun onMessage(ws: WebSocket, text: String) {
                Log.d(TAG, "SMP frame: ${text.take(120)}")
                incomingFrames.trySend(text)
                broadcastToListeners(text)
                val response = processSmpFrame(text)
                if (response.isNotEmpty()) ws.send(response)
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "SMP WS error: ${t.message}")
                updateNotification("SMP: reconnecting...")
                scheduleReconnect()
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.i(TAG, "SMP WS closed: $code $reason")
                if (code != 1000) scheduleReconnect()
            }
        })
    }

    private fun processSmpFrame(json: String): String {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val cmd = obj.get("cmd")?.asString ?: return ""
            val corrId = obj.get("corrId")?.asString ?: genId()
            val queueId = obj.get("queueId")?.asString ?: ""
            when (cmd) {
                "NEW" -> handleNewQueue(obj, corrId)
                "SUB" -> handleSubscribe(queueId, corrId)
                "SEND" -> handleSend(queueId, obj, corrId)
                "ACK" -> handleAck(queueId, obj)
                "DEL" -> handleDelete(queueId, corrId)
                "PING" -> gson.toJson(mapOf("cmd" to "OK", "corrId" to corrId, "body" to mapOf("ts" to System.currentTimeMillis())))
                "KEY" -> handleKey(queueId, obj, corrId)
                else -> gson.toJson(mapOf("cmd" to "ERR", "corrId" to corrId, "body" to mapOf("error" to "unknown command: $cmd")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Process frame error: ${e.message}")
            ""
        }
    }

    private fun handleNewQueue(obj: JsonObject, corrId: String): String {
        val queueId = newQueueId()
        val body = obj.getAsJsonObject("body")
        val recipientKey = body?.get("recipientKey")?.asString ?: ""
        val ratchetKey = body?.get("ratchetKey")?.asString ?: ""
        queues[queueId] = SmpQueue(
            id = queueId,
            recipientKey = recipientKey,
            ratchetKey = ratchetKey
        )
        subscribers[queueId] = mutableListOf()
        Log.i(TAG, "SMP queue created: $queueId")
        return gson.toJson(mapOf(
            "cmd" to "OK",
            "corrId" to corrId,
            "queueId" to queueId
        ))
    }

    private fun handleSubscribe(queueId: String, corrId: String): String {
        if (!queues.containsKey(queueId)) {
            return gson.toJson(mapOf("cmd" to "ERR", "corrId" to corrId,
                "body" to mapOf("error" to "queue not found")))
        }
        val sub = subscribers.getOrPut(queueId) { mutableListOf() }
        Log.i(TAG, "Subscribed to queue: $queueId")
        return gson.toJson(mapOf("cmd" to "OK", "corrId" to corrId, "queueId" to queueId))
    }

    private fun handleSend(queueId: String, obj: JsonObject, corrId: String): String {
        val queue = queues[queueId] ?: return gson.toJson(mapOf("cmd" to "ERR", "corrId" to corrId,
            "body" to mapOf("error" to "queue not found")))
        val body = obj.getAsJsonObject("body") ?: return gson.toJson(mapOf("cmd" to "ERR",
            "corrId" to corrId, "body" to mapOf("error" to "no body")))
        val encrypted = body.get("encrypted")?.asString ?: ""
        val nonce = body.get("nonce")?.asString ?: ""
        val msgId = genId(16)
        val msg = SmpMessage(id = msgId, encrypted = encrypted, nonce = nonce,
            timestamp = System.currentTimeMillis())
        queue.messages.add(msg)
        subscribers[queueId]?.forEach { subscriber ->
            broadcastToListeners(gson.toJson(mapOf(
                "cmd" to "MSG", "queueId" to queueId,
                "body" to mapOf("msgId" to msgId, "encrypted" to encrypted,
                    "nonce" to nonce, "ts" to msg.timestamp)
            )))
        }
        Log.i(TAG, "SMP message stored: queue=$queueId msg=$msgId")
        return gson.toJson(mapOf("cmd" to "ACK", "corrId" to corrId, "queueId" to queueId,
            "body" to mapOf("msgId" to msgId)))
    }

    private fun handleAck(queueId: String, obj: JsonObject): String {
        val body = obj.getAsJsonObject("body")
        val msgId = body?.get("msgId")?.asString ?: return ""
        val queue = queues[queueId]
        queue?.messages?.find { it.id == msgId }?.let {
            queues[queueId] = queue.copy(messages = queue.messages.map { m ->
                if (m.id == msgId) m.copy(acked = true) else m
            }.toMutableList())
        }
        return gson.toJson(mapOf("cmd" to "OK", "queueId" to queueId, "body" to mapOf("msgId" to msgId)))
    }

    private fun handleDelete(queueId: String, corrId: String): String {
        queues.remove(queueId)
        subscribers.remove(queueId)
        Log.i(TAG, "SMP queue deleted: $queueId")
        return gson.toJson(mapOf("cmd" to "OK", "corrId" to corrId, "queueId" to queueId))
    }

    private fun handleKey(queueId: String, obj: JsonObject, corrId: String): String {
        val body = obj.getAsJsonObject("body")
        val senderKey = body?.get("senderKey")?.asString ?: ""
        queues[queueId]?.let {
            Log.i(TAG, "SMP key exchange for queue $queueId")
        }
        return gson.toJson(mapOf("cmd" to "OK", "corrId" to corrId, "queueId" to queueId))
    }

    private fun startFrameProcessor() {
        scope.launch {
            for (frame in incomingFrames) {
                processSmpFrame(frame)
            }
        }
    }

    private fun broadcastToListeners(json: String) {
        messageListeners.forEach { listener ->
            try { listener(json) } catch (e: Exception) { Log.w(TAG, "Listener error: ${e.message}") }
        }
    }

    private fun scheduleReconnect() {
        scope.launch(Dispatchers.IO) {
            delay(5000)
            try { connectToRemoteSmp() } catch (e: Exception) {
                Log.e(TAG, "Reconnect failed: ${e.message}")
            }
        }
    }

    fun createQueue(onResult: (String?) -> Unit) {
        val corrId = genId()
        val queueId = newQueueId()
        val frame = mapOf(
            "cmd" to "NEW", "corrId" to corrId,
            "queueId" to queueId,
            "body" to mapOf("recipientKey" to genId(32), "ratchetKey" to genId(32))
        )
        queues[queueId] = SmpQueue(id = queueId)
        subscribers[queueId] = mutableListOf()
        onResult(queueId)
    }

    fun subscribeToQueue(queueId: String, onResult: (Boolean) -> Unit) {
        if (queues.containsKey(queueId)) {
            subscribers.getOrPut(queueId) { mutableListOf() }
            onResult(true)
        } else {
            sendFrameWithCallback(
                gson.toJson(mapOf("cmd" to "SUB", "corrId" to genId(), "queueId" to queueId)),
            ) { response ->
                val obj = gson.fromJson(response, JsonObject::class.java)
                onResult(obj.get("cmd")?.asString == "OK")
            }
        }
    }

    fun sendMessage(queueId: String, body: Map<String, Any?>, onResult: (Boolean) -> Unit) {
        val corrId = genId()
        val msgId = genId(16)
        val frame = gson.toJson(mapOf(
            "cmd" to "SEND", "corrId" to corrId, "queueId" to queueId, "body" to body
        ))
        val queue = queues[queueId]
        if (queue != null) {
            queue.messages.add(SmpMessage(id = msgId,
                encrypted = body["encrypted"]?.toString() ?: "",
                nonce = body["nonce"]?.toString() ?: "",
                timestamp = System.currentTimeMillis()))
            onResult(true)
        } else {
            sendFrameWithCallback(frame) { response ->
                val obj = gson.fromJson(response, JsonObject::class.java)
                onResult(obj.get("cmd")?.asString == "ACK")
            }
        }
    }

    fun deleteQueue(queueId: String, onResult: (Boolean) -> Unit) {
        queues.remove(queueId)
        subscribers.remove(queueId)
        onResult(true)
    }

    private fun sendFrameWithCallback(json: String, callback: (String) -> Unit) {
        val corrId = gson.fromJson(json, JsonObject::class.java).get("corrId")?.asString ?: return
        val completer = CompletableDeferred<String>()
        val listener: (String) -> Unit = { response ->
            try {
                val obj = gson.fromJson(response, JsonObject::class.java)
                if (obj.get("corrId")?.asString == corrId) {
                    completer.complete(response)
                }
            } catch (e: Exception) { Log.w(TAG, "sendFrameWithCallback parse: ${e.message}") }
        }
        messageListeners += listener
        sendFrame(json)
        scope.launch {
            try {
                val response = withTimeout(30000) { completer.await() }
                callback(response)
            } catch (e: Exception) {
                callback("")
            } finally {
                messageListeners -= listener
            }
        }
    }

    fun sendFrame(json: String): Boolean {
        return try {
            if (isConnected && webSocket != null) {
                webSocket?.send(json) ?: false
            } else true
        } catch (e: Exception) {
            Log.e(TAG, "SMP send error: ${e.message}")
            false
        }
    }

    fun addMessageListener(listener: (String) -> Unit) { messageListeners += listener }
    fun removeMessageListener(listener: (String) -> Unit) { messageListeners -= listener }

    private fun genId(size: Int = 8): String {
        val bytes = ByteArray(size)
        rng.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun newQueueId(): String {
        val bytes = ByteArray(24)
        rng.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NexusChatApp.CH_SMP)
            .setContentTitle("NexusChat SMP")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_smp)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service stopped")
        wsClient?.dispatcher?.executorService?.shutdown()
        try { localServerSocket?.close() } catch (_: Exception) {}
        localServerSocket = null
        scope.cancel()
        Log.i(TAG, "SmpServerService destroyed")
    }
}
