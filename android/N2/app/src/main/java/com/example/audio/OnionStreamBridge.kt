package com.example.audio

import android.net.Uri
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class OnionStreamBridge(private val socksPort: Int = 9050) {
    private var serverSocket: ServerSocket? = null
    var localPort: Int = 0
        private set
    private var isRunning = false

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(0)
            localPort = serverSocket?.localPort ?: 0
            isRunning = true
            thread(name = "OnionProxyBridgeThread") {
                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        Log.e("OnionStreamBridge", "accept failed", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OnionStreamBridge", "start failed", e)
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        thread {
            try {
                val inputReader = client.getInputStream().bufferedReader()
                val output = client.getOutputStream()
                val firstLine = inputReader.readLine() ?: return@thread
                if (!firstLine.startsWith("GET ")) {
                    try { client.close() } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
                    return@thread
                }
                val parts = firstLine.split(" ")
                if (parts.size < 2) return@thread
                val path = parts[1]
                val queryParam = path.substringAfter("url=", "")
                if (queryParam.isEmpty()) {
                    try { client.close() } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
                    return@thread
                }
                val targetUrl = URLDecoder.decode(queryParam, "UTF-8")
                var useLocalSocks = false
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress("127.0.0.1", socksPort), 400)
                    s.close()
                    useLocalSocks = true
                } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
                var finalUrl = targetUrl
                var proxyToUse = Proxy.NO_PROXY
                if (useLocalSocks) {
                    proxyToUse = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                } else {
                    val uri = Uri.parse(targetUrl)
                    val host = uri.host ?: ""
                    if (host.endsWith(".onion")) {
                        val gatewayHost = host.replace(".onion", ".onion.pet")
                        finalUrl = targetUrl.replace(host, gatewayHost)
                    }
                }
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("Icy-MetaData", "1")
                    .header("Connection", "keep-alive")
                    .build()
                val okHttpClient = OkHttpClient.Builder()
                    .proxy(proxyToUse)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body
                if (body == null) {
                    output.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".toByteArray())
                    try { client.close() } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
                    return@thread
                }
                output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                output.write("Content-Type: ${body.contentType() ?: "audio/mpeg"}\r\n".toByteArray())
                output.write("Connection: close\r\n\r\n".toByteArray())
                val instream = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (instream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
            } catch (e: Exception) {
                Log.e("OnionStreamBridge", "handleClient failed", e)
            } finally {
                try { client.close() } catch (_: java.lang.Exception) { Log.w("OnionStreamBridge", "ignored exception") }
            }
        }
    }
}
