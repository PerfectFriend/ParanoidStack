/**
 * Client-side cryptographic utility for AES-256-GCM encryption and decryption.
 *
 * Derives encryption keys from user passphrases via SHA-256. Stateless singleton
 * supporting arbitrary text and file payload encryption with Base64 encoding.
 */
package com.paranoidx.sdk.security

import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Файл: CryptoUtils.kt
 * Пакет: com.example.data.security
 * Назначение: Набор криптографических утилит для шифрования/дешифрования на стороне клиента.
 * Реализует симметричное шифрование AES-256-GCM с выводом ключа из парольной фразы (PIN/passphrase)
 * через SHA-256. Поддерживает шифрование произвольных текстовых данных, а также файловых полезных нагрузок
 * (имя файла + содержимое). Все зашифрованные данные кодируются в Base64 для безопасной передачи/хранения.
 *
 * Архитектура: объект-одиночка (object), не хранит состояние. Все методы stateless.
 *
 * @see SecretKeySpec
 * @see Cipher
 */
/**
 * Robust, client-side cryptographic utility implementing AES-256 GCM encryption/decryption.
 * Can use a master text pin or user passphrase to derive encryption keys via SHA-256.
 */
object CryptoUtils {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    /**
     * Derives a 256-bit AES key from a given PIN or passphrase.
     */
    fun deriveKey(passphrase: String): SecretKey {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    /**
     * Encrypts plaintext string with GCM using the derived key.
     * Returns a string containing: Base64(IV) + ":" + Base64(Ciphertext)
     */
    fun encrypt(plaintext: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivBase64 = Base64.getEncoder().withoutPadding().encodeToString(iv)
        val ciphertextBase64 = Base64.getEncoder().withoutPadding().encodeToString(ciphertext)
        
        return "$ivBase64:$ciphertextBase64"
    }

    /**
     * Decrypts encrypted string (format "Base64(IV):Base64(Ciphertext)") using the derived key.
     */
    fun decrypt(encryptedPayload: String, secretKey: SecretKey): String {
        val parts = encryptedPayload.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Malformed encrypted payload")
        
        val iv = Base64.getDecoder().decode(parts[0])
        val ciphertext = Base64.getDecoder().decode(parts[1])
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Генерирует случайный крипто-ключ для восстановления резервной копии.
     * Ключ состоит из 24 случайных байт, закодированных в Base64 без символов '+', '/', '=',
     * разбитых на группы по 4 символа, разделённых дефисами, для удобства чтения/ввода человеком.
     * @return строка ключа восстановления формата "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"
     */
    fun generateBackupRecoveryKey(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
            .replace("+", "X")
            .replace("/", "Y")
            .replace("=", "")
            .take(24)
            .chunked(4)
            .joinToString("-")
    }

    /**
     * Шифрует файловую полезную нагрузку (имя_файла|содержимое) алгоритмом AES-256-GCM.
     * @param fileName имя файла
     * @param fileContent содержимое файла в виде строки
     * @param secretKey секретный ключ AES
     * @return зашифрованная строка в формате "Base64(IV):Base64(Ciphertext)"
     */
    fun encryptFilePayload(fileName: String, fileContent: String, secretKey: SecretKey): String {
        // Формируем метаданные: имя файла и содержимое через разделитель '|'
        val metadata = "$fileName|$fileContent"
        return encrypt(metadata, secretKey)
    }

    /**
     * Дешифрует файловую полезную нагрузку и возвращает пару (имя_файла, содержимое).
     * @param encryptedPayload зашифрованная строка в формате "Base64(IV):Base64(Ciphertext)"
     * @param secretKey секретный ключ AES
     * @return пара (имя файла, содержимое файла)
     * @throws IllegalArgumentException если структура расшифрованных данных нарушена (нет разделителя '|')
     */
    fun decryptFilePayload(encryptedPayload: String, secretKey: SecretKey): Pair<String, String> {
        val decrypted = decrypt(encryptedPayload, secretKey)
        // Разделяем на имя файла и содержимое по первому вхождению '|'
        val parts = decrypted.split("|", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Invalid shared file content structural integrity")
        return Pair(parts[0], parts[1])
    }
}
