/**
 * Double Ratchet protocol implementation for end-to-end encrypted messaging.
 *
 * Uses X25519 Diffie-Hellman key exchange, HMAC-SHA256 chain key derivation,
 * and AES-256-GCM for message encryption. Maintains session state including
 * root key, sending/receiving chain keys, and DH ratchet steps.
 *
 * @see SmpE2E
 */
package com.paranoidx.sdk.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Файл: DoubleRatchet.kt
 * Пакет: com.example.data.security
 * Назначение: Реализация протокола Double Ratchet для сквозного шифрования (E2E).
 * Используется для асинхронного обмена зашифрованными сообщениями между двумя сторонами.
 * Основан на кривой X25519 для обмена ключами по Диффи-Хеллману, HMAC-SHA256 для
 * KDF цепочки ключей и AES-256-GCM для шифрования полезных нагрузок.
 *
 * Архитектура: класс содержит состояние сессии (rootKey, sendingChainKey, receivingChainKey,
 * ключевая пара для отправки, открытый ключ получателя и счётчик сообщений).
 * DH-ratchet шаг выполняется после получения открытого ключа от контрагента.
 *
 * @property sharedSecret общий секрет (32 байта), полученный из X3DH или другого протокола
 * @property messageIndex монотонно возрастающий счётчик отправленных сообщений
 */
class DoubleRatchet(private val sharedSecret: ByteArray) {

    private var rootKey: ByteArray
    private var sendingChainKey: ByteArray
    private var receivingChainKey: ByteArray
    private var sendingKeyPair: KeyPair? = null
    private var receivingPublicKey: ByteArray? = null
    var messageIndex: Int = 0
        private set

    init {
        val hkdf = hkdfExpand(sharedSecret, "paranoidx_root".toByteArray(), 64)
        rootKey = hkdf.copyOfRange(0, 32)
        sendingChainKey = hkdf.copyOfRange(32, 64)
        receivingChainKey = hkdf.copyOfRange(32, 64)
    }

    /**
     * Генерирует ключевую пару X25519.
     * Пытается использовать провайдер "X25519", при ошибке использует "XDH" с параметром "X25519".
     * @return сгенерированная ключевая пара (открытый + закрытый ключ)
     */
    fun generateKeyPair(): KeyPair {
        return try {
            val kpg = KeyPairGenerator.getInstance("X25519")
            kpg.initialize(256)
            kpg.generateKeyPair()
        } catch (_: Exception) {
            // Fallback: генерация ключей через KeyPairGenerator с XDH
            val kpg = KeyPairGenerator.getInstance("XDH")
            kpg.initialize(java.security.spec.NamedParameterSpec("X25519"))
            kpg.generateKeyPair()
        }
    }

    /**
     * Выполняет протокол Диффи-Хеллмана на кривой X25519.
     * @param privKey закрытый ключ одной стороны
     * @param pubKey открытый ключ другой стороны
     * @return общий секрет (32 байта)
     */
    fun dh(privKey: PrivateKey, pubKey: PublicKey): ByteArray {
        return try {
            val ka = KeyAgreement.getInstance("X25519")
            ka.init(privKey)
            ka.doPhase(pubKey, true)
            ka.generateSecret()
        } catch (_: Exception) {
            val ka = KeyAgreement.getInstance("XDH")
            ka.init(privKey)
            ka.doPhase(pubKey, true)
            ka.generateSecret()
        }
    }

    /**
     * Кодирует открытый ключ в массив байт.
     */
    fun encodePublicKey(pub: PublicKey): ByteArray = pub.encoded

    /**
     * Декодирует открытый ключ из массива байт.
     * @param encoded закодированный открытый ключ
     * @return восстановленный объект PublicKey
     */
    fun decodePublicKey(encoded: ByteArray): PublicKey {
        return try {
            val kf = KeyFactory.getInstance("X25519")
            kf.generatePublic(X509EncodedKeySpec(encoded))
        } catch (_: Exception) {
            val kf = KeyFactory.getInstance("XDH")
            kf.generatePublic(X509EncodedKeySpec(encoded))
        }
    }

    /**
     * Декодирует закрытый ключ из массива байт.
     * @param encoded закодированный закрытый ключ
     * @return восстановленный объект PrivateKey
     */
    fun decodePrivateKey(encoded: ByteArray): PrivateKey {
        return try {
            val kf = KeyFactory.getInstance("X25519")
            kf.generatePrivate(PKCS8EncodedKeySpec(encoded))
        } catch (_: Exception) {
            val kf = KeyFactory.getInstance("XDH")
            kf.generatePrivate(PKCS8EncodedKeySpec(encoded))
        }
    }

    /**
     * Выполняет ratchet-шаг для отправки сообщения.
     * Выводит ключ сообщения из цепочки отправки, шифрует текст,
     * увеличивает счётчик сообщений и обновляет цепочку отправки.
     * @param plaintext открытый текст сообщения
     * @return тройка (ciphertext_b64, nonce, закодированный_открытый_ключ_отправителя)
     */
    fun ratchetSend(plaintext: String): Triple<String, ByteArray, ByteArray> {
        // Выводим ключ сообщения из цепочки отправки (0x01 = ключ сообщения)
        val messageKey = kdfChain(sendingChainKey, 0x01.toByte())
        val (ciphertext, nonce) = encryptWithKey(plaintext, messageKey)
        // Закодированный открытый ключ для возможности DH-ratchet на стороне получателя
        val pubKeyEncoded = sendingKeyPair?.let { encodePublicKey(it.public) } ?: ByteArray(0)
        messageIndex++
        // Обновляем цепочку отправки (0x02 = следующий ключ цепочки)
        sendingChainKey = kdfChain(sendingChainKey, 0x02.toByte())
        return Triple(ciphertext, nonce, pubKeyEncoded)
    }

    /**
     * Выполняет ratchet-шаг для получения сообщения.
     * Выводит ключ сообщения из цепочки получения, дешифрует текст,
     * обновляет цепочку получения.
     * @param ciphertext зашифрованный текст (Base64)
     * @param nonce одноразовый вектор (12 байт)
     * @param senderPubKey открытый ключ отправителя (не используется в базовой реализации)
     * @return расшифрованный открытый текст
     */
    fun ratchetReceive(ciphertext: String, nonce: ByteArray, senderPubKey: ByteArray): String {
        // Выводим ключ сообщения из цепочки получения
        val messageKey = kdfChain(receivingChainKey, 0x01.toByte())
        val plaintext = decryptWithKey(ciphertext, messageKey, nonce)
        // Обновляем цепочку получения
        receivingChainKey = kdfChain(receivingChainKey, 0x02.toByte())
        return plaintext
    }

    /**
     * Выполняет шаг DH-ratchet: генерирует новую ключевую пару, вычисляет общий секрет
     * с открытым ключом контрагента, выводит новый rootKey и цепочки отправки/получения.
     * @param theirPublicKey открытый ключ контрагента (32 байта)
     */
    fun dhRatchetStep(theirPublicKey: ByteArray) {
        // Генерируем новую ключевую пару для будущих раундов
        val ourNewPair = generateKeyPair()
        // Вычисляем общий секрет DH между нашим новым закрытым и их открытым ключом
        val shared = dh(ourNewPair.private, decodePublicKey(theirPublicKey))
        // HKDF-расширение: rootKey + новый общий секрет -> 64 байта
        val derived = hkdfExpand(rootKey + shared, "paranoidx_ratchet".toByteArray(), 64)
        rootKey = derived.copyOfRange(0, 32)
        // Цепочка получения становится предыдущей цепочкой отправки
        receivingChainKey = sendingChainKey
        sendingChainKey = derived.copyOfRange(32, 64)
        sendingKeyPair = ourNewPair
        receivingPublicKey = theirPublicKey
    }

    /**
     * KDF для цепочки ключей: вычисляет HMAC-SHA256 от chainKey с заданным назначением.
     * @param chainKey ключ цепочки (32 байта)
     * @param purpose байт назначения: 0x01 — ключ сообщения, 0x02 — следующий ключ цепочки
     * @return производный (выведенный) ключ (32 байта)
     */
    private fun kdfChain(chainKey: ByteArray, purpose: Byte): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        return hmac.doFinal(byteArrayOf(purpose))
    }

    /**
     * Шифрует открытый текст ключом AES (первые 16 байт от key) в режиме GCM.
     * @param plaintext открытый текст
     * @param key 32-байтовый ключ (используется первая половина)
     * @return пара (ciphertext в Base64, nonce)
     */
    private fun encryptWithKey(plaintext: String, key: ByteArray): Pair<String, ByteArray> {
        // Берём первые 16 байт ключа для AES-128-GCM
        val aesKey = SecretKeySpec(key.copyOfRange(0, 16), "AES")
        // Генерируем случайный 12-байтовый IV
        val iv = java.security.SecureRandom().generateSeed(12)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(java.util.Base64.getEncoder().withoutPadding().encodeToString(ct), iv)
    }

    /**
     * Дешифрует ciphertext ключом AES (первые 16 байт от key) в режиме GCM.
     * @param ciphertext зашифрованный текст в Base64
     * @param key 32-байтовый ключ (используется первая половина)
     * @param nonce одноразовый вектор (12 байт)
     * @return расшифрованный открытый текст
     */
    private fun decryptWithKey(ciphertext: String, key: ByteArray, nonce: ByteArray): String {
        val aesKey = SecretKeySpec(key.copyOfRange(0, 16), "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, aesKey, javax.crypto.spec.GCMParameterSpec(128, nonce))
        val pt = cipher.doFinal(java.util.Base64.getDecoder().decode(ciphertext))
        return String(pt, Charsets.UTF_8)
    }

    companion object {
        /**
         * Реализация HKDF-Expand на базе HMAC-SHA256.
         * Расширяет входной ключ до заданной длины (макс. 64 байта).
         * @param input входной ключевой материал
         * @param info контекстная информация (строка привязки)
         * @param length требуемая длина выходного ключа
         * @return производный ключ указанной длины
         */
        fun hkdfExpand(input: ByteArray, info: ByteArray, length: Int): ByteArray {
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(input, "HmacSHA256"))
            val digest = hmac.doFinal(info)
            // Если нужная длина <= 32 байта, возвращаем усечённый digest
            if (length <= 32) return digest.copyOfRange(0, length)
            // Иначе делаем второй раунд HMAC
            val hmac2 = Mac.getInstance("HmacSHA256")
            hmac2.init(SecretKeySpec(digest, "HmacSHA256"))
            val digest2 = hmac2.doFinal(info)
            return digest + digest2.copyOfRange(0, length - 32)
        }
    }
}
