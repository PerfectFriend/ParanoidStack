package com.nexuschat.app.bridges

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import com.nexuschat.app.services.AudioRelay
import com.nexuschat.app.services.TorService
import com.nexuschat.app.services.TransportManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*

class WebRtcBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit
) {
    companion object {
        private const val TAG = "NexusChat/WebRTC"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var factory: PeerConnectionFactory? = null
    private var peerConn: PeerConnection? = null
    private var localStream: MediaStream? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var statsJob: Job? = null
    private var isInitialised = false
    private var audioRelay: AudioRelay? = null
    private var transportManager: TransportManager? = null
    private var relayUrl = ""
    private var peerId = ""

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

    @JavascriptInterface
    fun configureTransport(relayUrlParam: String, peerIdParam: String) {
        this.relayUrl = relayUrlParam
        this.peerId = peerIdParam
        transportManager = try {
            TransportManager.getInstance(ctx)
        } catch (e: Exception) { null }
        Log.i(TAG, "Audio relay configured: $relayUrlParam peer=$peerIdParam")
    }

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

    private fun initNativeWebRtc(f: PeerConnectionFactory) {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
        }
        audioSource = f.createAudioSource(audioConstraints)
        audioTrack = f.createAudioTrack("audio0", audioSource)
        localStream = f.createLocalMediaStream("stream0")
        localStream!!.addTrack(audioTrack)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceTransportsType = PeerConnection.IceTransportsType.RELAY
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peerConn = f.createPeerConnection(rtcConfig, makeObserver()) ?: return
        peerConn!!.addStream(localStream)

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

    @JavascriptInterface
    fun setCallRelay(relayUrlParam: String, peerIdParam: String) {
        this.relayUrl = relayUrlParam
        this.peerId = peerIdParam
        if (isInitialised && audioRelay == null) {
            startAudioRelay()
        }
    }

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

    @JavascriptInterface
    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
        Log.i(TAG, if (muted) "Mic muted" else "Mic unmuted")
    }

    @JavascriptInterface
    fun setSpeakerphone(enabled: Boolean) {
        Log.i(TAG, "Speakerphone ${if (enabled) "on" else "off"} (stub)")
    }

    @JavascriptInterface
    fun endCall() {
        scope.launch {
            statsJob?.cancel()
            audioRelay?.endCall()
            audioTrack?.setEnabled(false)
            audioSource?.dispose()
            localStream?.dispose()
            peerConn?.close()
            peerConn = null; localStream = null; audioSource = null; audioTrack = null
            Log.i(TAG, "Call ended")
        }
    }

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

    private fun startStatsPolling() {
        statsJob = scope.launch {
            while (true) {
                delay(3000)
                peerConn?.getStats { report ->
                    var rtt = -1.0; var codec = "OPUS"; var loss = 0
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
                    val transportName = try { TransportManager.getInstance(ctx).activeTransport.name } catch (e: Exception) { "TOR" }
                    val safe = com.google.gson.Gson().toJson(mapOf(
                        "rtt" to rttMs, "codec" to codec,
                        "packetsLost" to loss, "transport" to transportName
                    ))
                    callJs("window.onRtcStats && window.onRtcStats($safe)")
                }
            }
        }
    }

    private fun mungeAudioSdp(sdp: String): String {
        return sdp.lines()
            .filter { !it.startsWith("m=video") }
            .joinToString("\r\n")
    }

    private fun callJs(js: String) = evalJs(js)
    private fun gsonJson(s: String) = com.google.gson.Gson().toJson(s)
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) { Log.e("SDP", "create: $error") }
    override fun onSetFailure(error: String) { Log.e("SDP", "set: $error") }
}
