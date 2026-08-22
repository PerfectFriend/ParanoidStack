package com.paranoidx.sdk.security

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
/**
 * Тесты криптографической утилиты CryptoUtils.
 * Покрывают вывод ключа (PBKDF2), симметричное шифрование (AES-GCM),
 * файловое шифрование и обработку граничных случаев (пустая строка,
 * неверный ключ, рандомизация соли/IV).
 */
class CryptoUtilsTest {

    /**
     * Проверяет детерминированность вывода ключа: один и тот же пароль
     * должен давать одинаковый ключ.
     */
    @Test
    fun `deriveKey produces consistent results with same password`() {
        val key1 = CryptoUtils.deriveKey("testPassword123!")
        val key2 = CryptoUtils.deriveKey("testPassword123!")
        assertArrayEquals(key1.encoded, key2.encoded) // Двоичное представление ключей совпадает
    }

    /**
     * Проверяет, что разные пароли дают разные ключи.
     * Коллизия ключей для разных паролей крайне маловероятна.
     */
    @Test
    fun `deriveKey produces different results with different passwords`() {
        val key1 = CryptoUtils.deriveKey("password1")
        val key2 = CryptoUtils.deriveKey("password2")
        assertFalse(key1.encoded.contentEquals(key2.encoded)) // Ключи не должны совпадать
    }

    /**
     * Проверяет полный цикл шифрования/дешифрования:
     * расшифрованный текст должен в точности соответствовать исходному.
     */
    @Test
    fun `encrypt and decrypt round-trip`() {
        val key = CryptoUtils.deriveKey("mySecretKey")
        val plaintext = "Hello, Secure World!"
        val ciphertext = CryptoUtils.encrypt(plaintext, key)
        assertNotNull(ciphertext)
        assertTrue(ciphertext.isNotEmpty()) // Шифротекст не должен быть пустым
        val decrypted = CryptoUtils.decrypt(ciphertext, key)
        assertEquals(plaintext, decrypted) // Round-trip успешен
    }

    /**
     * Проверяет корректную обработку пустой строки при шифровании:
     * шифрование и дешифрование пустой строки должны проходить без ошибок.
     */
    @Test
    fun `encrypt empty string`() {
        val key = CryptoUtils.deriveKey("key")
        val ciphertext = CryptoUtils.encrypt("", key)
        val decrypted = CryptoUtils.decrypt(ciphertext, key)
        assertEquals("", decrypted) // Пустая строка после round-trip остаётся пустой
    }

    /**
     * Проверяет, что дешифрование с неверным ключом выбрасывает
     * AEADBadTagException (нарушение аутентификации AES-GCM).
     */
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun `decrypt with wrong key throws`() {
        val key1 = CryptoUtils.deriveKey("correct")
        val key2 = CryptoUtils.deriveKey("wrong")
        val ciphertext = CryptoUtils.encrypt("secret", key1)
        CryptoUtils.decrypt(ciphertext, key2) // Ожидается исключение из-за несовпадения тега
    }

    /**
     * Проверяет, что каждый вызов encrypt() с одними и теми же данными
     * даёт разный шифротекст (за счёт случайной соли и IV).
     * Это свойство называется семантической безопасностью.
     */
    @Test
    fun `encrypt produces different ciphertexts each time`() {
        val key = CryptoUtils.deriveKey("randomize")
        val plaintext = "same text"
        val c1 = CryptoUtils.encrypt(plaintext, key)
        val c2 = CryptoUtils.encrypt(plaintext, key)
        assertNotEquals(c1, c2) // Разные шифротексты гарантируют неразличимость
    }

    /**
     * Проверяет сквозное шифрование файлового payload:
     * имя файла и содержимое шифруются вместе и восстанавливаются корректно.
     */
    @Test
    fun `encryptFilePayload round-trips`() {
        val key = CryptoUtils.deriveKey("fileKey")
        val name = "document.txt"
        val content = "File content with sensitive data"
        val encrypted = CryptoUtils.encryptFilePayload(name, content, key)
        assertTrue(encrypted.isNotEmpty())
        val (decryptedName, decryptedContent) = CryptoUtils.decryptFilePayload(encrypted, key)
        assertEquals(name, decryptedName) // Имя файла восстановлено
        assertEquals(content, decryptedContent) // Содержимое восстановлено
    }

    /**
     * Проверяет, что deriveKey возвращает валидный AES-ключ:
     * алгоритм = "AES", размер не менее 16 байт (128 бит).
     */
    @Test
    fun `deriveKey produces valid AES key`() {
        val key = CryptoUtils.deriveKey("strong-password!")
        assertNotNull(key)
        assertEquals("AES", key.algorithm) // Алгоритм ключа — AES
        assertTrue(key.encoded.size >= 16) // Минимальная длина ключа — 128 бит
    }
}
