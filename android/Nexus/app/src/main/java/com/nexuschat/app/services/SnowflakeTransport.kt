package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import com.nexuschat.app.config.TransportConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SnowflakeTransport - Snowflake WebRTC-based transport for censorship circumvention.
 * 
 * Snowflake uses WebRTC to create ephemeral proxy connections through volunteer proxies.
 * It connects to a broker server to get matched with a proxy, then establishes a
 * WebRTC data channel for traffic.
 * 
 * This class loads configuration from assets/config/snowflake.json via TransportConfig,
 * including broker URL, front domain, STUN servers, and client settings.
 * 
 * Configuration sources:
 * - assets/config/snowflake.json: Broker URL, front domain, STUN servers, client config
 * - assets/config/bridges.json: Snowflake bridge configuration (fallback)
 * 
 * Thread-safe with coroutine-based async operations.
 */
class SnowflakeTransport(
    private val context: Context,
    private val onDataReceived: (ByteArray) -> Unit,
    private val onStatusChange: (Boolean, String) -> Unit
) {

    companion object {
        private const val TAG = "NexusChat/Snowflake"
        private const val DEFAULT_BROKER_URL = "https://snowflake-broker.torproject.net/"
        private const val MAX_OFFER_ATTEMPTS = 3
        @Volatile private var instance: SnowflakeTransport? = null
        
        /**
         * Get singleton instance.
         */
        fun getInstance(
            ctx: Context,
            onDataReceived: (ByteArray) -> Unit,
            onStatusChange: (Boolean, String) -> Unit
        ): SnowflakeTransport {
            return instance ?: synchronized(this) {
                instance ?: SnowflakeTransport(ctx.applicationContext, onDataReceived, onStatusChange).also { instance = it }
            }
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
     * SnowflakeConfig - Configuration for Snowflake transport.
     * Loaded from assets/config/snowflake.json via TransportConfig.
     */
    data class SnowflakeConfig(
        val brokerUrl: String = DEFAULT_BROKER_URL,
        val frontDomain: String = "snowflake.torproject.net",
        val stunServers: List<String> = emptyList(),
        val maxRetries: Int = 3,
        val connectionTimeoutMs: Int = 30000,
        val keepAliveIntervalMs: Int = 15000,
        val bridgeFingerprint: String = ""
    ) {
        /**
         * Convert STUN server strings to WebRTC IceServer objects.
         * Supports stun: and turn: URLs with optional credentials.
         */
        fun toIceServers(): List<PeerConnection.IceServer> {
            return stunServers.map { url ->
                when {
                    url.startsWith("turn:") -> parseTurnUrl(url)
                    url.startsWith("stun:") -> PeerConnection.IceServer.builder(url).createIceServer()
                    else -> PeerConnection.IceServer.builder("stun:$url").createIceServer()
                }
            }
        }
        
        private fun parseTurnUrl(url: String): PeerConnection.IceServer {
            // Format: turn:host:port?transport=udp&username=user&credential=pass
            val uri = java.net.URI(url.replaceFirst("turn:", "turn://"))
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 3478
            val query = uri.query ?: ""
            
            val params = query.split("&").associate {
                val parts = it.split("=")
                parts[0] to parts.getOrElse(1) { "" }
            }
            
            val username = params["username"] ?: ""
            val credential = params["credential"] ?: ""
            
            return if (username.isNotEmpty() && credential.isNotEmpty()) {
                PeerConnection.IceServer.builder("turn:$host:$port")
                    .setUsername(username)
                    .setPassword(credential)
                    .createIceServer()
            } else {
                PeerConnection.IceServer.builder("turn:$host:$port").createIceServer()
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val dataQueue = ConcurrentLinkedQueue<ByteArray>()
    private val _isConnected = AtomicBoolean(false)
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var factory: PeerConnectionFactory? = null
    private var config = SnowflakeConfig()
    private var retryCount = 0
    private var pollJob: Job? = null
    private val transportConfig = TransportConfig.getInstance(context)

    /** Current connection status. */
    val isConnected: Boolean get() = _isConnected.get()

    /**
     * Configure Snowflake transport.
     * If not provided, loads from assets/config/snowflake.json.
     */
    fun configure(cfg: SnowflakeConfig? = null) {
        if (cfg != null) {
            config = cfg
        } else {
            // Load from TransportConfig (assets)
            val fullConfig = transportConfig.getSnowflakeFullConfig()
            config = SnowflakeConfig(
                brokerUrl = fullConfig.brokerUrl,
                frontDomain = fullConfig.frontDomain,
                stunServers = fullConfig.stunServers,
                maxRetries = fullConfig.clientConfig.maxRetries,
                connectionTimeoutMs = fullConfig.clientConfig.connectionTimeoutMs,
                keepAliveIntervalMs = fullConfig.clientConfig.keepAliveIntervalMs
            )
        }
        Log.i(TAG, "Snowflake configured: broker=${config.brokerUrl}, front=${config.frontDomain}, STUN=${config.stunServers.size}")
    }

    /**
     * Initialize and connect to Snowflake broker.
     * Starts the WebRTC peer connection factory and begins broker connection.
     */
    fun connect() {
        scope.launch {
            try {
                initPeerConnectionFactory()
                connectThroughBroker()
            } catch (e: Exception) {
                Log.e(TAG, "Snowflake connect failed: ${e.message}")
                onStatusChange(false, "Failed: ${e.message}")
                if (retryCount < config.maxRetries) {
                    retryCount++
                    delay(3000L * retryCount)
                    connect()
                }
            }
        }
    }

    /**
     * Initialize WebRTC PeerConnectionFactory.
     * Must be called before creating peer connections.
     */
    private fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        Log.d(TAG, "PeerConnectionFactory initialized")
    }

    /**
     * Connect through Snowflake broker.
     * Creates offer, sends to broker, receives answer, sets remote description.
     */
    private suspend fun connectThroughBroker() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Connecting to Snowflake broker: ${config.brokerUrl}")
        
        for (attempt in 1..MAX_OFFER_ATTEMPTS) {
            try {
                val offerSdp = createOffer()
                val brokerResponse = sendOfferToBroker(offerSdp)
                
                if (brokerResponse != null) {
                    val answerSdp = parseBrokerResponse(brokerResponse)
                    setRemoteAnswer(answerSdp)
                    _isConnected.set(true)
                    onStatusChange(true, "Snowflake connected via broker")
                    startPolling()
                    Log.i(TAG, "Snowflake connected on attempt $attempt")
                    return@withContext
                }
            } catch (e: Exception) {
                Log.w(TAG, "Broker attempt $attempt failed: ${e.message}")
                delay(2000L * attempt)
            }
        }
        throw Exception("All broker connection attempts failed")
    }

    /**
     * Create WebRTC offer SDP for data channel.
     * Configures ICE servers from config (STUN/TURN from assets).
     */
    private fun createOffer(): String {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        // Convert STUN/TURN URLs to IceServer objects
        val iceServers = config.toIceServers()
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate: ${candidate.sdp}")
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "Snowflake ICE state: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    _isConnected.set(false)
                    onStatusChange(false, "ICE disconnected: $state")
                } else if (state == PeerConnection.IceConnectionState.CONNECTED ||
                           state == PeerConnection.IceConnectionState.COMPLETED) {
                    Log.i(TAG, "ICE connected successfully")
                }
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onSignalingChange(s: PeerConnection.SignalingState) {}
            override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(c: Array<IceCandidate>) {}
            override fun onRemoveStream(s: MediaStream) {}
            
            override fun onDataChannel(channel: DataChannel) {
                Log.i(TAG, "Snowflake data channel received from remote")
                setupDataChannel(channel)
            }
            
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) {}
        })
        
        // Create data channel for bidirectional traffic
        val dataChannelInit = DataChannel.Init().apply {
            ordered = false          // Unordered for lower latency
            maxRetransmits = 0       // No retransmits (unreliable, low latency)
            negotiated = true        // Pre-negotiated ID
            id = 1
            protocol = "snowflake"
        }
        
        dataChannel = peerConnection?.createDataChannel("snowflake", dataChannelInit)
        setupDataChannel(dataChannel!!)
        
        // Create offer
        var resultSdp = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { latch.countDown() }
                    override fun onSetFailure(error: String) { 
                        Log.e(TAG, "Set local failed: $error")
                        latch.countDown() 
                    }
                    override fun onCreateSuccess(s: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, sdp)
                resultSdp = sdp.description
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) { 
                Log.e(TAG, "Create offer failed: $error")
                latch.countDown() 
            }
            override fun onSetFailure(error: String) { latch.countDown() }
        }, constraints)
        
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return resultSdp
    }

    /**
     * Send WebRTC offer to Snowflake broker.
     * POSTs JSON with SDP offer to broker endpoint.
     */
    private fun sendOfferToBroker(offerSdp: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${config.brokerUrl}broker")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = config.connectionTimeoutMs
            
            val payload = JSONObject().apply {
                put("sdp", offerSdp)
                put("type", "offer")
                put("fingerprint", config.bridgeFingerprint)
            }
            
            conn.outputStream.write(payload.toString().toByteArray())
            val responseCode = conn.responseCode
            
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                Log.d(TAG, "Broker response: ${response.take(200)}")
                return response
            }
            
            Log.w(TAG, "Broker HTTP $responseCode")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Broker HTTP error: ${e.message}")
            return null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /** Parse broker response JSON to extract answer SDP. */
    private fun parseBrokerResponse(response: String): String {
        val json = JSONObject(response)
        return json.getString("sdp")
    }

    /** Set remote answer SDP on peer connection. */
    private fun setRemoteAnswer(answerSdp: String) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully")
            }
            override fun onSetFailure(error: String) { 
                Log.e(TAG, "Set remote answer failed: $error") 
            }
            override fun onCreateSuccess(s: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sdp)
    }

    /**
     * Set up data channel observers for bidirectional communication.
     * Handles incoming messages and connection state changes.
     */
    private fun setupDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previous: Long) {}
            
            override fun onStateChange() {
                val state = channel.state()
                Log.d(TAG, "Snowflake DC state: $state")
                if (state == DataChannel.State.OPEN) {
                    Log.i(TAG, "Data channel OPEN - ready for traffic")
                } else if (state == DataChannel.State.CLOSED ||
                           state == DataChannel.State.CLOSING) {
                    Log.w(TAG, "Data channel closed: $state")
                    _isConnected.set(false)
                    onStatusChange(false, "Data channel closed")
                }
            }
            
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onDataReceived(data)
            }
        })
    }

    /** Start polling loop for sending queued data. */
    private fun startPolling() {
        pollJob = scope.launch {
            while (_isConnected.get()) {
                val data = dataQueue.poll()
                if (data != null && dataChannel?.state() == DataChannel.State.OPEN) {
                    val buffer = DataChannel.Buffer(
                        java.nio.ByteBuffer.wrap(data),
                        false
                    )
                    dataChannel?.send(buffer)
                }
                delay(50) // 20ms polling interval
            }
        }
    }

    /**
     * Send data through Snowflake data channel.
     * Queues data for polling loop to send.
     */
    fun send(data: ByteArray) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataQueue.offer(data)
        }
    }

    /** Disconnect and cleanup. */
    fun disconnect() {
        _isConnected.set(false)
        pollJob?.cancel()
        dataChannel?.close()
        peerConnection?.close()
        Log.i(TAG, "Snowflake disconnected")
    }

    /** Destroy and cleanup all resources. */
    fun destroy() {
        disconnect()
        scope.cancel()
        factory?.dispose()
        instance = null
    }
}