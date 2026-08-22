/**
 * A custom DCT-based audio compression codec for voice messages. Uses forward DCT-II on
 * 320-sample PCM frames, retaining the first 80 frequency coefficients. The 'Opus' in the
 * wire format refers to this custom codec, not the standard Opus codec (RFC 6716).
 *
 * ## Wire format
 * - 4 bytes: magic header `OPUS` (0x4F 0x50 0x55 0x53)
 * - 1 byte: format version
 * - N × (numCoeffs × 2) bytes: little-endian 16-bit DCT coefficients per frame
 *
 * Used internally by [VoiceMessageManager] for voice message compression.
 */
package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.PI

/**
 * Custom audio compressor/decompressor that applies a DCT-based transform to PCM data.
 * The compression discards high-frequency coefficients to reduce data size.
 */
class OpusEncoder {

    /**
     * Compresses raw 16-bit PCM data using a forward DCT.
     * The input is split into 320-sample frames; each frame is transformed to 80 DCT
     * coefficients and serialised in little-endian format.
     * @param pcmData raw PCM byte array (16-bit, little-endian, mono).
     * @return compressed byte array with header and coefficient data.
     */
    fun compressPcm(pcmData: ByteArray): ByteArray {
        val samples = ShortArray(pcmData.size / 2)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        val frameSize = 320
        val numFrames = (samples.size + frameSize - 1) / frameSize

        val baos = ByteArrayOutputStream()
        baos.write(OPUS_HEADER)
        baos.write(0x01)

        for (f in 0 until numFrames) {
            val start = f * frameSize
            val len = minOf(frameSize, samples.size - start)
            val frame = ShortArray(frameSize)
            System.arraycopy(samples, start, frame, 0, len)

            val numCoeffs = frameSize / 4
            val coeffs = ShortArray(numCoeffs)
            for (k in 0 until numCoeffs) {
                var sum = 0.0
                for (n in 0 until frameSize) {
                    sum += frame[n] * cos(PI / frameSize * (n + 0.5) * k)
                }
                val scaled = (sum / frameSize).toLong()
                coeffs[k] = scaled.coerceIn(
                    Short.MIN_VALUE.toLong(),
                    Short.MAX_VALUE.toLong()
                ).toShort()
            }

            val buf = ByteArray(numCoeffs * 2)
            ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(coeffs)
            baos.write(buf)
        }

        return baos.toByteArray()
    }

    /**
     * Decompresses data previously compressed by [compressPcm] back to PCM audio.
     * Applies the inverse DCT on each frame and reconstructs the waveform.
     * @param opusData compressed data with OPUS header.
     * @param originalLength expected length of the original PCM data in bytes.
     * @return reconstructed PCM byte array.
     */
    fun decompressOpus(opusData: ByteArray, originalLength: Int): ByteArray {
        if (opusData.size < 5) return ByteArray(0)
        var pos = 0
        val header = byteArrayOf(opusData[0], opusData[1], opusData[2], opusData[3])
        pos += 4
        val format = opusData[pos++].toInt() and 0xFF

        val frameSize = 320
        val numCoeffs = frameSize / 4
        val coeffBytesPerFrame = numCoeffs * 2

        val dataSize = opusData.size - pos
        val numFrames = dataSize / coeffBytesPerFrame
        if (numFrames == 0) return ByteArray(0)

        val totalSamples = numFrames * frameSize
        val result = ShortArray(totalSamples)

        for (f in 0 until numFrames) {
            val coeffs = ShortArray(numCoeffs)
            ByteBuffer.wrap(opusData, pos, coeffBytesPerFrame)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()
                .get(coeffs)
            pos += coeffBytesPerFrame

            for (n in 0 until frameSize) {
                var s = coeffs[0].toDouble()
                for (k in 1 until numCoeffs) {
                    s += 2.0 * coeffs[k] * cos(PI / frameSize * (n + 0.5) * k)
                }
                result[f * frameSize + n] = s.toLong().coerceIn(
                    Short.MIN_VALUE.toLong(),
                    Short.MAX_VALUE.toLong()
                ).toShort()
            }
        }

        val outLen = minOf(originalLength, result.size * 2)
        val out = ByteArray(outLen)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(result, 0, outLen / 2)
        return out
    }

    companion object {
        /** Magic bytes that identify a file compressed by this encoder ("OPUS" in ASCII). */
        val OPUS_HEADER = byteArrayOf(0x4F, 0x50, 0x55, 0x53)
    }
}
