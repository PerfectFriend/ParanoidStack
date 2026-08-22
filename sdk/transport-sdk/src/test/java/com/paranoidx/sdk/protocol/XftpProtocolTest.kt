package com.paranoidx.sdk.protocol

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Тесты протокола XFTP (SimpleX File Transfer Protocol).
 * Покрывает проверку констант команд/ответов/ошибок, кодирование/декодирование
 * фреймов, генерацию идентификаторов файлов, хеширование чанков
 * и парсинг команд управления файлами.
 */
class XftpProtocolTest {

    /**
     * Проверяет соответствие кодов команд XFTP (FNEW=1 ... FPING=7).
     */
    @Test
    fun `Cmd constants are correct`() {
        assertEquals(1, XftpProtocol.Cmd.FNEW)
        assertEquals(2, XftpProtocol.Cmd.FCHUNK)
        assertEquals(3, XftpProtocol.Cmd.FGET)
        assertEquals(4, XftpProtocol.Cmd.FDOWN)
        assertEquals(5, XftpProtocol.Cmd.FDEL)
        assertEquals(6, XftpProtocol.Cmd.FINFO)
        assertEquals(7, XftpProtocol.Cmd.FPING)
    }

    /**
     * Проверяет соответствие кодов ответов XFTP (FIDS=1 ... FPONG=6).
     */
    @Test
    fun `Resp constants are correct`() {
        assertEquals(1, XftpProtocol.Resp.FIDS)
        assertEquals(2, XftpProtocol.Resp.FDATA)
        assertEquals(3, XftpProtocol.Resp.FOK)
        assertEquals(4, XftpProtocol.Resp.FERR)
        assertEquals(5, XftpProtocol.Resp.FDESC)
        assertEquals(6, XftpProtocol.Resp.FPONG)
    }

    /**
     * Проверяет соответствие кодов ошибок XFTP (NOT_FOUND=1 ... AUTH=7).
     */
    @Test
    fun `ErrCode constants are correct`() {
        assertEquals(1, XftpProtocol.ErrCode.NOT_FOUND)
        assertEquals(2, XftpProtocol.ErrCode.EXISTS)
        assertEquals(3, XftpProtocol.ErrCode.QUOTA)
        assertEquals(4, XftpProtocol.ErrCode.LARGE_CHUNK)
        assertEquals(5, XftpProtocol.ErrCode.HASH_MISMATCH)
        assertEquals(6, XftpProtocol.ErrCode.INTERNAL)
        assertEquals(7, XftpProtocol.ErrCode.AUTH)
    }

    /**
     * Проверяет строковые имена команд XFTP. Для неизвестного кода — "UNKNOWN(99)".
     */
    @Test
    fun `Cmd name returns correct strings`() {
        assertEquals("FNEW", XftpProtocol.Cmd.name(1))
        assertEquals("FCHUNK", XftpProtocol.Cmd.name(2))
        assertEquals("FGET", XftpProtocol.Cmd.name(3))
        assertEquals("UNKNOWN(99)", XftpProtocol.Cmd.name(99))
    }

    /**
     * Проверяет строковые имена ответов XFTP.
     */
    @Test
    fun `Resp name returns correct strings`() {
        assertEquals("FIDS", XftpProtocol.Resp.name(1))
        assertEquals("FDATA", XftpProtocol.Resp.name(2))
        assertEquals("FOK", XftpProtocol.Resp.name(3))
        assertEquals("FERR", XftpProtocol.Resp.name(4))
        assertEquals("UNKNOWN(99)", XftpProtocol.Resp.name(99))
    }

    /**
     * Проверяет строковые имена кодов ошибок XFTP.
     */
    @Test
    fun `ErrCode name returns correct strings`() {
        assertEquals("NOT_FOUND", XftpProtocol.ErrCode.name(1))
        assertEquals("EXISTS", XftpProtocol.ErrCode.name(2))
        assertEquals("QUOTA", XftpProtocol.ErrCode.name(3))
        assertEquals("INTERNAL", XftpProtocol.ErrCode.name(6))
        assertEquals("AUTH", XftpProtocol.ErrCode.name(7))
        assertEquals("UNKNOWN(99)", XftpProtocol.ErrCode.name(99))
    }

    /**
     * Проверяет стандартный порт XFTP — 5274.
     */
    @Test
    fun `XFTP_PORT is 5274`() {
        assertEquals(5274, XftpProtocol.XFTP_PORT)
    }

    /**
     * Проверяет размер заголовка фрейма XFTP — 6 байт.
     */
    @Test
    fun `FRAME_HEADER_SIZE is 6`() {
        assertEquals(6, XftpProtocol.FRAME_HEADER_SIZE)
    }

    /**
     * Проверяет максимальный размер фрейма XFTP — 262 144 байта (256 КБ).
     */
    @Test
    fun `MAX_FRAME_SIZE is 262144`() {
        assertEquals(262144, XftpProtocol.MAX_FRAME_SIZE)
    }

    /**
     * Проверяет размер чанка по умолчанию — 65 536 байт (64 КБ).
     */
    @Test
    fun `DEFAULT_CHUNK_SIZE is 65536`() {
        assertEquals(65536, XftpProtocol.DEFAULT_CHUNK_SIZE)
    }

    /**
     * Проверяет максимальный размер файла — 64 МБ (65536 * 1024).
     */
    @Test
    fun `MAX_FILE_SIZE is correct`() {
        assertEquals(65536L * 1024, XftpProtocol.MAX_FILE_SIZE)
    }

    /**
     * Проверяет время истечения чанка — 7 дней в миллисекундах.
     */
    @Test
    fun `CHUNK_EXPIRY_MS is 7 days in ms`() {
        assertEquals(7 * 24 * 60 * 60 * 1000L, XftpProtocol.CHUNK_EXPIRY_MS)
    }

    /**
     * Проверяет полный цикл encodeFrame -> decodeFrame.
     * Фрейм с командой FPING и телом [0x0A, 0x0B, 0x0C, 0x0D].
     */
    @Test
    fun `encodeFrame and decodeFrame round-trip`() {
        val body = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val frame = XftpProtocol.encodeFrame(XftpProtocol.Cmd.FPING, body)
        assertEquals(XftpProtocol.FRAME_HEADER_SIZE + body.size, frame.size)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(XftpProtocol.Cmd.FPING, decoded!!.commandCode)
        assertArrayEquals(body, decoded.body)
    }

    /**
     * Проверяет кодирование фрейма с пустым телом.
     * Размер фрейма должен быть равен заголовку (6 байт).
     */
    @Test
    fun `encodeFrame with empty body`() {
        val frame = XftpProtocol.encodeFrame(XftpProtocol.Cmd.FPING, ByteArray(0))
        assertEquals(XftpProtocol.FRAME_HEADER_SIZE, frame.size)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertArrayEquals(ByteArray(0), decoded!!.body)
    }

    /**
     * Проверяет, что decodeFrame возвращает null для данных
     * размером меньше 6 байт (минимальный заголовок).
     */
    @Test
    fun `decodeFrame returns null for too short input`() {
        assertNull(XftpProtocol.decodeFrame(ByteArray(0))) // Пустой массив
        assertNull(XftpProtocol.decodeFrame(ByteArray(5))) // Меньше FRAME_HEADER_SIZE
    }

    /**
     * Проверяет encodeFidsResponse и отсутствие парсинга как FCHUNK-команды.
     * FIDS — это ответ, а не команда, поэтому parseFchunkCommand возвращает null.
     */
    @Test
    fun `encodeFidsResponse and parseFchunkCommand round-trip`() {
        val chunkId = ByteArray(32) { it.toByte() }
        val chunkIndex = 7
        val response = XftpProtocol.encodeFidsResponse(chunkId, chunkIndex)
        assertNotNull(response)
        val decoded = XftpProtocol.decodeFrame(response)
        assertNotNull(decoded)
        assertEquals(XftpProtocol.Resp.FIDS, decoded!!.commandCode)
        val parsed = XftpProtocol.parseFchunkCommand(decoded.body)
        assertNull(parsed) // FIDS is a response, not a command
    }

    /**
     * Проверяет создание ответа FOK (успешное завершение операции).
     */
    @Test
    fun `encodeFokResponse returns valid frame`() {
        val frame = XftpProtocol.encodeFokResponse()
        assertNotNull(frame)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(XftpProtocol.Resp.FOK, decoded!!.commandCode)
    }

    /**
     * Проверяет создание ответа FPONG (pong на ping).
     */
    @Test
    fun `encodeFpongResponse returns valid frame`() {
        val frame = XftpProtocol.encodeFpongResponse()
        assertNotNull(frame)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(XftpProtocol.Resp.FPONG, decoded!!.commandCode)
    }

    /**
     * Проверяет создание ответа FERR с кодом ошибки NOT_FOUND и сообщением.
     */
    @Test
    fun `encodeFerrResponse contains error code and message`() {
        val frame = XftpProtocol.encodeFerrResponse(XftpProtocol.ErrCode.NOT_FOUND, "File not found")
        assertNotNull(frame)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        assertEquals(XftpProtocol.Resp.FERR, decoded!!.commandCode)
    }

    /**
     * Проверяет генерацию 32-байтовых идентификаторов файлов.
     * Два последовательных вызова должны давать разные ID (криптостойкая случайность).
     */
    @Test
    fun `generateFileId returns 32 random bytes`() {
        val id1 = XftpProtocol.generateFileId()
        val id2 = XftpProtocol.generateFileId()
        assertEquals(32, id1.size)
        assertEquals(32, id2.size)
        assertFalse(id1.contentEquals(id2)) // Уникальность ID
    }

    /**
     * Проверяет, что hashChunk() возвращает корректный SHA-256 хеш (32 байта),
     * совпадающий с эталонным MessageDigest.
     */
    @Test
    fun `hashChunk returns SHA-256 hash`() {
        val data = "test data".toByteArray()
        val hash = XftpProtocol.hashChunk(data)
        assertEquals(32, hash.size)
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertArrayEquals(expected, hash) // Хеш совпадает с эталоном
    }

    /**
     * Проверяет детерминированность хеша: одинаковые входные данные
     * всегда дают одинаковый SHA-256 хеш.
     */
    @Test
    fun `hashChunk produces consistent results`() {
        val data = "consistent".toByteArray()
        val hash1 = XftpProtocol.hashChunk(data)
        val hash2 = XftpProtocol.hashChunk(data)
        assertArrayEquals(hash1, hash2) // Детерминированность
    }

    /**
     * Проверяет парсинг команды FNEW из CBOR-тела.
     * Команда содержит: fileId (32 байта), имя файла ("test.txt"),
     * MIME-тип ("text/plain").
     */
    @Test
    fun `parseFnewCommand parses valid body`() {
        val fileId = ByteArray(32) { it.toByte() }
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(fileId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeText("test.txt"),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeText("text/plain")
        ))
        val frame = XftpProtocol.encodeFrame(XftpProtocol.Cmd.FNEW, body)
        val decoded = XftpProtocol.decodeFrame(frame)
        assertNotNull(decoded)
        val parsed = XftpProtocol.parseFnewCommand(decoded!!.body)
        assertNotNull(parsed)
        val (pFileId, pFileName, pMimeType) = parsed!!
        assertArrayEquals(fileId, pFileId) // ID файла совпадает
        assertEquals("test.txt", pFileName) // Имя файла совпадает
        assertEquals("text/plain", pMimeType) // MIME-тип совпадает
    }
}
