package com.nexuschat.app.crypto

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.SecureRandom

/**
 * SmpProtocol — SimpleX Message Protocol v3 frame builder and parser.
 *
 * Reference: https://github.com/simplex-chat/simplexmq/blob/stable/protocol/simplex-messaging.md
 *
 * Frame format (JSON over WebSocket for this implementation):
 * {
 *   "v":     3,                    // protocol version
 *   "corrId": "base64(16B)",       // correlation ID (client-generated)
 *   "queueId": "base64(24B)|null", // recipient/sender queue ID
 *   "cmd":   "NEW|SUB|SEND|ACK|DEL|PING|KEY|NKEY|QKEY",
 *   "body":  { ... },              // command-specific body
 *   "sig":   "base64(64B)|null"    // Ed25519 signature over corrId+queueId+cmd+body
 * }
 *
 * In the real SMP protocol, frames are binary (length-prefixed blocks) with
 * the queue ID acting as an implicit channel selector. This implementation
 * uses JSON for WebSocket transport, matching the JS app.js implementation.
 */
object SmpProtocol {

    private const val TAG     = "NexusChat/SMP"
    private const val VERSION = 3
    private val gson          = Gson()
    private val rng           = SecureRandom()

    // ── Command constants ──────────────────────────────────────────
    object Cmd {
        const val NEW   = "NEW"     // Create recipient queue
        const val SUB   = "SUB"     // Subscribe to queue
        const val SEND  = "SEND"    // Send message to queue
        const val ACK   = "ACK"     // Acknowledge message
        const val DEL   = "DEL"     // Delete queue
        const val PING  = "PING"    // Keep-alive ping
        const val KEY   = "KEY"     // Secure queue with sender key
        const val NKEY  = "NKEY"    // Rotate sender key
        const val QKEY  = "QKEY"    // Queue key response
        const val OK    = "OK"      // Success response
        const val MSG   = "MSG"     // Incoming message notification
        const val ERR   = "ERR"     // Error response
        const val END   = "END"     // Queue end/deleted
        const val INFO  = "INFO"    // Server info response
    }

    // ── Data classes ───────────────────────────────────────────────

    data class SmpFrame(
        @SerializedName("v")       val version:  Int    = VERSION,
        @SerializedName("corrId")  val corrId:   String,
        @SerializedName("queueId") val queueId:  String?,
        @SerializedName("cmd")     val cmd:      String,
        @SerializedName("body")    val body:     Map<String, Any?> = emptyMap(),
        @SerializedName("sig")     val sig:      String? = null
    )

    data class SmpNewBody(
        val recipientKey: String,       // base64 X25519 DH public key
        val sndSecure:    Boolean = true
    )

    data class SmpSendBody(
        val encrypted: String,          // base64 ciphertext (XSalsa20-Poly1305)
        val nonce:     String,          // base64 nonce (24B)
        val ts:        Long = System.currentTimeMillis(),
        val msgId:     String = randomId(16)
    )

    data class SmpMsgBody(
        val msgId:     String,
        val encrypted: String,
        val nonce:     String,
        val ts:        Long
    )

    data class SmpErrBody(
        val error:  String,
        val code:   Int = 0
    )

    // ── Frame builders ─────────────────────────────────────────────

    fun buildNew(recipientDhPubKey: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = null,
        cmd     = Cmd.NEW,
        body    = mapOf(
            "recipientKey" to recipientDhPubKey,
            "sndSecure"    to true
        )
    )

    fun buildSub(queueId: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = queueId,
        cmd     = Cmd.SUB,
        body    = emptyMap()
    )

    fun buildSend(queueId: String, encrypted: String, nonce: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = queueId,
        cmd     = Cmd.SEND,
        body    = mapOf(
            "encrypted" to encrypted,
            "nonce"     to nonce,
            "ts"        to System.currentTimeMillis(),
            "msgId"     to randomId(16)
        )
    )

    fun buildAck(queueId: String, msgId: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = queueId,
        cmd     = Cmd.ACK,
        body    = mapOf("msgId" to msgId)
    )

    fun buildDel(queueId: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = queueId,
        cmd     = Cmd.DEL,
        body    = emptyMap()
    )

    fun buildPing(): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = null,
        cmd     = Cmd.PING,
        body    = mapOf("ts" to System.currentTimeMillis())
    )

    fun buildKey(queueId: String, senderDhPubKey: String): SmpFrame = SmpFrame(
        corrId  = randomId(16),
        queueId = queueId,
        cmd     = Cmd.KEY,
        body    = mapOf("senderKey" to senderDhPubKey)
    )

    // ── Serialise / Deserialise ────────────────────────────────────

    fun toJson(frame: SmpFrame): String = gson.toJson(frame)

    fun fromJson(json: String): SmpFrame? = try {
        gson.fromJson(json, SmpFrame::class.java)
    } catch (e: Exception) {
        Log.e(TAG, "Parse error: ${e.message}")
        null
    }

    /**
     * Sign a frame with Ed25519 private key.
     * Signature covers: version + corrId + queueId + cmd + serialised body.
     *
     * In a real implementation this uses BouncyCastle Ed25519Signer or
     * libsodium via JNI. Here we use BouncyCastle (in dependencies).
     */
    fun signFrame(frame: SmpFrame, privateKeyBase64: String): SmpFrame {
        return try {
            val payload = "${frame.version}${frame.corrId}${frame.queueId}${frame.cmd}${gson.toJson(frame.body)}"
            val privKey = Base64.decode(privateKeyBase64, Base64.NO_WRAP)

            // BouncyCastle Ed25519
            val keyParams = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(privKey, 0)
            val signer    = org.bouncycastle.crypto.signers.Ed25519Signer()
            signer.init(true, keyParams)
            val msgBytes = payload.toByteArray(Charsets.UTF_8)
            signer.update(msgBytes, 0, msgBytes.size)
            val sig = Base64.encodeToString(signer.generateSignature(), Base64.NO_WRAP)
            frame.copy(sig = sig)
        } catch (e: Exception) {
            Log.e(TAG, "Sign failed: ${e.message}")
            frame
        }
    }

    /**
     * Verify Ed25519 signature on a frame.
     */
    fun verifyFrame(frame: SmpFrame, publicKeyBase64: String): Boolean {
        val sig = frame.sig ?: return false
        return try {
            val payload  = "${frame.version}${frame.corrId}${frame.queueId}${frame.cmd}${gson.toJson(frame.body)}"
            val pubKey   = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val sigBytes = Base64.decode(sig, Base64.NO_WRAP)
            val keyParams = org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(pubKey, 0)
            val verifier  = org.bouncycastle.crypto.signers.Ed25519Signer()
            verifier.init(false, keyParams)
            val msgBytes  = payload.toByteArray(Charsets.UTF_8)
            verifier.update(msgBytes, 0, msgBytes.size)
            verifier.verifySignature(sigBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Verify failed: ${e.message}")
            false
        }
    }

    /**
     * Generate a new SMP queue ID (24 bytes, base64url).
     */
    fun newQueueId(): String {
        val bytes = ByteArray(24)
        rng.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Generate a random correlation ID (16 bytes, base64).
     */
    fun randomId(size: Int = 16): String {
        val bytes = ByteArray(size)
        rng.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
