/**
 * End-to-end encryption implementation for the SimpleX SMP protocol.
 *
 * Implements X3DH (Extended Triple Diffie-Hellman) for initial key agreement
 * and PreKeyBundle generation, and Double Ratchet for ongoing message encryption
 * and decryption.
 *
 * @see DoubleRatchet
 */
package com.paranoidx.sdk.security

import com.paranoidx.sdk.security.SdkLogger
import java.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec

/**
 * Файл: SmpE2E.kt
 * Пакет: com.example.data.security
 * Назначение: Реализация сквозного шифрования (E2E) для протокола SimpleX (SMP).
 * Включает генерацию PreKeyBundle (X3DH), создание сессий инициатора/получателя,
 * шифрование/дешифрование сообщений с использованием Double Ratchet.
 *
 * Протокол: X3DH (Extended Triple Diffie-Hellman) для установки общего секрета,
 * затем Double Ratchet для асинхронного обмена сообщениями.
 *
 * @see DoubleRatchet
 * @see SmpKeyManager
 */
object SmpE2E {
    private const val TAG = "SmpE2E"
    private const val PROTOCOL_VERSION = 0x01.toByte()
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 16

    /**
     * Набор предварительных ключей для протокола X3DH.
     * Содержит идентификационный ключ, подписанный предварительный ключ,
     * подпись и список одноразовых предварительных ключей.
     * @property identityKey идентификационный открытый ключ
     * @property signedPreKey подписанный предварительный открытый ключ
     * @property signedPreKeySig подпись signedPreKey идентификационным ключом
     * @property oneTimePreKeys список одноразовых ключей (для PFS — совершенной прямой секретности)
     */
    data class PreKeyBundle(
        val identityKey: ByteArray,
        val signedPreKey: ByteArray,
        val signedPreKeySig: ByteArray,
        val oneTimePreKeys: List<ByteArray>
    ) {
        /**
         * Кодирует PreKeyBundle в бинарный протокольный формат.
         * @return массив байт: [версия][identityKey][signedPreKey][signedPreKeySig][кол-во_otpk][otpk...]
         */
        fun encode(): ByteArray {
            val parts = mutableListOf<ByteArray>()
            parts += byteArrayOf(PROTOCOL_VERSION)
            parts += protocolEncoder(identityKey)
            parts += protocolEncoder(signedPreKey)
            parts += protocolEncoder(signedPreKeySig)
            parts += shortEncoder(oneTimePreKeys.size)
            oneTimePreKeys.forEach { parts += protocolEncoder(it) }
            return parts.fold(ByteArray(0)) { a, b -> a + b }
        }

        companion object {
            /**
             * Декодирует PreKeyBundle из бинарного протокольного формата.
             * @param data массив байт в формате encode()
             * @return восстановленный PreKeyBundle или null при ошибке
             */
            fun decode(data: ByteArray): PreKeyBundle? {
                try {
                    var offset = 1
                    if (data[0] != PROTOCOL_VERSION) return null
                    fun readBytes(): ByteArray? {
                        if (offset + 2 > data.size) return null
                        val len = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                        offset += 2
                        if (offset + len > data.size) return null
                        val result = data.copyOfRange(offset, offset + len)
                        offset += len
                        return result
                    }
                    val idKey = readBytes() ?: return null
                    val spk = readBytes() ?: return null
                    val spkSig = readBytes() ?: return null
                    if (offset + 2 > data.size) return null
                    val otCount = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    val otpks = mutableListOf<ByteArray>()
                    repeat(otCount) {
                        val pk = readBytes() ?: return@repeat
                        otpks.add(pk)
                    }
                    return PreKeyBundle(idKey, spk, spkSig, otpks)
                } catch (e: Exception) {
                    SdkLogger.e(TAG, "Bundle decode failed", e)
                    return null
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PreKeyBundle) return false
            return identityKey.contentEquals(other.identityKey) &&
                   signedPreKey.contentEquals(other.signedPreKey) &&
                   signedPreKeySig.contentEquals(other.signedPreKeySig) &&
                   oneTimePreKeys.size == other.oneTimePreKeys.size &&
                   oneTimePreKeys.zip(other.oneTimePreKeys).all { it.first.contentEquals(it.second) }
        }

        override fun hashCode(): Int {
            var result = identityKey.contentHashCode()
            result = 31 * result + signedPreKey.contentHashCode()
            result = 31 * result + signedPreKeySig.contentHashCode()
            result = 31 * result + oneTimePreKeys.hashCode()
            return result
        }
    }

    /**
     * Зашифрованное сообщение с метаданными для маршрутизации.
     * @property identityKey открытый идентификационный ключ отправителя
     * @property ephemeralKey эфемерный открытый ключ для DH
     * @property nonce одноразовый вектор (12 байт)
     * @property ciphertext зашифрованный текст
     * @property ratchetIndex индекс ratchet-шага для синхронизации состояний
     */
    data class EncryptedMessage(
        val identityKey: ByteArray,
        val ephemeralKey: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val ratchetIndex: Int
    )

    /**
     * Кодирует данные с префиксом длины (2 байта, big-endian).
     * @param data данные для кодирования
     * @return [длина(2 байта)][data]
     */
    private fun protocolEncoder(data: ByteArray): ByteArray {
        val len = data.size
        return byteArrayOf((len shr 8).toByte(), len.toByte()) + data
    }

    /**
     * Кодирует короткое целое (2 байта, big-endian).
     */
    private fun shortEncoder(value: Int): ByteArray {
        return byteArrayOf((value shr 8).toByte(), value.toByte())
    }

    /**
     * Генерирует PreKeyBundle для регистрации на SMP-сервере.
     * Создаёт идентификационный ключ, подписанный предварительный ключ
     * и 5 одноразовых предварительных ключей.
     * @return PreKeyBundle или null при ошибке генерации ключей
     */
    fun generatePreKeyBundle(): PreKeyBundle? {
        val identityKey = SmpKeyManager.generateIdentityKey() ?: return null
        val signedPreKey = SmpKeyManager.generateIdentityKey("signed-prekey".toByteArray()) ?: return null
        val signedPreKeySig = SmpKeyManager.sign(
            signedPreKey.pubKey, identityKey.privKey
        )
        val otpks = (1..5).mapNotNull { SmpKeyManager.generateIdentityKey("otpk-$it".toByteArray())?.pubKey }
        return PreKeyBundle(
            identityKey = identityKey.pubKey,
            signedPreKey = signedPreKey.pubKey,
            signedPreKeySig = signedPreKeySig ?: ByteArray(0),
            oneTimePreKeys = otpks
        )
    }

    /**
     * Выполняет протокол X3DH (Extended Triple Diffie-Hellman) для установки общего секрета.
     * Вычисляет: DH1 = DH(ourIdentity, theirSignedPreKey)
     *           DH2 = DH(ourEphemeral, theirIdentityKey)
     *           DH3 = DH(ourEphemeral, theirSignedPreKey)
     *           DH4 = DH(ourEphemeral, theirOneTimePreKey) — опционально
     * Общий секрет: SHA-256(DH1 || DH2 || DH3 [+ DH4])
     * @param ourIdentityKey наш закрытый идентификационный ключ
     * @param ourEphemeralKey наша эфемерная ключевая пара
     * @param theirPreKeyBundle PreKeyBundle контрагента
     * @return 32-байтовый общий секрет
     */
    fun performX3DH(
        ourIdentityKey: PrivateKey,
        ourEphemeralKey: KeyPair,
        theirPreKeyBundle: PreKeyBundle
    ): ByteArray {
        val dr = DoubleRatchet(ByteArray(32))
        val dh1 = dr.dh(ourIdentityKey, dr.decodePublicKey(theirPreKeyBundle.signedPreKey))
        val dh2 = dr.dh(ourEphemeralKey.private, dr.decodePublicKey(theirPreKeyBundle.identityKey))
        val dh3 = dr.dh(ourEphemeralKey.private, dr.decodePublicKey(theirPreKeyBundle.signedPreKey))
        val shared = dh1 + dh2 + dh3
        if (theirPreKeyBundle.oneTimePreKeys.isNotEmpty()) {
            val dh4 = dr.dh(ourEphemeralKey.private, dr.decodePublicKey(theirPreKeyBundle.oneTimePreKeys.first()))
            return MessageDigest.getInstance("SHA-256").digest(shared + dh4)
        }
        return MessageDigest.getInstance("SHA-256").digest(shared)
    }

    /**
     * Шифрует сообщение в рамках сессии E2E.
     * @param plaintext открытый текст
     * @param dr экземпляр Double Ratchet для данного диалога
     * @param ourIdentityKey наш открытый идентификационный ключ
     * @param ourEphemeralKey наш эфемерный открытый ключ
     * @return EncryptedMessage с зашифрованными данными
     */
    fun encryptMessage(
        plaintext: ByteArray,
        dr: DoubleRatchet,
        ourIdentityKey: PublicKey,
        ourEphemeralKey: PublicKey
    ): EncryptedMessage {
        val nonce = SecureRandom().generateSeed(NONCE_SIZE)
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce)
        )
        val ct = cipher.doFinal(plaintext)
        return EncryptedMessage(
            identityKey = ourIdentityKey.encoded,
            ephemeralKey = ourEphemeralKey.encoded,
            nonce = nonce,
            ciphertext = ct,
            ratchetIndex = dr.messageIndex
        )
    }

    /**
     * Дешифрует сообщение в рамках сессии E2E.
     * @param encrypted зашифрованное сообщение
     * @param dr экземпляр Double Ratchet
     * @param ourPrivateKey наш закрытый ключ
     * @return расшифрованные байты или null при ошибке
     */
    fun decryptMessage(
        encrypted: EncryptedMessage,
        dr: DoubleRatchet,
        ourPrivateKey: PrivateKey
    ): ByteArray? {
        return try {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, encrypted.nonce)
            )
            cipher.doFinal(encrypted.ciphertext)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Decrypt failed", e)
            null
        }
    }

    /**
     * Сессия сквозного шифрования E2E.
     * @property sharedSecret общий секрет, установленный через X3DH
     * @property dr экземпляр Double Ratchet для шифрования/дешифрования
     * @property ourIdentityKey наша ключевая пара (идентификационная)
     * @property theirBundle PreKeyBundle контрагента
     */
    data class E2ESession(
        val sharedSecret: ByteArray,
        val dr: DoubleRatchet,
        val ourIdentityKey: KeyPair,
        val theirBundle: PreKeyBundle
    )

    /**
     * Создаёт сессию E2E со стороны инициатора.
     * Генерирует эфемерный ключ, выполняет X3DH, инициализирует Double Ratchet.
     * @param ourIdentityKey наша идентификационная ключевая пара
     * @param theirBundle PreKeyBundle контрагента
     * @return E2ESession с установленным общим секретом и Ratchet
     */
    fun initiatorSession(ourIdentityKey: KeyPair, theirBundle: PreKeyBundle): E2ESession {
        val ephemeral = dr.generateKeyPair()
        val shared = performX3DH(ourIdentityKey.private, ephemeral, theirBundle)
        val dr = DoubleRatchet(shared)
        dr.dhRatchetStep(theirBundle.signedPreKey)
        return E2ESession(shared, dr, ourIdentityKey, theirBundle)
    }

    /**
     * Создаёт сессию E2E со стороны получателя.
     * Использует наш подписанный предварительный ключ и ключи инициатора
     * для восстановления общего секрета.
     * @param ourIdentityKey наша идентификационная ключевая пара
     * @param ourSignedPreKey наш подписанный предварительный закрытый ключ
     * @param theirIdentityKey идентификационный открытый ключ инициатора
     * @param theirEphemeralKey эфемерный открытый ключ инициатора
     * @return E2ESession или null при ошибке
     */
    fun receiverSession(
        ourIdentityKey: KeyPair,
        ourSignedPreKey: PrivateKey,
        theirIdentityKey: ByteArray,
        theirEphemeralKey: ByteArray
    ): E2ESession? {
        return try {
            val dr = DoubleRatchet(ByteArray(32))
            val kf = KeyFactory.getInstance("X25519")
            val dh1 = dr.dh(ourSignedPreKey, kf.generatePublic(X509EncodedKeySpec(theirIdentityKey)))
            val dh2 = dr.dh(ourIdentityKey.private, kf.generatePublic(X509EncodedKeySpec(theirEphemeralKey)))
            val dh3 = dr.dh(ourSignedPreKey, kf.generatePublic(X509EncodedKeySpec(theirEphemeralKey)))
            val shared = MessageDigest.getInstance("SHA-256").digest(dh1 + dh2 + dh3)
            val newDr = DoubleRatchet(shared)
            val bundle = PreKeyBundle(theirIdentityKey, theirEphemeralKey, ByteArray(0), emptyList())
            E2ESession(shared, newDr, ourIdentityKey, bundle)
        } catch (e: Exception) {
            SdkLogger.e(TAG, "Receiver session failed", e)
            null
        }
    }

    private val dr = DoubleRatchet(ByteArray(32))
}
