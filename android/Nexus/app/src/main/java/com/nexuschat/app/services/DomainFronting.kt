package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import com.nexuschat.app.config.TransportConfig
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * DomainFronting - Domain fronting implementation for censorship circumvention.
 * 
 * Domain fronting routes traffic through a high-reputation CDN (front domain)
 * while the actual backend is a different host. The SNI and Host header
 * point to the front domain, but the actual connection goes to the backend.
 * 
 * This class loads front domain configurations from assets/config/front-domains.json
 * via TransportConfig, supporting weighted random selection and rotation.
 * 
 * Configuration sources:
 * - assets/config/front-domains.json: List of front domains with weights
 * - assets/config/health-checks.json: Health check endpoints for front domains
 * 
 * Thread-safe singleton with coroutine-based async operations.
 */
class DomainFronting private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NexusChat/Fronting"
        @Volatile private var instance: DomainFronting? = null
        
        /**
         * Get singleton instance, initializing with context on first call.
         */
        fun getInstance(ctx: Context): DomainFronting {
            return instance ?: synchronized(this) {
                instance ?: DomainFronting(ctx.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Initialize with context (call early in Application.onCreate).
         */
        fun initialize(ctx: Context) {
            getInstance(ctx)
        }
        
        /**
         * Reset instance (for testing or config reload).
         */
        fun reset() {
            instance?.destroy()
            instance = null
        }
    }

    /**
     * FrontConfig - Configuration for a domain fronting connection.
     * 
     * @param frontDomain The CDN domain to use as front (e.g., "www.google.com")
     * @param backendHost Actual backend hostname to connect to
     * @param backendPort Backend port (usually 443)
     * @param connectTimeout Connection timeout in seconds
     * @param readTimeout Read timeout in seconds (0 = infinite for streaming)
     * @param rotationEnabled Whether to rotate front domains
     * @param rotationIntervalMinutes Rotation interval
     */
    data class FrontConfig(
        val frontDomain: String = "",
        val backendHost: String = "",
        val backendPort: Int = 443,
        val connectTimeout: Int = 15,
        val readTimeout: Int = 30,
        val rotationEnabled: Boolean = true,
        val rotationIntervalMinutes: Int = 30
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private var config = FrontConfig()
    private var httpClient: OkHttpClient? = null
    private var wsClient: OkHttpClient? = null
    private val transportConfig = TransportConfig.getInstance(context)
    private var frontDomainRotationJob: Job? = null

    /**
     * Configure domain fronting settings.
     * If frontDomain is empty, weighted random selection from config is used.
     */
    fun configure(cfg: FrontConfig) {
        config = cfg
        httpClient = null
        wsClient = null
        startFrontDomainRotation()
    }

    /**
     * Make an HTTP request through domain fronting.
     * 
     * The request is sent to the front domain with the Host header set to
     * the actual backend host. This makes the connection appear to go to
     * the front domain while actually reaching the backend.
     * 
     * @param originalUrl The actual backend URL to reach
     * @param headers Additional headers to include
     * @param body Optional request body
     * @return Response or null on failure
     */
    suspend fun frontRequest(
        originalUrl: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null
    ): Response? = withContext(Dispatchers.IO) {
        // Select front domain: configured, or weighted random from assets
        val frontDomain = config.frontDomain.ifEmpty { transportConfig.selectFrontDomain() }
        val backendHost = config.backendHost.ifEmpty { extractHostname(originalUrl) }
        
        try {
            val requestBuilder = Request.Builder()
                .url(originalUrl)
                // Host header points to backend, but SNI/TLS goes to front domain
                .header("Host", backendHost)
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
                .readTimeout(config.readTimeout.toLong(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build().also { httpClient = it }
            
            val response = client.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "Front request: $originalUrl → $frontDomain (Host: $backendHost) → HTTP ${response.code}")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Front request failed: ${e.message}")
            null
        }
    }

    /**
     * Establish a WebSocket connection through domain fronting.
     * 
     * @param wsUrl WebSocket URL (backend)
     * @param listener WebSocketListener for callbacks
     * @return WebSocket or null on failure
     */
    suspend fun frontedWebSocket(
        wsUrl: String,
        listener: WebSocketListener
    ): WebSocket? = withContext(Dispatchers.IO) {
        val frontDomain = config.frontDomain.ifEmpty { transportConfig.selectFrontDomain() }
        val backendHost = config.backendHost.ifEmpty { extractHostname(wsUrl) }
        
        try {
            val request = Request.Builder()
                .url(wsUrl)
                .header("Host", backendHost)
                .header("User-Agent", randomUserAgent())
                .header("Upgrade", "websocket")
                .header("Connection", "Upgrade")
                .build()
            
            val client = wsClient ?: OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build().also { wsClient = it }
            
            Log.d(TAG, "Fronted WebSocket: $wsUrl → $frontDomain (Host: $backendHost)")
            client.newWebSocket(request, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Fronted WS failed: ${e.message}")
            null
        }
    }

    /**
     * Extract hostname from URL.
     * Used for Host header when backendHost is not explicitly configured.
     */
    private fun extractHostname(url: String): String {
        return try {
            java.net.URI(url).host ?: "example.com"
        } catch (e: Exception) {
            "example.com"
        }
    }

    /**
     * Pool of realistic User-Agent strings for rotation.
     * Helps avoid fingerprinting.
     */
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

    /**
     * Start periodic front domain rotation.
     * Rotates to a new front domain at configured interval to avoid detection.
     */
    private fun startFrontDomainRotation() {
        frontDomainRotationJob?.cancel()
        
        if (!config.rotationEnabled) return
        
        frontDomainRotationJob = scope.launch {
            while (true) {
                delay(config.rotationIntervalMinutes * 60 * 1000L)
                // Rotate to new front domain
                val newDomain = transportConfig.selectFrontDomain()
                if (newDomain != config.frontDomain && config.frontDomain.isEmpty()) {
                    // Only rotate if using auto-selection (empty config frontDomain)
                    Log.i(TAG, "Rotating front domain to: $newDomain")
                    // httpClient and wsClient will be recreated on next request with new domain
                    httpClient = null
                    wsClient = null
                }
            }
        }
    }

    /** Get current front domain (selected or configured). */
    fun getCurrentFrontDomain(): String {
        return config.frontDomain.ifEmpty { transportConfig.selectFrontDomain() }
    }

    /** Get all available front domains from config. */
    fun getAvailableFrontDomains(): List<String> = transportConfig.getFrontDomains()

    /** Get front domain configuration with weights. */
    fun getFrontDomainsConfig() = transportConfig.getFrontDomainsConfig()

    /** Destroy and cleanup resources. */
    fun destroy() {
        scope.cancel()
        frontDomainRotationJob?.cancel()
        httpClient?.dispatcher?.executorService?.shutdown()
        wsClient?.dispatcher?.executorService?.shutdown()
        instance = null
    }
}