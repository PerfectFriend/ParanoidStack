/**
 * Упрощённый криптографический слой для приложения SimpleX.
 * Предоставляет: E2EE (сквозное шифрование) через X25519 + AES-GCM,
 * интеграцию с Double Ratchet, вывод ключей (PBKDF2, HKDF),
 * шифрование хранилища и генерацию ключевых пар.
 */
package com.example.data

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Утилитарный объект для криптографических операций */
object SimpleXCrypto {
    private const val TAG = "SimpleXCrypto"
    private const val GCM_TAG_LENGTH = 128       // длина тега аутентификации GCM
    private const val GCM_IV_LENGTH = 12          // длина IV для GCM
    private const val KEY_EXCHANGE_ALGO = "X25519"
    private const val KEY_SIZE = 256
    private const val HKDF_SALT = "SimpleX-E2EE-v1"

    /** Ключевая пара для E2EE с методом toBase64 */
    data class E2EEKeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        fun publicKeyBase64(): String = Base64.encodeToString(publicKey, Base64.NO_WRAP)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is E2EEKeyPair) return false
            return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
        }
        override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
    }

    /** Сгенерировать X25519 ключевую пару */
    fun generateKeyPair(): E2EEKeyPair {
        val kpg = KeyPairGenerator.getInstance("XDH")
        kpg.initialize(java.security.spec.NamedParameterSpec("X25519"))
        val kp = kpg.generateKeyPair()
        return E2EEKeyPair(
            publicKey = kp.public.encoded,
            privateKey = kp.private.encoded
        )
    }

    /** Вычислить общий секрет X25519 (DH) */
    fun computeSharedSecret(myPrivate: ByteArray, peerPublic: ByteArray): ByteArray {
        val kf = KeyFactory.getInstance("XDH")
        val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(myPrivate))
        val pubKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(peerPublic))
        val ka = KeyAgreement.getInstance("XDH")
        ka.init(privKey)
        ka.doPhase(pubKey, true)
        return ka.generateSecret()
    }

    /**
     * Вывод ключа через HKDF-SHA256 (RFC 5869).
     *
     * HKDF consists of two phases:
     *   1. Extract: PRK = HMAC-SHA256(salt, inputKey) — condenses the input entropy
     *   2. Expand: iteratively generates output blocks by feeding PRK + previous block + info + counter
     *      through HMAC-SHA256. This produces arbitrary-length derived key material.
     * Uses proper HMAC-SHA256 for both Extract and Expand phases per RFC 5869.
     */
    fun hkdfDerive(inputKey: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // HKDF-Extract: PRK = HMAC-SHA256(salt, inputKey)  (RFC 5869)
        val prk = javax.crypto.Mac.getInstance("HmacSHA256").let { mac ->
            mac.init(SecretKeySpec(salt, "HmacSHA256"))
            mac.doFinal(inputKey)
        }
        // HKDF-Expand: T(i) = HMAC(PRK, T(i-1) || info || i)
        val result = ByteArray(length)
        var previous = ByteArray(0)
        val hashLen = 32
        var remaining = length
        var counter = 1
        while (remaining > 0) {
            hmacSha256(prk, previous + info + counter.toByte()) { block ->
                val take = minOf(block.size, remaining)
                System.arraycopy(block, 0, result, length - remaining, take)
                previous = block
                remaining -= take
                counter++
            }
        }
        return result
    }

    /** Вычисление HMAC-SHA256 */
    private fun hmacSha256(key: ByteArray, data: ByteArray, block: (ByteArray) -> Unit) {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        block(mac.doFinal(data))
    }

    /** Зашифровать данные AES-256-GCM (IV + ciphertext) */
    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /** Расшифровать данные AES-256-GCM */
    fun decryptAesGcm(data: ByteArray, key: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    /** Формат зашифрованного сообщения для передачи */
    data class SecureMessage(
        val senderPublicKeyB64: String,   // публичный ключ отправителя (Base64)
        val ciphertextB64: String,         // шифротекст (Base64)
        val nonceB64: String               // nonce/IV (Base64)
    ) {
        fun toJson(): String = """{"senderKey":"$senderPublicKeyB64","ct":"$ciphertextB64","nonce":"$nonceB64"}"""
        companion object {
            fun fromJson(json: org.json.JSONObject): SecureMessage? = try {
                SecureMessage(
                    senderPublicKeyB64 = json.getString("senderKey"),
                    ciphertextB64 = json.getString("ct"),
                    nonceB64 = json.getString("nonce")
                )
            } catch (e: Exception) { null }
        }
    }

    /**
     * Зашифровать сообщение для E2EE (X25519 + AES-GCM).
     * @return JSON строка с зашифрованным сообщением или null
     */
    fun encryptMessage(plaintext: String, myKeyPair: E2EEKeyPair, peerPublicKeyB64: String): String? {
        return try {
            val peerPublic = Base64.decode(peerPublicKeyB64, Base64.NO_WRAP)
            val sharedSecret = computeSharedSecret(myKeyPair.privateKey, peerPublic)
            val derivedKey = hkdfDerive(sharedSecret, HKDF_SALT.toByteArray(), "msg-key".toByteArray(), 32)
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val sm = SecureMessage(
                senderPublicKeyB64 = myKeyPair.publicKeyBase64(),
                ciphertextB64 = Base64.encodeToString(ct, Base64.NO_WRAP),
                nonceB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            )
            sm.toJson()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "encryptMessage failed", e)
            null
        }
    }

    /** Расшифровать E2EE сообщение */
    fun decryptMessage(encryptedJson: String, myKeyPair: E2EEKeyPair): String? {
        return try {
            val json = org.json.JSONObject(encryptedJson)
            val sm = SecureMessage.fromJson(json) ?: return null
            val peerPublic = Base64.decode(sm.senderPublicKeyB64, Base64.NO_WRAP)
            val sharedSecret = computeSharedSecret(myKeyPair.privateKey, peerPublic)
            val derivedKey = hkdfDerive(sharedSecret, HKDF_SALT.toByteArray(), "msg-key".toByteArray(), 32)
            val iv = Base64.decode(sm.nonceB64, Base64.NO_WRAP)
            val ct = Base64.decode(sm.ciphertextB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derivedKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "decryptMessage failed", e)
            null
        }
    }

    /** Зашифровать сообщение с Double Ratchet */
    fun encryptMessageWithDR(plaintext: String, myKeyPair: E2EEKeyPair, state: RatchetState, contactId: String? = null): Pair<String?, RatchetState> {
        return try {
            val ad = myKeyPair.publicKeyBase64().toByteArray(Charsets.UTF_8)
            val (newState, msg) = DoubleRatchet.ratchetEncrypt(state, plaintext.toByteArray(Charsets.UTF_8), ad, contactId)
            Pair(msg.toJson(), newState)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "encryptMessageWithDR failed", e)
            Pair(null, state)
        }
    }

    /** Расшифровать сообщение с Double Ratchet */
    fun decryptMessageWithDR(encryptedJson: String, myKeyPair: E2EEKeyPair, state: RatchetState, contactId: String? = null): Pair<String?, RatchetState> {
        return try {
            val json = org.json.JSONObject(encryptedJson)
            val rm = RatchetMessage.fromJson(json) ?: return Pair(null, state)
            val ad = myKeyPair.publicKeyBase64().toByteArray(Charsets.UTF_8)
            val (newState, plaintext) = DoubleRatchet.ratchetDecrypt(state, rm, ad, contactId)
            Pair(String(plaintext, Charsets.UTF_8), newState)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "decryptMessageWithDR failed", e)
            Pair(null, state)
        }
    }

    /** Сгенерировать ID сообщения */
    fun generateMessageId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Вывести ключ из пароля через PBKDF2 */
    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = 100000): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return factory.generateSecret(spec).encoded
    }

    /** Зашифровать данные для хранения (AES-256-GCM) */
    fun encryptStorage(data: ByteArray, key: ByteArray): ByteArray = encryptAesGcm(data, key)

    /** Расшифровать данные для хранения */
    fun decryptStorage(data: ByteArray, key: ByteArray): ByteArray = decryptAesGcm(data, key)
}
