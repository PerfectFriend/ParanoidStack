package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class SnowflakeTransport(
    private val onDataReceived: (ByteArray) -> Unit,
    private val onStatusChange: (Boolean, String) -> Unit
) {
    companion object {
        private const val TAG = "NexusChat/Snowflake"
        private const val BROKER_URL = "https://snowflake-broker.torproject.net/"
        private const val DEFAULT_SNOWFLAKE = "api.snowflake.torproject.org"
        private const val MAX_OFFER_ATTEMPTS = 3
    }

    data class SnowflakeConfig(
        val brokerUrl: String = BROKER_URL,
        val maxRetries: Int = 3,
        val iceServers: List<String> = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun.cloudflare.com:3478",
            "stun:stun.voiparound.com"
        ),
        val bridgeFingerprint: String = ""
    )

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
    private var brokerPollJob: Job? = null

    val isConnected: Boolean get() = _isConnected.get()

    fun configure(cfg: SnowflakeConfig) {
        config = cfg
    }

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

    private fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(
                com.nexuschat.app.NexusChatApp.instance
            ).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

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

    private fun createOffer(): String {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        val iceServers = config.iceServers.map {
            PeerConnection.IceServer.builder(it).createIceServer()
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "Snowflake ICE state: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    _isConnected.set(false)
                    onStatusChange(false, "ICE disconnected")
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
                Log.i(TAG, "Snowflake data channel received")
                setupDataChannel(channel)
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) {}
        })
        val dataChannelInit = DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
            negotiated = true
            id = 1
        }
        dataChannel = peerConnection?.createDataChannel("snowflake", dataChannelInit)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previous: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "Snowflake DC state: ${dataChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onDataReceived(data)
            }
        })
        var resultSdp = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() { latch.countDown() }
                    override fun onSetFailure(error: String) { latch.countDown() }
                    override fun onCreateSuccess(s: SessionDescription) {}
                    override fun onCreateFailure(error: String) {}
                }, sdp)
                resultSdp = sdp.description
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) { latch.countDown(); Log.e(TAG, "Offer fail: $error") }
            override fun onSetFailure(error: String) { latch.countDown() }
        }, constraints)
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        return resultSdp
    }

    private fun sendOfferToBroker(offerSdp: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${config.brokerUrl}broker")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
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

    private fun parseBrokerResponse(response: String): String {
        val json = JSONObject(response)
        return json.getString("sdp")
    }

    private fun setRemoteAnswer(answerSdp: String) {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {}
            override fun onSetFailure(error: String) { Log.e(TAG, "Set remote fail: $error") }
            override fun onCreateSuccess(s: SessionDescription) {}
            override fun onCreateFailure(error: String) {}
        }, sdp)
    }

    private fun setupDataChannel(channel: DataChannel) {
        dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previous: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                onDataReceived(data)
            }
        })
    }

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
                delay(100)
            }
        }
    }

    fun send(data: ByteArray) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            dataQueue.offer(data)
        }
    }

    fun disconnect() {
        _isConnected.set(false)
        pollJob?.cancel()
        dataChannel?.close()
        peerConnection?.close()
        Log.i(TAG, "Snowflake disconnected")
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
