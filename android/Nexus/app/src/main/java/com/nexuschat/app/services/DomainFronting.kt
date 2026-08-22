package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class DomainFronting private constructor() {
    companion object {
        private const val TAG = "NexusChat/Fronting"
        @Volatile private var instance: DomainFronting? = null
        fun getInstance(): DomainFronting =
            instance ?: synchronized(this) {
                instance ?: DomainFronting().also { instance = it }
            }
    }

    data class FrontConfig(
        val frontDomain: String = "www.google.com",
        val backendHost: String = "",
        val backendPort: Int = 443,
        val connectTimeout: Int = 15,
        val readTimeout: Int = 30
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private var config = FrontConfig()
    private var httpClient: OkHttpClient? = null
    private val frontProviders = listOf(
        "cloudflare.com",
        "www.google.com",
        "www.bing.com",
        "www.baidu.com",
        "www.yahoo.com",
        "www.amazon.com"
    )

    fun configure(cfg: FrontConfig) {
        config = cfg
        httpClient = null
    }

    suspend fun frontRequest(
        originalUrl: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): Response? = withContext(Dispatchers.IO) {
        val frontDomain = config.frontDomain.ifEmpty { frontProviders[rng.nextInt(frontProviders.size)] }
        try {
            val requestBuilder = Request.Builder()
                .url(originalUrl)
                .header("Host", extractHostname(originalUrl))
                .header("User-Agent", randomUserAgent())
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .header("Connection", "keep-alive")
            for ((k, v) in headers) {
                requestBuilder.header(k, v)
            }
            if (body != null) {
                requestBuilder.post(RequestBody.create(null, body))
            }
            val client = httpClient ?: OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build().also { httpClient = it }
            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "Front request: $originalUrl → HTTP ${response.code}")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Front request failed: ${e.message}")
            null
        }
    }

    suspend fun frontedWebSocket(
        wsUrl: String,
        listener: WebSocketListener
    ): WebSocket? = withContext(Dispatchers.IO) {
        val frontDomain = config.frontDomain.ifEmpty { frontProviders[rng.nextInt(frontProviders.size)] }
        try {
            val request = Request.Builder()
                .url(wsUrl)
                .header("Host", extractHostname(wsUrl))
                .header("User-Agent", randomUserAgent())
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .build()
            httpClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Fronted WS failed: ${e.message}")
            null
        }
    }

    private fun extractHostname(url: String): String {
        return try {
            java.net.URI(url).host ?: config.backendHost.ifEmpty { "example.com" }
        } catch (e: Exception) {
            config.backendHost.ifEmpty { "example.com" }
        }
    }

    private fun randomUserAgent(): String {
        val agents = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2) AppleWebKit/605.1.15 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 Chrome/120.0.6099.230 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_1 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
        )
        return agents[rng.nextInt(agents.size)]
    }

    fun destroy() {
        scope.cancel()
        instance = null
    }
}
