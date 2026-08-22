/**
 * Manages the full lifecycle of voice messages: recording, compression (Opus-like),
 * encryption, XFTP upload, and the reverse (download, decryption, decompression, playback).
 *
 * ## Flow: record & send
 * 1. Capture PCM audio via [VoiceRecorder].
 * 2. Compress with [OpusEncoder].
 * 3. Encrypt with [NaClCrypto.cryptoBoxAfterNm] using the recipient's key.
 * 4. Compute SHA-256 digest of the encrypted payload.
 * 5. Register and upload the chunk via [XFTPClient].
 *
 * ## Flow: receive & play
 * 1. Download the encrypted chunk from XFTP.
 * 2. Decrypt with [NaClCrypto.cryptoBoxOpenAfterNm].
 * 3. Decompress via [OpusEncoder.decompressOpus] to PCM.
 */
package com.example.audio

import com.example.data.NaClCrypto
import com.example.data.XFTPClient
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * High-level controller for voice message recording, encryption, upload, download, and playback.
 * Wraps [VoiceRecorder], [OpusEncoder], and XFTP networking into a simple send/receive API.
 */
class VoiceMessageManager {
    private val voiceRecorder = VoiceRecorder()
    private val opusEncoder = OpusEncoder()
    private val secureRandom = SecureRandom()

    val isRecording: Boolean get() = voiceRecorder.isRecording

    /**
     * Records up to [MAX_RECORDING_DURATION_MS] of audio, compresses, encrypts,
     * and uploads the result via XFTP. Should be called from a background thread.
     * @param xftpClient initialised XFTP client for chunk registration and upload.
     * @param recipientKey recipient's public key for NaCl encryption.
     * @return true if the upload was successful, false on failure or invalid data.
     */
    fun recordAndSend(xftpClient: XFTPClient, recipientKey: ByteArray): Boolean {
        if (!voiceRecorder.startRecording()) return false

        val maxDurationMs = MAX_RECORDING_DURATION_MS
        val startTime = System.currentTimeMillis()
        while (voiceRecorder.isRecording &&
            System.currentTimeMillis() - startTime < maxDurationMs
        ) {
            Thread.sleep(100)
        }

        val pcmData = voiceRecorder.stopRecording()
        if (pcmData.isEmpty() || pcmData.size > MAX_FILE_SIZE_BYTES) return false

        val compressed = opusEncoder.compressPcm(pcmData)
        if (compressed.size > MAX_FILE_SIZE_BYTES) return false

        val nonce = ByteArray(24)
        secureRandom.nextBytes(nonce)
        val encrypted = NaClCrypto.cryptoBoxAfterNm(compressed, nonce, recipientKey)

        val payload = nonce + encrypted

        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val sndKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val rcvKeys = listOf(ByteArray(32).also { secureRandom.nextBytes(it) })

        val result = xftpClient.registerChunk(sndKey, rcvKeys, payload.size, digest)
            ?: return false

        return xftpClient.uploadChunk(result.senderId, payload)
    }

    /**
     * Downloads, decrypts, and decompresses a voice message chunk from XFTP.
     * @param xftpClient initialised XFTP client for download.
     * @param chunkId identifier of the remote chunk to fetch.
     * @param decryptionKey symmetric (or recipient's) key for NaCl decryption.
     * @return decompressed PCM byte array, or an empty array on failure.
     */
    fun receiveAndPlay(
        xftpClient: XFTPClient,
        chunkId: ByteArray,
        decryptionKey: ByteArray
    ): ByteArray {
        val downloaded = xftpClient.downloadChunk(chunkId, ByteArray(0))
            ?: return ByteArray(0)

        if (downloaded.size < 24) return ByteArray(0)

        val nonce = downloaded.copyOfRange(0, 24)
        val encrypted = downloaded.copyOfRange(24, downloaded.size)

        val compressed: ByteArray
        try {
            compressed = NaClCrypto.cryptoBoxOpenAfterNm(encrypted, nonce, decryptionKey)
        } catch (e: Exception) {
            return ByteArray(0)
        }

        val originalLength = (compressed.size - 5) / (320 / 4 * 2) * 320 * 2
        return opusEncoder.decompressOpus(compressed, originalLength)
    }

    /** Stops the ongoing recording and returns any captured PCM data. */
    fun stopRecording(): ByteArray {
        return voiceRecorder.stopRecording()
    }

    companion object {
        /** Maximum duration of a single voice recording: 60 seconds. */
        const val MAX_RECORDING_DURATION_MS = 60_000L
        /** Maximum allowed file size for voice messages: 5 MB. */
        const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024
    }
}
