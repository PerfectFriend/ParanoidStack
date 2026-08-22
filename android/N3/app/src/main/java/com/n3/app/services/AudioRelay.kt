package com.n3.app.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

class AudioRelay private constructor(
    private val ctx: Context,
    private val transportManager: TransportManager
) {
    companion object {
        private const val TAG = "NexusChat/AudioRelay"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 4096
        private const val PACKET_INTERVAL_MS = 20L
        private const val OPUS_FRAME_SIZE = 960
        @Volatile private var instance: AudioRelay? = null
        fun getInstance(ctx: Context, transport: TransportManager): AudioRelay =
            instance ?: synchronized(this) {
                instance ?: AudioRelay(ctx.applicationContext, transport).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val _isCallActive = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var relayWebSocket: WebSocket? = null
    private var audioManager: AudioManager? = null

    var onAudioPacketSent: ((ByteArray) -> Unit)? = null
    var onAudioPacketReceived: ((ByteArray) -> Unit)? = null

    val isCallActive: Boolean get() = _isCallActive.get()

    fun startCall(relayUrl: String, peerId: String): Boolean {
        if (!hasAudioPermission()) {
            Log.e(TAG, "No audio permission")
            return false
        }
        _isCallActive.set(true)
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = false
        scope.launch {
            try {
                initAudioRecord()
                initAudioTrack()
                connectRelay(relayUrl, peerId)
                startCapture()
                Log.i(TAG, "Audio relay started: $relayUrl peer=$peerId")
            } catch (e: Exception) {
                Log.e(TAG, "Audio relay start failed: ${e.message}")
                _isCallActive.set(false)
            }
        }
        return true
    }

    fun endCall() {
        _isCallActive.set(false)
        captureJob?.cancel()
        playbackJob?.cancel()
        relayWebSocket?.close(1000, "Call ended")
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        audioManager?.mode = AudioManager.MODE_NORMAL
        audioRecord = null
        audioTrack = null
        Log.i(TAG, "Audio relay stopped")
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun initAudioRecord() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBuffer, BUFFER_SIZE)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw Exception("AudioRecord init failed")
        }
        audioRecord?.startRecording()
        Log.i(TAG, "AudioRecord started: ${SAMPLE_RATE}Hz mono PCM-16")
    }

    private fun initAudioTrack() {
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT)
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT,
            maxOf(minBuffer, BUFFER_SIZE * 4),
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
        Log.i(TAG, "AudioTrack started")
    }

    private fun connectRelay(relayUrl: String, peerId: String) {
        val (client, transportType) = transportManager.getClient()
        val wsUrl = relayUrl.replace("http://", "ws://").replace("https://", "wss://")
        val request = Request.Builder()
            .url("$wsUrl/audio?peer=$peerId")
            .addHeader("X-Transport", transportType.name)
            .build()
        relayWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Audio relay WS open: $wsUrl")
            }
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (_isCallActive.get()) {
                    playAudioPacket(bytes.toByteArray())
                    onAudioPacketReceived?.invoke(bytes.toByteArray())
                }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Audio relay WS failed: ${t.message}")
                if (_isCallActive.get()) {
                    scope.launch {
                        delay(3000)
                        connectRelay(relayUrl, peerId)
                    }
                }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Audio relay WS closed: $code $reason")
                if (_isCallActive.get() && code != 1000) {
                    scope.launch {
                        delay(5000)
                        connectRelay(relayUrl, peerId)
                    }
                }
            }
        })
    }

    private fun startCapture() {
        captureJob = scope.launch {
            val buf = ByteArray(BUFFER_SIZE)
            var seqNum = 0
            while (_isCallActive.get()) {
                val frameSize = OPUS_FRAME_SIZE * 2
                val frameBuf = ByteArray(frameSize)
                var totalRead = 0
                while (totalRead < frameSize) {
                    val read = audioRecord?.read(frameBuf, totalRead, frameSize - totalRead) ?: -1
                    if (read < 0) break
                    totalRead += read
                }
                if (totalRead > 0) {
                    val packet = buildAudioPacket(frameBuf.copyOf(totalRead), seqNum++)
                    relayWebSocket?.send(Base64.encodeToString(packet, Base64.NO_WRAP))
                    transportManager.recordBytesSent(
                        transportManager.activeTransport,
                        packet.size.toLong()
                    )
                    onAudioPacketSent?.invoke(packet)
                }
                delay(PACKET_INTERVAL_MS)
            }
        }
    }

    private fun playAudioPacket(data: ByteArray) {
        try {
            val decoded = if (data.size > 2 && data[0] == 'N'.toByte() && data[1] == 'C'.toByte()) {
                Base64.decode(data.copyOfRange(4, data.size), Base64.NO_WRAP)
            } else Base64.decode(data, Base64.NO_WRAP)
            audioTrack?.write(decoded, 0, decoded.size)
        } catch (e: Exception) {
            Log.w(TAG, "Audio playback error: ${e.message}")
        }
    }

    private fun buildAudioPacket(pcmData: ByteArray, seqNum: Int): ByteArray {
        val header = ByteArray(4)
        header[0] = 'N'.toByte()
        header[1] = 'C'.toByte()
        header[2] = (seqNum shr 8).toByte()
        header[3] = seqNum.toByte()
        val paddingLen = rng.nextInt(32)
        val padding = ByteArray(paddingLen)
        rng.nextBytes(padding)
        return header + pcmData + padding
    }

    fun destroy() {
        endCall()
        scope.cancel()
        instance = null
    }
}
