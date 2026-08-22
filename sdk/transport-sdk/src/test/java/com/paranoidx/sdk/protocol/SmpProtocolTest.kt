package com.paranoidx.sdk.protocol

import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты протокола SMP (Simple Message Protocol).
 * Проверяет корректность кодов команд/ответов/ошибок, кодирование/декодирование
 * фреймов, создание очередей, отправку сообщений и парсинг CBOR-ответов.
 */
class SmpProtocolTest {

    /**
     * Проверяет соответствие кодов команд протокола SMP (NEW=1 ... PING=8).
     * Любое изменение этих констант нарушит совместимость с сервером.
     */
    @Test
    fun `Cmd constants are correct`() {
        assertEquals(1, SmpProtocol.Cmd.NEW)
        assertEquals(2, SmpProtocol.Cmd.SEND)
        assertEquals(3, SmpProtocol.Cmd.SUB)
        assertEquals(4, SmpProtocol.Cmd.ACK)
        assertEquals(5, SmpProtocol.Cmd.OFF)
        assertEquals(6, SmpProtocol.Cmd.DEL)
        assertEquals(7, SmpProtocol.Cmd.NID)
        assertEquals(8, SmpProtocol.Cmd.PING)
    }

    /**
     * Проверяет соответствие кодов ответов сервера SMP (IDS=1 ... PONG=5).
     */
    @Test
    fun `Resp constants are correct`() {
        assertEquals(1, SmpProtocol.Resp.IDS)
        assertEquals(2, SmpProtocol.Resp.OK)
        assertEquals(3, SmpProtocol.Resp.ERR)
        assertEquals(4, SmpProtocol.Resp.MSG)
        assertEquals(5, SmpProtocol.Resp.PONG)
    }

    /**
     * Проверяет, что Cmd.name() возвращает строковое представление команды.
     * Для неизвестного кода возвращается "UNKNOWN(99)".
     */
    @Test
    fun `Cmd name returns correct strings`() {
        assertEquals("NEW", SmpProtocol.Cmd.name(1))
        assertEquals("SEND", SmpProtocol.Cmd.name(2))
        assertEquals("PING", SmpProtocol.Cmd.name(8))
        assertEquals("UNKNOWN(99)", SmpProtocol.Cmd.name(99))
    }

    /**
     * Проверяет строковые имена ответов сервера через Resp.name().
     */
    @Test
    fun `Resp name returns correct strings`() {
        assertEquals("IDS", SmpProtocol.Resp.name(1))
        assertEquals("OK", SmpProtocol.Resp.name(2))
        assertEquals("ERR", SmpProtocol.Resp.name(3))
        assertEquals("MSG", SmpProtocol.Resp.name(4))
        assertEquals("PONG", SmpProtocol.Resp.name(5))
        assertEquals("UNKNOWN(99)", SmpProtocol.Resp.name(99))
    }

    /**
     * Проверяет строковые имена кодов ошибок SMP (BLOCKED, NOT_FOUND, EXISTS).
     */
    @Test
    fun `ErrCode name returns correct strings`() {
        assertEquals("BLOCKED", SmpProtocol.ErrCode.name(1))
        assertEquals("NOT_FOUND", SmpProtocol.ErrCode.name(2))
        assertEquals("EXISTS", SmpProtocol.ErrCode.name(3))
        assertEquals("UNKNOWN(99)", SmpProtocol.ErrCode.name(99))
    }

    /**
     * Проверяет значение стандартного порта SMP — 5273.
     */
    @Test
    fun `SMP_PORT is 5273`() {
        assertEquals(5273, SmpProtocol.SMP_PORT)
    }

    /**
     * Проверяет размер заголовка фрейма SMP — 6 байт.
     */
    @Test
    fun `FRAME_HEADER_SIZE is positive`() {
        assertEquals(6, SmpProtocol.FRAME_HEADER_SIZE)
    }

    /**
     * Проверяет размер цифровой подписи — 64 байта (Ed25519).
     */
    @Test
    fun `SIG_SIZE is 64`() {
        assertEquals(64, SmpProtocol.SIG_SIZE)
    }

    /**
     * Проверяет полный цикл encodeFrame -> decodeFrame.
     * Закодированный фрейм должен иметь размер (заголовок + тело),
     * а декодированный — содержать исходную команду и тело.
     */
    @Test
    fun `encodeFrame produces correct format`() {
        val body = byteArrayOf(0x01, 0x02, 0x03)
        val frame = SmpProtocol.encodeFrame(SmpProtocol.Cmd.PING, body)
        assertEquals(SmpProtocol.FRAME_HEADER_SIZE + body.size, frame.size)
        val decoded = SmpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(SmpProtocol.Cmd.PING, decoded!!.commandCode) // Команда сохранена
        assertArrayEquals(body, decoded.body) // Тело сохранено
    }

    /**
     * Проверяет кодирование фрейма с пустым телом.
     * Размер фрейма должен быть равен размеру заголовка.
     */
    @Test
    fun `encodeFrame with empty body`() {
        val frame = SmpProtocol.encodeFrame(SmpProtocol.Cmd.PING, ByteArray(0))
        assertEquals(SmpProtocol.FRAME_HEADER_SIZE, frame.size)
        val decoded = SmpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(SmpProtocol.Cmd.PING, decoded!!.commandCode)
        assertArrayEquals(ByteArray(0), decoded.body)
    }

    /**
     * Проверяет, что decodeFrame возвращает null для слишком коротких входных
     * данных (меньше минимального размера заголовка).
     */
    @Test
    fun `decodeFrame returns null for too short input`() {
        assertNull(SmpProtocol.decodeFrame(ByteArray(0))) // Пустой массив
        assertNull(SmpProtocol.decodeFrame(ByteArray(5))) // Меньше 6 байт (FRAME_HEADER_SIZE)
    }

    /**
     * Проверяет, что encodeSendMessage создаёт валидный фрейм
     * с идентификатором очереди (8 байт) и телом сообщения.
     */
    @Test
    fun `encodeSendMessage produces valid frame`() {
        val queueId = ByteArray(8) { it.toByte() }
        val body = "Hello".toByteArray()
        val result = SmpProtocol.encodeSendMessage(queueId, body)
        assertTrue(result.isNotEmpty()) // Фрейм сформирован
    }

    /**
     * Проверяет создание фрейма новой очереди с ключами
     * получателя (32 байта) и отправителя (32 байта).
     */
    @Test
    fun `encodeNewQueue produces valid frame`() {
        val recipientKey = ByteArray(32) { it.toByte() }
        val senderKey = ByteArray(32) { (it + 1).toByte() }
        val result = SmpProtocol.encodeNewQueue(recipientKey, senderKey)
        assertTrue(result.isNotEmpty())
    }

    /**
     * Проверяет парсинг CBOR-тела ответа IDS (содержит queueId и ключ).
     * Парсер должен извлечь queueId (ключ 0) и ключ (ключ 1).
     */
    @Test
    fun `parseIdsResponse parses valid CBOR body`() {
        val queueId = ByteArray(8) { it.toByte() }
        val key = ByteArray(32) { (it + 0x10).toByte() }
        val cborBody = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(queueId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeBytes(key)
        ))
        val result = SmpProtocol.parseIdsResponse(cborBody)
        assertNotNull(result)
        val (parsedId, parsedKey) = result!!
        assertArrayEquals(queueId, parsedId) // queueId извлечён
        assertArrayEquals(key, parsedKey) // ключ извлечён
    }

    /**
     * Проверяет, что parseIdsResponse возвращает null для
     * некорректного CBOR (например, мусорных байт).
     */
    @Test
    fun `parseIdsResponse returns null for invalid CBOR`() {
        val result = SmpProtocol.parseIdsResponse(byteArrayOf(0x01, 0x02))
        assertNull(result)
    }

    /**
     * Проверяет, что hasSignature возвращает false для фреймов
     * размером меньше SIG_SIZE (64 байта).
     */
    @Test
    fun `hasSignature returns false for small frames`() {
        assertFalse(SmpProtocol.hasSignature(ByteArray(0))) // Пустой фрейм
        assertFalse(SmpProtocol.hasSignature(ByteArray(10))) // Меньше 64 байт
    }
}
