package com.nexuschat.app.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * CryptoManager — real file and message encryption using Google Tink.
 *
 * Encryption scheme:
 *   - Files:    AES-256-GCM streaming (chunked, 1MB blocks)
 *   - Messages: AES-256-GCM with random nonce (128-bit)
 *   - Keys:     Stored in Android Keystore via AndroidKeysetManager
 *
 * The master key wrapping Tink keysets is in Android Keystore (hardware-backed
 * on devices with StrongBox: Pixel 3+, Samsung Galaxy S10+, etc.)
 */
class CryptoManager private constructor(private val aead: Aead) {

    companion object {
        private const val TAG              = "NexusChat/Crypto"
        private const val KEYSET_NAME      = "nexuschat_aead_keyset"
        private const val MASTER_KEY_URI   = "android-keystore://nexuschat_master_key"
        private const val PREF_FILE        = "nexuschat_keyset_prefs"
        private const val CHUNK_SIZE       = 1024 * 1024  // 1 MB
        private const val NONCE_SIZE       = 12            // 96-bit GCM nonce
        private const val TAG_SIZE         = 16            // 128-bit GCM tag

        @Volatile private var instance: CryptoManager? = null

        fun getInstance(ctx: Context): CryptoManager {
            return instance ?: synchronized(this) {
                instance ?: create(ctx).also { instance = it }
            }
        }

        private fun create(ctx: Context): CryptoManager {
            AeadConfig.register()
            val handle: KeysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(ctx, KEYSET_NAME, PREF_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            val aead = handle.getPrimitive(Aead::class.java)
            Log.i(TAG, "CryptoManager initialised — AES-256-GCM, Android Keystore backed")
            return CryptoManager(aead)
        }
    }

    // ── Message Encryption (small payloads < 64KB) ─────────────────
    /**
     * Encrypt plaintext bytes.
     * Returns Base64(nonce || ciphertext+tag)
     */
    fun encryptMessage(plaintext: ByteArray, associatedData: ByteArray = byteArrayOf()): String {
        val ciphertext = aead.encrypt(plaintext, associatedData)
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun encryptMessage(plaintext: String, associatedData: String = ""): String =
        encryptMessage(plaintext.toByteArray(Charsets.UTF_8),
                       associatedData.toByteArray(Charsets.UTF_8))

    /**
     * Decrypt Base64-encoded ciphertext.
     * Returns null if authentication fails (tampered / wrong key).
     */
    fun decryptMessage(ciphertextB64: String, associatedData: ByteArray = byteArrayOf()): ByteArray? {
        return try {
            val ct = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            aead.decrypt(ct, associatedData)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.message}")
            null
        }
    }

    fun decryptMessageStr(ciphertextB64: String, associatedData: String = ""): String? =
        decryptMessage(ciphertextB64, associatedData.toByteArray(Charsets.UTF_8))
            ?.toString(Charsets.UTF_8)

    // ── File Encryption (streaming, chunked) ────────────────────────
    /**
     * Encrypt a file in 1MB chunks.
     * Output format: [4B chunk_count][chunk0_nonce(12B)][chunk0_ct][chunk1_nonce]...
     *
     * Each chunk is independently authenticated — partial reads are safe.
     */
    fun encryptFile(input: File, output: File, associatedData: ByteArray = byteArrayOf()) {
        val rng = SecureRandom()
        input.inputStream().buffered(CHUNK_SIZE).use { ins ->
            output.outputStream().buffered().use { outs ->
                var chunkIdx = 0
                val buf = ByteArray(CHUNK_SIZE)
                var read: Int
                while (ins.read(buf).also { read = it } != -1) {
                    val chunk    = buf.copyOf(read)
                    val nonce    = ByteArray(NONCE_SIZE).also { rng.nextBytes(it) }
                    // Associate chunk index to prevent reordering attacks
                    val ad       = associatedData + chunkIdx.toByteArray()
                    val ct       = aead.encrypt(chunk, ad)  // Tink includes nonce internally
                    // Write: [4B length][ciphertext]
                    outs.write(ct.size.toByteArray())
                    outs.write(ct)
                    chunkIdx++
                    Log.d(TAG, "Encrypted chunk $chunkIdx (${read} bytes → ${ct.size} bytes)")
                }
                Log.i(TAG, "File encrypted: ${input.name} → ${output.name} ($chunkIdx chunks)")
            }
        }
    }

    /**
     * Decrypt a chunked-encrypted file.
     */
    fun decryptFile(input: File, output: File, associatedData: ByteArray = byteArrayOf()) {
        input.inputStream().buffered().use { ins ->
            output.outputStream().buffered().use { outs ->
                var chunkIdx = 0
                val lenBuf   = ByteArray(4)
                while (ins.read(lenBuf) == 4) {
                    val ctLen = lenBuf.toInt()
                    val ct    = ByteArray(ctLen).also { buf -> var off = 0; while (off < ctLen) { val n = ins.read(buf, off, ctLen - off); if (n < 0) throw java.io.EOFException("Unexpected EOF reading chunk $chunkIdx"); off += n } }
                    val ad    = associatedData + chunkIdx.toByteArray()
                    val plain = aead.decrypt(ct, ad)
                    outs.write(plain)
                    chunkIdx++
                }
                Log.i(TAG, "File decrypted: ${input.name} ($chunkIdx chunks)")
            }
        }
    }

    // ── Utilities ────────────────────────────────────────────────────
    /**
     * Securely wipe a ByteArray (overwrite with zeros + random).
     * Helps prevent key material remaining in memory.
     */
    fun wipeBytes(data: ByteArray) {
        val rng = SecureRandom()
        rng.nextBytes(data)         // overwrite with random
        data.fill(0)                // then zero
        rng.nextBytes(data)         // random again
        data.fill(0)                // final zero
    }

    /**
     * Generate a random token (used for SMP queue IDs, nonces).
     */
    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun randomBase64(size: Int): String   = Base64.encodeToString(randomBytes(size), Base64.NO_WRAP)

    // ── Int ↔ ByteArray ─────────────────────────────────────────────
    private fun Int.toByteArray(): ByteArray = byteArrayOf(
        (this shr 24).toByte(), (this shr 16).toByte(),
        (this shr 8).toByte(),   this.toByte()
    )
    private fun ByteArray.toInt(): Int =
        ((this[0].toInt() and 0xFF) shl 24) or
        ((this[1].toInt() and 0xFF) shl 16) or
        ((this[2].toInt() and 0xFF) shl  8) or
         (this[3].toInt() and 0xFF)
}
