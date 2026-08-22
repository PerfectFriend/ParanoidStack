package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.SecureRandom

class MeekTransport private constructor(
    private val onStatusChange: (Boolean, String) -> Unit
) {
    companion object {
        private const val TAG = "NexusChat/Meek"
        private const val POLL_INTERVAL_MS = 100L
        private const val LOCAL_PORT = 9555
        private const val MAX_PENDING = 65536
        @Volatile private var instance: MeekTransport? = null
        fun getInstance(onStatus: (Boolean, String) -> Unit): MeekTransport =
            instance ?: synchronized(this) {
                instance ?: MeekTransport(onStatus).also { instance = it }
            }
    }

    data class MeekConfig(
        val frontDomain: String = "meek.azureedge.net",
        val frontPort: Int = 443,
        val backendHost: String = "",
        val backendPort: Int = 443,
        val localPort: Int = LOCAL_PORT,
        val pollingInterval: Long = POLL_INTERVAL_MS,
        val useTls: Boolean = true
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private var config = MeekConfig()
    private var isRunning = false
    private var sessionId = ""
    private val pendingData = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    private val receivedData = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    private var serverSocket: ServerSocket? = null
    private var connections = mapOf<String, Socket>()

    fun start(config: MeekConfig) {
        this.config = config
        sessionId = java.util.UUID.randomUUID().toString().take(8)
        scope.launch {
            try {
                serverSocket = ServerSocket(config.localPort)
                isRunning = true
                onStatusChange(true, "Meek listening on :${config.localPort}")
                Log.i(TAG, "Meek transport started front=${config.frontDomain} session=$sessionId")
                scope.launch { pollLoop() }
                acceptLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Meek start failed: ${e.message}")
                onStatusChange(false, e.message ?: "start failed")
            }
        }
    }

    private suspend fun acceptLoop() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val client = serverSocket!!.accept()
                scope.launch { handleLocalConnection(client) }
            } catch (e: Exception) { if (isRunning) Log.w(TAG, "Accept: ${e.message}") }
        }
    }

    private suspend fun handleLocalConnection(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val buf = ByteArray(8192)
            val read = client.inputStream.read(buf)
            if (read > 0) {
                pendingData.add(buf.copyOf(read))
            }
            Unit
        } catch (e: Exception) {
            Log.w(TAG, "Local conn: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private suspend fun pollLoop() = withContext(Dispatchers.IO) {
        val baseUrl = "${if (config.useTls) "https" else "http"}://${config.frontDomain}:${config.frontPort}/"
        val obfuscatedPath = "/${sessionId}/" + java.util.UUID.randomUUID().toString().take(8).replace("-", "")
        while (isRunning) {
            try {
                val dataToSend = collectPendingData()
                val response = meekRoundTrip(baseUrl + obfuscatedPath, dataToSend)
                if (response != null) {
                    dispatchResponse(response)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Meek poll: ${e.message}")
            }
            delay(config.pollingInterval)
        }
    }

    private fun collectPendingData(): ByteArray? {
        if (pendingData.isEmpty()) return null
        val bos = ByteArrayOutputStream()
        pendingData.forEach { bos.write(it) }
        pendingData.clear()
        val data = bos.toByteArray()
        val header = ByteArray(4)
        header[0] = (data.size shr 24).toByte()
        header[1] = (data.size shr 16).toByte()
        header[2] = (data.size shr 8).toByte()
        header[3] = data.size.toByte()
        return header + data
    }

    private fun meekRoundTrip(url: String, data: ByteArray?): ByteArray? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = if (data != null) "POST" else "GET"
            conn.doInput = true
            conn.doOutput = data != null
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", randomUserAgent())
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("X-Session-Id", sessionId)
            conn.instanceFollowRedirects = false
            if (data != null) {
                conn.outputStream.write(data)
                conn.outputStream.flush()
            }
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                conn.inputStream.readBytes()
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Meek round-trip error: ${e.message}")
            null
        }
    }

    private fun dispatchResponse(data: ByteArray) {
        if (data.size < 4) return
        val len = ((data[0].toInt() and 0xFF) shl 24) or
                  ((data[1].toInt() and 0xFF) shl 16) or
                  ((data[2].toInt() and 0xFF) shl 8) or
                  (data[3].toInt() and 0xFF)
        if (len > 0 && data.size >= 4 + len) {
            val payload = data.copyOfRange(4, 4 + len)
            receivedData.add(payload)
        }
    }

    private fun randomUserAgent(): String {
        val agents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 Safari/605.1.15",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0.6099.230 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
        )
        return agents[rng.nextInt(agents.size)]
    }

    fun readReceived(): ByteArray? = receivedData.poll()

    fun getSessionId(): String = sessionId

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        Log.i(TAG, "Meek transport stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
