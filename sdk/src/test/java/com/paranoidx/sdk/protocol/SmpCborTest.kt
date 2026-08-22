package com.paranoidx.sdk.protocol

import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты CBOR-кодирования/декодирования для протокола SMP.
 * Покрывает базовые типы: целые числа, строки, байтовые строки,
 * null, boolean, ассоциативные карты и обработку некорректных данных.
 */
class SmpCborTest {

    /**
     * Проверяет encode/decode для положительного целого числа 42.
     * CBOR использует компактное представление для малых чисел.
     */
    @Test
    fun `encode and decode positive integer`() {
        val encoded = SmpCbor.encodeInt(42)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(42, decoded.asInt())
    }

    /**
     * Проверяет encode/decode для нуля — граничное значение для беззнаковых целых.
     */
    @Test
    fun `encode and decode zero`() {
        val encoded = SmpCbor.encodeInt(0)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(0, decoded.asInt())
    }

    /**
     * Проверяет encode/decode для большого целого числа (65536),
     * которое требует дополнительного байта в CBOR.
     */
    @Test
    fun `encode and decode large integer`() {
        val encoded = SmpCbor.encodeInt(65536)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(65536, decoded.asInt())
    }

    /**
     * Проверяет encode/decode для байтовой строки с байтом 0xFF.
     * Важно: 0xFF в Kotlin/Java представляется как 0xFF.toByte().
     */
    @Test
    fun `encode and decode byte string`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val encoded = SmpCbor.encodeBytes(data)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertArrayEquals(data, decoded.asBytes())
    }

    /**
     * Проверяет encode/decode для пустой байтовой строки.
     */
    @Test
    fun `encode and decode empty byte string`() {
        val encoded = SmpCbor.encodeBytes(ByteArray(0))
        val (decoded, _) = SmpCbor.decode(encoded)
        assertArrayEquals(ByteArray(0), decoded.asBytes())
    }

    /**
     * Проверяет round-trip текстовой строки ASCII.
     */
    @Test
    fun `encode and decode text string`() {
        val text = "Hello SimpleX!"
        val encoded = SmpCbor.encodeText(text)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(text, decoded.asText())
    }

    /**
     * Проверяет round-trip пустой текстовой строки.
     */
    @Test
    fun `encode and decode empty text`() {
        val encoded = SmpCbor.encodeText("")
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals("", decoded.asText())
    }

    /**
     * Проверяет корректную обработку Unicode / кириллицы.
     * Строка "Привет Мир!" должна корректно кодироваться и декодироваться в CBOR.
     */
    @Test
    fun `encode and decode Unicode text`() {
        val text = "Привет Мир!"
        val encoded = SmpCbor.encodeText(text)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(text, decoded.asText())
    }

    /**
     * Проверяет encode/decode карты с числовыми ключами.
     * Карта содержит текст, целое число и байтовый массив.
     * Порядок пар сохраняется в соответствии со спецификацией CBOR.
     */
    @Test
    fun `encode and decode map with int keys`() {
        val map = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeText("alpha"),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeInt(100),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeBytes(byteArrayOf(0xBA.toByte(), 0xBE.toByte()))
        ))
        val (decoded, _) = SmpCbor.decode(map)
        val pairs = decoded.asMap()
        assertEquals("alpha", pairs[0].second) // Первый элемент — текст
        assertEquals(100, pairs[1].second) // Второй элемент — число
        assertArrayEquals(byteArrayOf(0xBA.toByte(), 0xBE.toByte()), pairs[2].second as ByteArray) // Третий — байты
    }

    /**
     * Проверяет encode/decode для пустой карты.
     */
    @Test
    fun `encode and decode empty map`() {
        val encoded = SmpCbor.encodeMap(emptyList())
        val (decoded, _) = SmpCbor.decode(encoded)
        assertTrue(decoded.asMap().isEmpty())
    }

    /**
     * Проверяет encode/decode для null-значения по CBOR.
     */
    @Test
    fun `encode and decode null`() {
        val encoded = SmpCbor.encodeNull()
        val (decoded, _) = SmpCbor.decode(encoded)
        assertNull(decoded.value)
    }

    /**
     * Проверяет encode/decode для true.
     */
    @Test
    fun `encode and decode boolean true`() {
        val encoded = SmpCbor.encodeBool(true)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(true, decoded.asBool())
    }

    /**
     * Проверяет encode/decode для false.
     */
    @Test
    fun `encode and decode boolean false`() {
        val encoded = SmpCbor.encodeBool(false)
        val (decoded, _) = SmpCbor.decode(encoded)
        assertEquals(false, decoded.asBool())
    }

    /**
     * Проверяет, что decode пустого массива выбрасывает ArrayIndexOutOfBoundsException.
     */
    @Test(expected = ArrayIndexOutOfBoundsException::class)
    fun `decode of empty byte array throws`() {
        SmpCbor.decode(ByteArray(0))
    }

    /**
     * Проверяет, что decode с невалидным заголовком (0xFF) выбрасывает
     * IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `decode of invalid header throws`() {
        SmpCbor.decode(byteArrayOf(0xFF.toByte()))
    }

    /**
     * Проверяет, что encodeIntKey() создаёт валидный CBOR-ключ,
     * который корректно декодируется обратно в целое число.
     */
    @Test
    fun `encodeIntKey returns CBOR encoded int`() {
        val encoded = SmpCbor.encodeIntKey(5)
        assertTrue(encoded.isNotEmpty())
        assertEquals(5, SmpCbor.decode(encoded).first.asInt())
    }
}
