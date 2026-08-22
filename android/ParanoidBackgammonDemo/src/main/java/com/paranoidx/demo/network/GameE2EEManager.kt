package com.paranoidx.demo.network

import com.paranoidx.sdk.protocol.SmpCbor
import com.paranoidx.sdk.security.DoubleRatchet
import java.security.SecureRandom

/**
 * E2E encryption wrapper for backgammon P2P moves.
 * Uses DoubleRatchet from PX SDK for AES-256-GCM ratchet encryption.
 */
class GameE2EEManager(sharedSecret: ByteArray) {
    private val ratchet = DoubleRatchet(sharedSecret)
    private val random = SecureRandom()

    /** Encrypt a move (from, to, diceUsed) into a secure CBOR-ratchet payload. */
    fun encryptMove(from: Int, to: Int, diceUsed: Int): ByteArray {
        val payload = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeInt(from),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeInt(to),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeInt(diceUsed),
            SmpCbor.encodeIntKey(3) to SmpCbor.encodeInt(ratchet.messageIndex)
        ))
        val plaintext = java.util.Base64.getEncoder().encodeToString(payload)
        val (ciphertext, nonce, senderKey) = ratchet.ratchetSend(plaintext)
        // Package: nonce(12) + senderKey(var) + ciphertext
        return encodeMessage(nonce, senderKey, ciphertext)
    }

    /** Decrypt a move from ratchet-encrypted bytes. */
    fun decryptMove(encrypted: ByteArray): Triple<Int, Int, Int>? {
        try {
            val (nonce, senderKey, ciphertext) = decodeMessage(encrypted)
            val plaintext = ratchet.ratchetReceive(ciphertext, nonce, senderKey)
            val decoded = java.util.Base64.getDecoder().decode(plaintext)
            val (cbor, _) = SmpCbor.decode(decoded)
            val pairs = cbor.asMap()
            val from = (pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? SmpCbor.CborValue)?.asInt() ?: return null
            val to = (pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? SmpCbor.CborValue)?.asInt() ?: return null
            val dice = (pairs.firstOrNull { (k, _) -> (k as? Int) == 2 }?.second as? SmpCbor.CborValue)?.asInt() ?: return null
            return Triple(from, to, dice)
        } catch (_: Exception) { return null }
    }

    /** Wire format: [2 bytes nonceLen][nonce][2 bytes senderKeyLen][senderKey][ciphertext] */
    private fun encodeMessage(nonce: ByteArray, senderKey: ByteArray, ciphertext: String): ByteArray {
        val ctBytes = ciphertext.toByteArray(Charsets.UTF_8)
        val buf = java.nio.ByteBuffer.allocate(2 + nonce.size + 2 + senderKey.size + ctBytes.size)
        buf.putShort(nonce.size.toShort())
        buf.put(nonce)
        buf.putShort(senderKey.size.toShort())
        buf.put(senderKey)
        buf.put(ctBytes)
        return buf.array()
    }

    private fun decodeMessage(data: ByteArray): Triple<ByteArray, ByteArray, String> {
        val buf = java.nio.ByteBuffer.wrap(data)
        val nonceLen = buf.short.toInt()
        val nonce = ByteArray(nonceLen); buf.get(nonce)
        val keyLen = buf.short.toInt()
        val key = ByteArray(keyLen); buf.get(key)
        val ctBytes = ByteArray(buf.remaining()); buf.get(ctBytes)
        return Triple(nonce, key, String(ctBytes, Charsets.UTF_8))
    }

    companion object {
        /** Generate a random shared secret for Double Ratchet init. */
        fun generateSharedSecret(): ByteArray {
            val secret = ByteArray(32)
            SecureRandom().nextBytes(secret)
            return secret
        }
    }
}
