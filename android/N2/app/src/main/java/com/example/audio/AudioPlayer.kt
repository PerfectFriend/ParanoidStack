package com.example.audio

import android.util.Log

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0f)
    val position: StateFlow<Float> = _position.asStateFlow()

    fun play(pcmData: ByteArray, sampleRate: Int = 16000): Boolean {
        stop()

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(pcmData.size)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.let { track ->
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                audioTrack = null
                return false
            }
            track.write(pcmData, 0, pcmData.size)
            _isPlaying.value = true
            _position.value = 0f

            playJob = scope.launch {
                track.play()
                val durationMs = (pcmData.size.toLong() * 1000) / (sampleRate * 2)
                val startTime = System.currentTimeMillis()
                while (isActive && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val elapsed = System.currentTimeMillis() - startTime
                    _position.value = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    if (elapsed >= durationMs) break
                    delay(50)
                }
                _position.value = 1f
                _isPlaying.value = false
                try { track.stop() } catch (_: java.lang.Exception) { Log.w("AudioPlayer", "ignored exception") }
                track.release()
                audioTrack = null
            }
            return true
        }
        return false
    }

    fun stop() {
        playJob?.cancel()
        try { audioTrack?.stop() } catch (_: java.lang.Exception) { Log.w("AudioPlayer", "ignored exception") }
        try { audioTrack?.release() } catch (_: java.lang.Exception) { Log.w("AudioPlayer", "ignored exception") }
        audioTrack = null
        _isPlaying.value = false
        _position.value = 0f
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
