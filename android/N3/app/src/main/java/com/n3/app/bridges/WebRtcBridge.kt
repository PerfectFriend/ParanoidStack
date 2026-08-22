package com.n3.app.bridges

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.webkit.JavascriptInterface
import com.n3.app.config.TransportConfig
import com.n3.app.services.AudioRelay
import com.n3.app.services.TorService
import com.n3.app.services.TransportManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*

/**
 * WebRtcBridge - WebRTC bridge for audio/video calls over Tor/transport.
 * 
 * This class bridges between JavaScript (WebView) and native WebRTC implementation.
 * It handles:
 * - PeerConnectionFactory initialization
 * - Audio/video call setup (offer/answer/ICE)
 * - Audio relay through configured transport (Tor, Snowflake, etc.)
 * - Statistics reporting to JavaScript
 * - Speakerphone/mute controls
 * 
 * Configuration:
 * - STUN/TURN servers loaded from assets/config/stun-turn.json via TransportConfig
 * - Transport selection via TransportManager
 * - Audio relay URL and peer ID from JavaScript
 * 
 * All methods annotated with @JavascriptInterface are callable from WebView JS.
 * Thread-safe with coroutine-based async operations on Main dispatcher.
 */
class WebRtcBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit
) {

    companion object {
        private const val TAG = "NexusChat/WebRTC"
    }

    // Coroutine scope on Main dispatcher for JS callbacks
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // WebRTC factory and peer connection
    private var factory: PeerConnectionFactory? = null
    private var peerConn: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    
    // Stats and audio relay
    private var statsJob: Job? = null
    private var isInitialised = false
    private var audioRelay: AudioRelay? = null
    private var transportManager: TransportManager? = null
    
    // Relay configuration from JS
    private var relayUrl = ""
    private var peerId = ""
    
    // AudioManager for speakerphone control
    private val audioManager: AudioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Initialize WebRTC PeerConnectionFactory.
     * Must be called before any other WebRTC operations.
     * Called automatically by initCall() if not already initialized.
     */
    @JavascriptInterface
    fun initFactory() {
        if (isInitialised) return
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(ctx)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            val options = PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            }
            factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            isInitialised = true
            Log.i(TAG, "WebRTC PeerConnectionFactory initialised")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC init failed: ${e.message}")
        }
    }

    /**
     * Configure transport relay URL and peer ID.
     * Called from JavaScript before starting a call.
     * 
     * @param relayUrlParam WebSocket/HTTP relay URL for audio relay
     * @param peerIdParam Remote peer identifier
     */
    @JavascriptInterface
    fun configureTransport(relayUrlParam: String, peerIdParam: String) {
        this.relayUrl = relayUrlParam
        this.peerId = peerIdParam
        transportManager = try {
            TransportManager.getInstance(ctx)
        } catch (e: Exception) { null }
        Log.i(TAG, "Audio relay configured: $relayUrlParam peer=$peerIdParam")
    }

    /**
     * Initialize and start a WebRTC call.
     * Creates peer connection, local audio stream, and starts audio relay.
     */
    @JavascriptInterface
    fun initCall() {
        scope.launch {
            try {
                if (!isInitialised) initFactory()
                val f = factory ?: return@launch
                initNativeWebRtc(f)
                startAudioRelay()
            } catch (e: Exception) {
                Log.e(TAG, "initCall error: ${e.message}", e)
                callJs("window.onRtcDisconnected && window.onRtcDisconnected()")
            }
        }
    }

    /**
     * Initialize native WebRTC components.
     * Creates audio source/track, local stream, peer connection with ICE servers.
     * 
     * @param f PeerConnectionFactory instance
     */
    private fun initNativeWebRtc(f: PeerConnectionFactory) {
        // Audio constraints: echo cancellation, noise suppression, auto gain
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        
        audioSource = f.createAudioSource(audioConstraints)
        audioTrack = f.createAudioTrack("audio0", audioSource)
        localStream = f.createLocalMediaStream("stream0")
        localStream!!.addTrack(audioTrack)

        // Load ICE servers from TransportConfig (assets/stun-turn.json)
        val transportConfig = TransportConfig.getInstance(ctx)
        val stunServers = transportConfig.getStunServers()
        val turnServers = transportConfig.getTurnServers()
        
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        
        // Add STUN servers
        iceServers.addAll(stunServers.map { PeerConnection.IceServer.builder(it).createIceServer() })
        
        // Add TURN servers with credentials
        iceServers.addAll(turnServers.map { turn ->
            if (turn.username.isNotEmpty() && turn.credential.isNotEmpty()) {
                PeerConnection.IceServer.builder(turn.url)
                    .setUsername(turn.username)
                    .setPassword(turn.credential)
                    .createIceServer()
            } else {
                PeerConnection.IceServer.builder(turn.url).createIceServer()
            }
        })
        
        // Fallback to defaults if no config loaded
        if (iceServers.isEmpty()) {
            iceServers.addAll(listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
            ))
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConn = f.createPeerConnection(rtcConfig, makeObserver()) ?: return
        peerConn!!.addStream(localStream)

        // Create offer for audio only
        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConn!!.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConn!!.setLocalDescription(SdpObserverAdapter(), sdp)
                val mungedSdp = mungeAudioSdp(sdp.description)
                callJs("window.onRtcOffer && window.onRtcOffer(${com.google.gson.Gson().toJson(mungedSdp)})")
                Log.i(TAG, "SDP offer created with TURN relay")
            }
            
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "Offer failed: $error")
            }
        }, offerConstraints)
    }

    /**
     * Start audio relay through configured transport.
     * Uses AudioRelay service for transport-agnostic audio streaming.
     */
    private fun startAudioRelay() {
        if (relayUrl.isNotEmpty() && peerId.isNotEmpty()) {
            try {
                audioRelay = AudioRelay.getInstance(ctx, transportManager
                    ?: TransportManager.getInstance(ctx))
                audioRelay?.onAudioPacketSent = { packet ->
                    val safe = com.google.gson.Gson().toJson(mapOf(
                        "rtt" to -1, "codec" to "OPUS",
                        "packetsLost" to 0, "relayBytes" to packet.size
                    ))
                    callJs("window.onRtcStats && window.onRtcStats($safe)")
                }
                audioRelay?.startCall(relayUrl, peerId)
            } catch (e: Exception) {
                Log.e(TAG, "Audio relay start error: ${e.message}")
            }
        } else {
            Log.w(TAG, "No relay URL configured — using direct P2P")
        }
        startStatsPolling()
    }

    /**
     * Update call relay configuration.
     * Can be called during active call to change relay.
     */
    @JavascriptInterface
    fun setCallRelay(relayUrlParam: String, peerIdParam: String) {
        this.relayUrl = relayUrlParam
        this.peerId = peerIdParam
        if (isInitialised && audioRelay == null) {
            startAudioRelay()
        }
    }

    /**
     * Set remote SDP (offer or answer) from signaling.
     * Called from JavaScript when receiving remote session description.
     * 
     * @param sdpJson JSON string with "type" (offer/answer) and "sdp" fields
     */
    @JavascriptInterface
    fun setRemoteSdp(sdpJson: String) {
        scope.launch {
            try {
                val obj = JSONObject(sdpJson)
                val type = SessionDescription.Type.fromCanonicalForm(obj.getString("type"))
                val sdp = SessionDescription(type, obj.getString("sdp"))
                peerConn?.setRemoteDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Remote SDP set (${type.name})")
                        if (type == SessionDescription.Type.OFFER) createAnswer()
                    }
                }, sdp)
            } catch (e: Exception) { Log.e(TAG, "setRemoteSdp: ${e.message}") }
        }
    }

    /**
     * Create and send SDP answer for received offer.
     */
    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConn?.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConn?.setLocalDescription(SdpObserverAdapter(), sdp)
                callJs("window.onRtcAnswer && window.onRtcAnswer(${com.google.gson.Gson().toJson(sdp.description)})")
            }
        }, constraints)
    }

    /**
     * Add ICE candidate from signaling.
     * Called from JavaScript when receiving remote ICE candidates.
     * 
     * @param candidateJson JSON with "sdpMid", "sdpMLineIndex", "candidate" fields
     */
    @JavascriptInterface
    fun addIceCandidate(candidateJson: String) {
        try {
            val obj = JSONObject(candidateJson)
            val cand = IceCandidate(
                obj.getString("sdpMid"),
                obj.getInt("sdpMLineIndex"),
                obj.getString("candidate")
            )
            peerConn?.addIceCandidate(cand)
        } catch (e: Exception) { Log.e(TAG, "addIceCandidate: ${e.message}") }
    }

    /**
     * Mute/unmute local microphone.
     * 
     * @param muted true to mute, false to unmute
     */
    @JavascriptInterface
    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
        Log.i(TAG, if (muted) "Mic muted" else "Mic unmuted")
    }

    /**
     * Enable/disable speakerphone (audio routing).
     * 
     * This is a REAL implementation using AudioManager.setSpeakerphoneOn()
     * instead of the previous stub. It routes audio output to speaker or earpiece.
     * 
     * @param enabled true for speakerphone, false for earpiece
     */
    @JavascriptInterface
    fun setSpeakerphone(enabled: Boolean) {
        try {
            // AudioManager.setSpeakerphoneOn() is the standard API for speakerphone control
            // Requires MODIFY_AUDIO_SETTINGS permission (granted to system apps)
            audioManager.isSpeakerphoneOn = enabled
            Log.i(TAG, "Speakerphone ${if (enabled) "ON" else "OFF"} via AudioManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone: ${e.message}")
            // Fallback: try legacy method for older Android versions
            try {
                val method = AudioManager::class.java.getMethod("setSpeakerphoneOn", Boolean::class.java)
                method.invoke(audioManager, enabled)
                Log.i(TAG, "Speakerphone set via reflection fallback")
            } catch (reflectError: Exception) {
                Log.e(TAG, "Speakerphone reflection fallback also failed: ${reflectError.message}")
            }
        }
    }

    /**
     * End current call and cleanup all resources.
     */
    @JavascriptInterface
    fun endCall() {
        scope.launch {
            statsJob?.cancel()
            audioRelay?.endCall()
            audioTrack?.setEnabled(false)
            audioSource?.dispose()
            localStream?.dispose()
            peerConn?.close()
            peerConn = null
            localStream = null
            audioSource = null
            audioTrack = null
            audioRelay = null
            Log.i(TAG, "Call ended")
        }
    }

    /**
     * Create PeerConnection observer for ICE/connection events.
     * Forwards events to JavaScript via evalJs callback.
     */
    private fun makeObserver() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val safe = com.google.gson.Gson().toJson(mapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "candidate" to candidate.sdp
            ))
            callJs("window.onRtcIceCandidate && window.onRtcIceCandidate($safe)")
        }
        
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "ICE: $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED ->
                    callJs("window.onRtcConnected && window.onRtcConnected()")
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED ->
                    callJs("window.onRtcDisconnected && window.onRtcDisconnected()")
                else -> {}
            }
        }
        
        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE receiving: $receiving")
        }
        
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.firstOrNull()?.setEnabled(true)
            Log.i(TAG, "Remote audio stream added")
        }
        
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(c: Array<IceCandidate>) {}
        override fun onRemoveStream(s: MediaStream) {}
        override fun onDataChannel(d: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) {}
    }

    /**
     * Start periodic stats polling.
     * Reports RTT, codec, packet loss, and active transport to JavaScript every 3 seconds.
     */
    private fun startStatsPolling() {
        statsJob = scope.launch {
            while (true) {
                delay(3000)
                peerConn?.getStats { report ->
                    var rtt = -1.0
                    var codec = "OPUS"
                    var loss = 0
                    
                    report.statsMap.values.forEach { stats ->
                        when (stats.type) {
                            "remote-inbound-rtp" -> {
                                rtt = (stats.members["roundTripTime"] as? Double) ?: rtt
                                loss = ((stats.members["packetsLost"] as? Double)?.toInt()) ?: loss
                            }
                            "codec" -> {
                                val mime = stats.members["mimeType"] as? String ?: ""
                                if (mime.contains("audio", ignoreCase = true))
                                    codec = mime.substringAfter("/").uppercase()
                            }
                        }
                    }
                    
                    val rttMs = if (rtt >= 0) (rtt * 1000).toInt() else -1
                    val transportName = try { 
                        TransportManager.getInstance(ctx).activeTransport.name 
                    } catch (e: Exception) { "TOR" }
                    
                    val safe = com.google.gson.Gson().toJson(mapOf(
                        "rtt" to rttMs, "codec" to codec,
                        "packetsLost" to loss, "transport" to transportName
                    ))
                    callJs("window.onRtcStats && window.onRtcStats($safe)")
                }
            }
        }
    }

    /**
     * Remove video media section from SDP (audio-only calls).
     */
    private fun mungeAudioSdp(sdp: String): String {
        return sdp.lines()
            .filter { !it.startsWith("m=video") }
            .joinToString("\r\n")
    }

    /** Helper to call JavaScript callback. */
    private fun callJs(js: String) = evalJs(js)
}

/**
 * SdpObserverAdapter - Base implementation of SdpObserver with empty methods.
 * Subclasses only override the methods they need.
 */
open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) { Log.e("SDP", "create: $error") }
    override fun onSetFailure(error: String) { Log.e("SDP", "set: $error") }
}