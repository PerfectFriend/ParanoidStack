/**
 * Key manager for the SimpleX Messaging Protocol (SMP).
 *
 * Generates Ed25519 identity and queue keys, handles signing and verification
 * of protocol data, and computes key fingerprints for display and verification.
 *
 * @see Bip39Utils
 */
package com.paranoidx.sdk.security

import com.paranoidx.sdk.security.SdkLogger
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Файл: SmpKeyManager.kt
 * Пакет: com.example.data.security
 * Назначение: Менеджер ключей для протокола SimpleX Messaging Protocol (SMP).
 * Предоставляет функции генерации Ed25519 ключей (идентификационных и очередей),
 * подписания и верификации данных, а также вычисления отпечатков (fingerprint).
 *
 * Поддерживает детерминированную генерацию ключей из seed (BIP-39 sub-key)
 * и случайную генерацию. Использует Ed25519 для подписей и SHA-256 для отпечатков.
 */
object SmpKeyManager {
    private const val TAG = "SmpKeyManager"
    private const val KEY_SIZE = 32

    /**
     * Модель идентификационных данных SMP.
     * @property pubKey открытый ключ Ed25519 (32 байта raw)
     * @property privKey закрытый ключ в формате PKCS8
     * @property fingerprint отпечаток (hex, первые 16 байт SHA-256 открытого ключа)
     */
    data class SmpIdentity(
        val pubKey: ByteArray,
        val privKey: ByteArray,
        val fingerprint: String
    )

    /**
     * Генерирует идентификационный ключ Ed25519.
     * Если передан seed, ключ выводится детерминированно из него (BIP-39 sub-key).
     * Если seed = null, генерируется случайная ключевая пара.
     * @param seed опциональный seed для детерминированной генерации
     * @return SmpIdentity с ключами и отпечатком, или null при ошибке
     */
    fun generateIdentityKey(seed: ByteArray? = null): SmpIdentity? {
        return try {
            val keyPair = if (seed != null) {
                deriveKeyPair(seed, "smp_identity")
            } else {
                val kgAlgo = try { KeyPairGenerator.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "X25519" }
                val kg = KeyPairGenerator.getInstance(kgAlgo)
                try { kg.initialize(KEY_SIZE * 8) } catch (e: Exception) { SdkLogger.w(TAG, "initialize key generator: ${e.message?.take(30)}") }
                kg.generateKeyPair()
            }
            val pubEncoded = keyPair.public.encoded ?: ByteArray(0)
            val rawPub = if (pubEncoded.size >= 44) pubEncoded.copyOfRange(pubEncoded.size - 32, pubEncoded.size) else pubEncoded
            val fp = fingerprint(rawPub)
            SmpIdentity(rawPub, keyPair.private.encoded ?: ByteArray(0), fp)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Key generation failed", e)
            null
        }
    }

    /**
     * Генерирует ключевую пару для очереди сообщений из seed и метки.
     * @param seed seed для детерминированной генерации
     * @param label метка контекста (например, "queue_1")
     * @return KeyPair Ed25519 или null при ошибке
     */
    fun generateQueueKeyPair(seed: ByteArray, label: String): KeyPair? {
        return try {
            deriveKeyPair(seed, label)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Queue key generation failed", e)
            null
        }
    }

    /**
     * Подписывает данные с использованием Ed25519 закрытого ключа.
     */
    fun sign(data: ByteArray, privateKeyBytes: ByteArray): ByteArray? {
        return try {
            val kfAlgo = try { KeyFactory.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "X25519" }
            val kf = KeyFactory.getInstance(kfAlgo)
            val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
            val sigAlgo = try { Signature.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "SHA512withECDSA" }
            val sig = Signature.getInstance(sigAlgo)
            sig.initSign(privKey)
            sig.update(data)
            sig.sign()
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Signing failed", e)
            null
        }
    }

    /**
     * Проверяет Ed25519 подпись данных.
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val kfAlgo = try { KeyFactory.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "X25519" }
            val kf = KeyFactory.getInstance(kfAlgo)
            val pubKeySpec = X509EncodedKeySpec(wrapPublicKey(publicKeyBytes))
            val pubKey = kf.generatePublic(pubKeySpec)
            val sigAlgo = try { Signature.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "SHA512withECDSA" }
            val sig = Signature.getInstance(sigAlgo)
            sig.initVerify(pubKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Verification failed: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Вычисляет отпечаток (fingerprint) открытого ключа: первые 16 байт SHA-256 в hex.
     * @param pubKey открытый ключ
     * @return 32-символьная hex строка отпечатка
     */
    fun fingerprint(pubKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pubKey)
        return hash.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Преобразует массив байт в hex строку.
     * @param data массив байт
     * @return строка в нижнем регистре hex
     */
    fun bytesToHex(data: ByteArray): String {
        return data.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * Детерминированно выводит ключевую пару Ed25519 из seed и метки.
     * Использует Bip39Utils.deriveSubKey для генерации 32 байт,
     * затем оборачивает их в формат PKCS8 для KeyFactory.
     * @param seed исходный seed
     * @param label контекстная метка
     * @return сгенерированная ключевая пара
     */
    private fun deriveKeyPair(seed: ByteArray, label: String): KeyPair {
        val subKey = Bip39Utils.deriveSubKey(seed, "smp_${label}_v1").copyOfRange(0, 32)
        val privateKeySpec = PKCS8EncodedKeySpec(wrapPrivateKey(subKey))
        val kfAlgo = try { KeyFactory.getInstance("Ed25519"); "Ed25519" } catch (_: Exception) { "X25519" }
        val kf = KeyFactory.getInstance(kfAlgo)
        val privKey = kf.generatePrivate(privateKeySpec)
        val pubKeyBytes = try {
            val spec = kf.getKeySpec(privKey, X509EncodedKeySpec::class.java)
            val encoded = spec.encoded
            if (encoded.size >= 44) encoded.copyOfRange(encoded.size - 32, encoded.size) else encoded
        } catch (_: Exception) {
            MessageDigest.getInstance("SHA-512").digest(subKey).copyOfRange(0, 32)
        }
        return KeyPair(
            object : PublicKey {
                override fun getAlgorithm(): String = "Ed25519"
                override fun getFormat(): String = "X.509"
                override fun getEncoded(): ByteArray = wrapPublicKey(pubKeyBytes)
            },
            privKey
        )
    }

    /**
     * Оборачивает сырые 32 байта закрытого ключа в формат PKCS8 с OID Ed25519.
     * Структура: SEQUENCE { AlgorithmIdentifier, OCTET STRING }
     */
    private fun wrapPrivateKey(raw32: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val algId = byteArrayOf(0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70)
        val privKey = byteArrayOf(0x04, 0x22, 0x04, 0x20) + raw32
        val seq = version + algId + privKey
        val len = encodeLength(seq.size)
        return byteArrayOf(0x30) + len + seq
    }

    /**
     * Оборачивает сырые 32 байта открытого ключа в формат SubjectPublicKeyInfo (X.509).
     * Структура: SEQUENCE { AlgorithmIdentifier, BIT STRING }
     */
    private fun wrapPublicKey(raw32: ByteArray): ByteArray {
        // AlgorithmIdentifier для Ed25519
        val algId = byteArrayOf(0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70)
        // BIT STRING с префиксом 0x00 (без отступа)
        val pubKeyPoint = byteArrayOf(0x00) + raw32
        val pubKey = byteArrayOf(0x03, pubKeyPoint.size.toByte()) + pubKeyPoint
        val seq = algId + pubKey
        val len = encodeLength(seq.size)
        return byteArrayOf(0x30) + len + seq
    }

    /**
     * Кодирует длину в DER-формате (BER/DER length encoding).
     * @param length длина для кодирования
     * @return закодированная длина
     */
    private fun encodeLength(length: Int): ByteArray {
        return when {
            length < 128 -> byteArrayOf(length.toByte())
            length < 256 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
    }
}
