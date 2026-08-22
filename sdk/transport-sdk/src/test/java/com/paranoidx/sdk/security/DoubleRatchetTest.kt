package com.paranoidx.sdk.security

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
/**
 * Модульные тесты для реализации протокола Double Ratchet (двойной храповик).
 * Проверяет создание экземпляров, шифрование/дешифрование отдельных сообщений,
 * управление счётчиком сообщений, сквозную связь между двумя сторонами
 * (Алиса и Боб) и корректную обработку неверных общих секретов.
 */
class DoubleRatchetTest {

    /**
     * Проверяет, что конструктор DoubleRatchet создаёт валидный экземпляр
     * с общим секретом 32 байта, и начальный messageIndex равен 0.
     */
    @Test
    fun `constructor creates valid instance`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val dr = DoubleRatchet(sharedSecret)
        assertNotNull(dr) // Экземпляр создан
        assertEquals(0, dr.messageIndex) // Счётчик начинается с нуля
    }

    /**
     * Проверяет, что ratchetSend() возвращает непустой шифротекст,
     * nonce размером 12 байт (стандарт AES-GCM) и открытый ключ.
     */
    @Test
    fun `ratchetSend returns ciphertext nonce and pubKey`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val dr = DoubleRatchet(sharedSecret)
        val (ciphertext, nonce, pubKey) = dr.ratchetSend("Hello E2EE!")
        assertTrue("ciphertext should not be empty", ciphertext.isNotEmpty())
        assertEquals("nonce should be 12 bytes from AES-GCM IV", 12, nonce.size) // AES-GCM использует 96-битный IV
    }

    /**
     * Проверяет, что messageIndex увеличивается на 1 после каждого
     * вызова ratchetSend(), обеспечивая порядок сообщений.
     */
    @Test
    fun `messageIndex increments after each send`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val dr = DoubleRatchet(sharedSecret)
        val idx0 = dr.messageIndex
        dr.ratchetSend("msg1")
        assertEquals(idx0 + 1, dr.messageIndex) // Первое сообщение увеличило индекс на 1
        dr.ratchetSend("msg2")
        assertEquals(idx0 + 2, dr.messageIndex) // Второе сообщение увеличило индекс на 2
    }

    /**
     * Проверяет сквозную связь: два экземпляра DoubleRatchet с одинаковым
     * sharedSecret могут обмениваться зашифрованными сообщениями.
     * Алиса шифрует -> Боб расшифровывает -> исходный текст.
     */
    @Test
    fun `two instances with same shared secret can communicate`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val alice = DoubleRatchet(sharedSecret)
        val bob = DoubleRatchet(sharedSecret)
        val message = "Secret message from Alice"
        val (ciphertext, nonce, pubKey) = alice.ratchetSend(message)
        val decrypted = bob.ratchetReceive(ciphertext, nonce, pubKey)
        assertEquals(message, decrypted) // Боб получил исходное сообщение Алисы
    }

    /**
     * Проверяет, что разные sharedSecret дают разные шифротексты
     * для одного и того же открытого текста.
     * Это гарантирует, что ключи не пересекаются.
     */
    @Test
    fun `different shared secrets produce different ciphertexts`() {
        val secret1 = ByteArray(32) { 0x01 }
        val secret2 = ByteArray(32) { 0x02 }
        val alice = DoubleRatchet(secret1)
        val dr2 = DoubleRatchet(secret2)
        val (c1, _, _) = alice.ratchetSend("test")
        val (c2, _, _) = dr2.ratchetSend("test")
        assertFalse(c1.contentEquals(c2)) // Разные ключи -> разные шифротексты
    }

    /**
     * Проверяет, что дешифрование с неверным sharedSecret выбрасывает
     * AEADBadTagException (атака "Евы" — прослушивание с чужим ключом).
     */
    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun `receive with wrong key throws`() {
        val aliceSecret = ByteArray(32) { 0xAA.toByte() }
        val eveSecret = ByteArray(32) { 0xBB.toByte() }
        val alice = DoubleRatchet(aliceSecret)
        val eve = DoubleRatchet(eveSecret)
        val (ciphertext, nonce, pubKey) = alice.ratchetSend("secret")
        eve.ratchetReceive(ciphertext, nonce, pubKey) // Ожидается исключение: неверный тег аутентификации
    }

    /**
     * Проверяет, что порядок сообщений сохраняется при отправке
     * нескольких сообщений подряд. Каждое сообщение расшифровывается
     * в том же порядке, в котором было отправлено.
     */
    @Test
    fun `multiple messages preserve ordering`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val alice = DoubleRatchet(sharedSecret)
        val bob = DoubleRatchet(sharedSecret)
        val messages = listOf("msg1", "msg2", "msg3")
        for ((i, msg) in messages.withIndex()) {
            val (ct, nonce, pk) = alice.ratchetSend(msg)
            val decrypted = bob.ratchetReceive(ct, nonce, pk)
            assertEquals("Message $i mismatch", msg, decrypted) // Сообщение i восстановлено корректно
        }
    }
}
