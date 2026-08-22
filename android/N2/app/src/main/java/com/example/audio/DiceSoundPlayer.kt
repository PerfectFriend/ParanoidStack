/**
 * Synthesises sound effects for dice rolls and checker moves using procedural audio generation.
 *
 * ## Dice roll sound
 * A ~1.15-second sound composed of:
 * 1. A rumbling noise phase (~0.5 s) with resonant shake impacts.
 * 2. Two distinct "dice landing" phases with wood-resonance and plastic-resonance layers.
 *
 * ## Checker move sound
 * A ~0.32-second sound with a short slide noise and two tap impacts.
 *
 * All sounds are generated at runtime using [AudioTrack] in STATIC mode – no audio files needed.
 */
package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural audio engine that generates dice-roll and checker-move sound effects
 * in real time using mathematical synthesis and Android's [AudioTrack] API.
 */
class DiceSoundPlayer {
    private var audioTrack: AudioTrack? = null

    /**
     * Plays a short PCM buffer through [AudioTrack] in STATIC mode.
     * Creates a new [AudioTrack] instance, writes the samples, plays, waits for duration,
     * then releases the track.
     * @param buffer PCM 16-bit sample buffer.
     * @param sampleRate sample rate in Hz.
     * @param numSamples number of samples in the buffer.
     */
    @Suppress("DEPRECATION")
    private suspend fun playSoundBuffer(buffer: ShortArray, sampleRate: Int, numSamples: Int) = withContext(Dispatchers.IO) {
        try {
            audioTrack?.let {
                try {
                    if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        it.stop()
                    }
                } catch (e: Exception) {
                    Log.w("DiceSoundPlayer", "error: ${e.message}")
                }
                it.release()
            }

            val bufferSizeInBytes = numSamples * 2
            
            val currentTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            } else {
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes,
                    AudioTrack.MODE_STATIC
                )
            }

            audioTrack = currentTrack

            try {
                // In STATIC mode, write MUST be called BEFORE play()
                currentTrack.write(buffer, 0, numSamples)
                currentTrack.play()

                // Let the sound play for its duration, then stop/release the track
                val durationMs = (numSamples * 1000L / sampleRate) + 120
                kotlinx.coroutines.delay(durationMs)
            } finally {
                if (audioTrack == currentTrack) {
                    try {
                        if (currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            currentTrack.stop()
                        }
                    } catch (e: Exception) {
                        Log.w("DiceSoundPlayer", "error: ${e.message}")
                    }
                    currentTrack.release()
                    audioTrack = null
                }
            }
        } catch (e: Exception) {
            Log.e("DiceSoundPlayer", "exception", e)
        }
    }

    /**
     * Generates and plays a dice roll sound effect (~1.15 seconds).
     * Synthesis layers: rumble noise → shake impacts → two dice landing with wood/plastic resonance.
     */
    suspend fun playRollSound() {
        val sampleRate = 22050
        val duration = 1.15 // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        val random = java.security.SecureRandom()
        
        val shakeTimes = doubleArrayOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.46)
        val shakeAmps = doubleArrayOf(0.20, 0.28, 0.22, 0.30, 0.20, 0.24)
        
        val d1Times = doubleArrayOf(0.52, 0.61, 0.68)
        val d1Amps = doubleArrayOf(0.68, 0.32, 0.15)
        val d1Pitches = doubleArrayOf(1350.0, 1420.0, 1380.0)
        
        val d2Times = doubleArrayOf(0.64, 0.74, 0.81)
        val d2Amps = doubleArrayOf(0.72, 0.35, 0.16)
        val d2Pitches = doubleArrayOf(1150.0, 1220.0, 1180.0)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            
            if (t < 0.50) {
                val rumbleNoise = random.nextGaussian()
                val rumbleSine = sin(2.0 * Math.PI * 180.0 * t)
                val rumbleEnv = 0.06 * (0.4 + 0.6 * sin(2.0 * Math.PI * 16.0 * t))
                sample += rumbleNoise * rumbleEnv * 0.5 + rumbleSine * rumbleEnv * 0.5
                
                for (s in shakeTimes.indices) {
                    val st = shakeTimes[s]
                    if (t >= st) {
                        val dt = t - st
                        val decay = exp(-dt * 150.0)
                        if (decay > 0.001) {
                            val resonance = sin(2.0 * Math.PI * 320.0 * dt) * 0.4 + sin(2.0 * Math.PI * 2600.0 * dt) * 0.4
                            val noise = random.nextGaussian() * 0.2
                            sample += (resonance + noise) * decay * shakeAmps[s]
                        }
                    }
                }
            }
            
            for (j in d1Times.indices) {
                val ct = d1Times[j]
                if (t >= ct) {
                    val dt = t - ct
                    val woodDecay = exp(-dt * 130.0)
                    val plasticDecay = exp(-dt * 260.0)
                    val noiseDecay = exp(-dt * 450.0)
                    
                    if (woodDecay > 0.001) {
                        val woodRes = sin(2.0 * Math.PI * 240.0 * dt) * 0.25 * woodDecay
                        val plasticRes = sin(2.0 * Math.PI * d1Pitches[j] * dt) * 0.45 * plasticDecay
                        val click = random.nextGaussian() * 0.30 * noiseDecay
                        sample += (woodRes + plasticRes + click) * d1Amps[j]
                    }
                }
            }
            
            for (j in d2Times.indices) {
                val ct = d2Times[j]
                if (t >= ct) {
                    val dt = t - ct
                    val woodDecay = exp(-dt * 120.0)
                    val plasticDecay = exp(-dt * 250.0)
                    val noiseDecay = exp(-dt * 420.0)
                    
                    if (woodDecay > 0.001) {
                        val woodRes = sin(2.0 * Math.PI * 200.0 * dt) * 0.25 * woodDecay
                        val plasticRes = sin(2.0 * Math.PI * d2Pitches[j] * dt) * 0.45 * plasticDecay
                        val click = random.nextGaussian() * 0.30 * noiseDecay
                        sample += (woodRes + plasticRes + click) * d2Amps[j]
                    }
                }
            }

            val normalized = when {
                sample > 0.99 -> 0.99
                sample < -0.99 -> -0.99
                else -> sample
            }
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }

        playSoundBuffer(buffer, sampleRate, numSamples)
    }

    /**
     * Generates and plays a checker move sound effect (~0.32 seconds).
     * Synthesis layers: a short slide/whoosh followed by two distinct tap impacts.
     */
    suspend fun playMoveSound() {
        val sampleRate = 22050
        val duration = 0.32 // seconds
        val numSamples = (sampleRate * duration).toInt()
        val buffer = ShortArray(numSamples)
        val random = java.security.SecureRandom()
 
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            
            if (t < 0.12) {
                val noise = random.nextGaussian()
                val slideEnv = 0.08 * sin(Math.PI * (t / 0.12))
                val wave = sin(2.0 * Math.PI * 240.0 * t)
                sample += (noise * 0.65 + wave * 0.35) * slideEnv
            }

            val tap1Time = 0.11
            if (t >= tap1Time) {
                val dt = t - tap1Time
                val woodDecay = exp(-dt * 150.0)
                val resinDecay = exp(-dt * 260.0)
                
                if (woodDecay > 0.001) {
                    val woodenBody = sin(2.0 * Math.PI * 260.0 * dt) * 0.42 * woodDecay
                    val resinClack = sin(2.0 * Math.PI * 1350.0 * dt) * 0.38 * resinDecay
                    val clickNoise = random.nextGaussian() * 0.15 * exp(-dt * 450.0)
                    sample += (woodenBody + resinClack + clickNoise) * 0.65
                }
            }

            val tap2Time = 0.145
            if (t >= tap2Time) {
                val dt = t - tap2Time
                val resinDecay = exp(-dt * 300.0)
                
                if (resinDecay > 0.001) {
                    val resinClack = sin(2.0 * Math.PI * 1580.0 * dt) * 0.55 * resinDecay
                    val clickNoise = random.nextGaussian() * 0.20 * exp(-dt * 500.0)
                    sample += (resinClack + clickNoise) * 0.35
                }
            }

            val normalized = when {
                sample > 0.99 -> 0.99
                sample < -0.99 -> -0.99
                else -> sample
            }
            buffer[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
        }

        playSoundBuffer(buffer, sampleRate, numSamples)
    }

    /** Releases the current [AudioTrack] instance if one exists. */
    fun release() {
        try {
            audioTrack?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.w("DiceSoundPlayer", "error: ${e.message}")
        }
    }
}
