/**
 * Records audio from the device microphone into a PCM byte array.
 *
 * Uses [AudioRecord] with a separate background thread to capture chunks of 4096 bytes.
 * Supports a single recording session at a time; concurrent calls to [startRecording]
 * are rejected.
 *
 * ## Configuration
 * - Sample rate: 16 kHz ([SAMPLE_RATE])
 * - Channel: mono ([CHANNEL_CONFIG])
 * - Encoding: 16-bit PCM ([AUDIO_FORMAT])
 */
package com.example.audio

import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Wraps Android's [AudioRecord] to capture microphone input into a contiguous PCM byte array.
 * Recording runs on a dedicated thread and is collected in a synchronized buffer list.
 */
class VoiceRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val pcmData = mutableListOf<ByteArray>()

    @Volatile
    var isRecording = false
        private set

    /**
     * Starts capturing audio from the microphone.
     * Spawns a background thread that continuously reads from [AudioRecord] into a buffer list.
     * @param sampleRate desired sample rate in Hz (default 16000).
     * @return true if recording started successfully, false if already recording or initialisation failed.
     */
    fun startRecording(sampleRate: Int = SAMPLE_RATE): Boolean {
        if (isRecording) return false
        val bufferSize = 4096
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return false
        }
        isRecording = true
        pcmData.clear()
        audioRecord?.startRecording()
        recordThread = thread {
            val buffer = ByteArray(4096)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    synchronized(pcmData) {
                        pcmData.add(buffer.copyOf(bytesRead))
                    }
                }
            }
        }
        return true
    }

    /**
     * Stops recording and concatenates all buffered PCM chunks into a single byte array.
     * Waits up to 2 seconds for the recording thread to join before releasing resources.
     * @return the complete PCM data recorded during this session.
     */
    fun stopRecording(): ByteArray {
        isRecording = false
        recordThread?.join(2000)
        audioRecord?.apply {
            try { stop() } catch (_: java.lang.Exception) { Log.w("VoiceRecorder", "ignored exception") }
            release()
        }
        audioRecord = null
        synchronized(pcmData) {
            val totalSize = pcmData.sumOf { it.size }
            val result = ByteArray(totalSize)
            var pos = 0
            for (chunk in pcmData) {
                System.arraycopy(chunk, 0, result, pos, chunk.size)
                pos += chunk.size
            }
            pcmData.clear()
            return result
        }
    }

    companion object {
        /** Default sample rate: 16 kHz. */
        const val SAMPLE_RATE = 16000
        /** Recording channel configuration: mono. */
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        /** Audio encoding format: 16-bit PCM. */
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
