/**
 * XFTP (SimpleX File Transfer Protocol) implementation for decentralized file transfer.
 *
 * Operates over TCP using CBOR-encoded frames with commands: FNEW, FCHUNK, FGET,
 * FDOWN, FDEL, FINFO, FPING. Supports chunked upload and download of arbitrary files.
 *
 * @see com.example.data.protocol.smp.SmpCbor
 */
package com.paranoidx.sdk.protocol

import com.paranoidx.sdk.protocol.SmpCbor
import java.nio.ByteBuffer

/**
 * Файл: XftpProtocol.kt
 * Пакет: com.example.data.protocol.xftp
 * Назначение: Реализация протокола XFTP (SimpleX File Transfer Protocol)
 * для децентрализованной передачи файлов. Работает поверх TCP, использует CBOR-кодирование
 * для тела фреймов (аналогично SMP, но с другими кодами команд).
 *
 * Команды: FNEW (создать файл), FCHUNK (загрузить чанк), FGET (запросить чанк),
 * FDOWN (скачать), FDEL (удалить), FINFO (информация о файле), FPING (проверка).
 *
 * Размер фрейма: до 256 КБ, размер чанка по умолчанию: 64 КБ, макс. 1024 чанков на файл.
 *
 * @see SmpCbor
 */
object XftpProtocol {
    const val XFTP_PORT = 5274
    const val FRAME_HEADER_SIZE = 6
    const val MAX_FRAME_SIZE = 262144
    const val DEFAULT_CHUNK_SIZE = 65536
    const val MAX_CHUNKS_PER_FILE = 1024
    const val MAX_FILE_SIZE = DEFAULT_CHUNK_SIZE.toLong() * MAX_CHUNKS_PER_FILE
    const val CHUNK_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Коды команд XFTP-протокола.
     */
    object Cmd {
        const val FNEW = 1
        const val FCHUNK = 2
        const val FGET = 3
        const val FDOWN = 4
        const val FDEL = 5
        const val FINFO = 6
        const val FPING = 7

        fun name(code: Int): String = when (code) {
            FNEW -> "FNEW"
            FCHUNK -> "FCHUNK"
            FGET -> "FGET"
            FDOWN -> "FDOWN"
            FDEL -> "FDEL"
            FINFO -> "FINFO"
            FPING -> "FPING"
            else -> "UNKNOWN($code)"
        }
    }

    /**
     * Коды ответов XFTP-протокола.
     */
    object Resp {
        const val FIDS = 1
        const val FDATA = 2
        const val FOK = 3
        const val FERR = 4
        const val FDESC = 5
        const val FPONG = 6

        fun name(code: Int): String = when (code) {
            FIDS -> "FIDS"
            FDATA -> "FDATA"
            FOK -> "FOK"
            FERR -> "FERR"
            FDESC -> "FDESC"
            FPONG -> "FPONG"
            else -> "UNKNOWN($code)"
        }
    }

    object ErrCode {
        const val NOT_FOUND = 1
        const val EXISTS = 2
        const val QUOTA = 3
        const val LARGE_CHUNK = 4
        const val HASH_MISMATCH = 5
        const val INTERNAL = 6
        const val AUTH = 7

        fun name(code: Int): String = when (code) {
            NOT_FOUND -> "NOT_FOUND"
            EXISTS -> "EXISTS"
            QUOTA -> "QUOTA"
            LARGE_CHUNK -> "LARGE_CHUNK"
            HASH_MISMATCH -> "HASH_MISMATCH"
            INTERNAL -> "INTERNAL"
            AUTH -> "AUTH"
            else -> "UNKNOWN($code)"
        }
    }

    /**
     * Информация о файле XFTP.
     * @property fileId уникальный идентификатор файла (32 байта)
     * @property fileName имя файла
     * @property mimeType MIME-тип
     * @property totalSize общий размер файла в байтах
     * @property chunkCount количество чанков
     * @property chunkSize размер каждого чанка
     * @property chunkHashes список SHA-256 хешей всех чанков
     * @property createdAt метка создания
     */
    data class FileInfo(
        val fileId: ByteArray,
        val fileName: String,
        val mimeType: String,
        val totalSize: Long,
        val chunkCount: Int,
        val chunkSize: Int,
        val chunkHashes: List<ByteArray>,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * Информация о чанке файла.
     * @property chunkId идентификатор чанка
     * @property fileId идентификатор файла
     * @property index индекс чанка (0-based)
     * @property size размер чанка в байтах
     * @property hash SHA-256 хеш данных чанка
     * @property createdAt метка создания
     */
    data class ChunkInfo(
        val chunkId: ByteArray,
        val fileId: ByteArray,
        val index: Int,
        val size: Int,
        val hash: ByteArray,
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * Кодирует фрейм XFTP.
     * Формат: [4 байта длина][2 байта код команды][тело]
     * @param commandCode код команды/ответа
     * @param body тело фрейма (CBOR)
     * @return закодированный фрейм
     */
    fun encodeFrame(commandCode: Int, body: ByteArray): ByteArray {
        val buf = ByteBuffer.allocate(FRAME_HEADER_SIZE + body.size)
        buf.putInt(body.size)
        buf.putShort(commandCode.toShort())
        buf.put(body)
        return buf.array()
    }

    /**
     * Декодированный фрейм XFTP.
     */
    data class Frame(val commandCode: Int, val body: ByteArray)

    /**
     * Декодирует фрейм из бинарных данных.
     * @param data сырые байты фрейма
     * @return Frame или null
     */
    fun decodeFrame(data: ByteArray): Frame? {
        if (data.size < FRAME_HEADER_SIZE) return null
        val bodyLen = ((data[0].toInt() and 0xff) shl 24) or
                       ((data[1].toInt() and 0xff) shl 16) or
                       ((data[2].toInt() and 0xff) shl 8) or
                       (data[3].toInt() and 0xff)
        if (bodyLen < 0 || bodyLen > MAX_FRAME_SIZE) return null
        if (data.size < FRAME_HEADER_SIZE + bodyLen) return null
        val cmd = ((data[4].toInt() and 0xff) shl 8) or (data[5].toInt() and 0xff)
        val body = data.copyOfRange(FRAME_HEADER_SIZE, FRAME_HEADER_SIZE + bodyLen)
        return Frame(cmd, body)
    }

    /**
     * Кодирует ответ FIDS (идентификатор чанка + индекс).
     */
    fun encodeFidsResponse(chunkId: ByteArray, chunkIndex: Int): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(chunkId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeInt(chunkIndex)
        ))
        return encodeFrame(Resp.FIDS, body)
    }

    /**
     * Кодирует ответ FDESC (описание файла с информацией о чанках).
     */
    fun encodeFdescResponse(info: FileInfo): ByteArray {
        val hashList = info.chunkHashes.map { SmpCbor.encodeBytes(it) }
        val hashArr = SmpCbor.encodeMap(hashList.mapIndexed { i, h -> SmpCbor.encodeInt(i) to h })
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(info.fileId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeText(info.fileName),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeText(info.mimeType),
            SmpCbor.encodeIntKey(3) to SmpCbor.encodeInt(info.totalSize.toInt()),
            SmpCbor.encodeIntKey(4) to SmpCbor.encodeInt(info.chunkCount),
            SmpCbor.encodeIntKey(5) to SmpCbor.encodeInt(info.chunkSize),
            SmpCbor.encodeIntKey(6) to hashArr
        ))
        return encodeFrame(Resp.FDESC, body)
    }

    /**
     * Кодирует ответ FDATA (данные чанка).
     */
    fun encodeFdataResponse(chunkData: ByteArray): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(chunkData)
        ))
        return encodeFrame(Resp.FDATA, body)
    }

    /**
     * Кодирует ответ FOK (успех).
     */
    fun encodeFokResponse(): ByteArray {
        return encodeFrame(Resp.FOK, SmpCbor.encodeMap(emptyList()))
    }

    /**
     * Кодирует ответ FERR (ошибка) с кодом и сообщением.
     */
    fun encodeFerrResponse(errCode: Int, message: String): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeInt(errCode),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeText(message)
        ))
        return encodeFrame(Resp.FERR, body)
    }

    /**
     * Кодирует ответ FPONG.
     */
    fun encodeFpongResponse(): ByteArray {
        return encodeFrame(Resp.FPONG, SmpCbor.encodeMap(emptyList()))
    }

    /**
     * Разбирает команду FNEW: возвращает (fileId, fileName, mimeType).
     */
    fun parseFnewCommand(body: ByteArray): Triple<ByteArray, String, String>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val fileId = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val fileName = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? String
            val mimeType = pairs.firstOrNull { (k, _) -> (k as? Int) == 2 }?.second as? String
            if (fileId != null && fileName != null && mimeType != null) Triple(fileId, fileName, mimeType) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду FCHUNK: возвращает (fileId, index, data).
     */
    fun parseFchunkCommand(body: ByteArray): Triple<ByteArray, Int, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val fileId = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val index = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? Int
            val data = pairs.firstOrNull { (k, _) -> (k as? Int) == 2 }?.second as? ByteArray
            if (fileId != null && index != null && data != null) Triple(fileId, index, data) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду FGET: возвращает (fileId, index).
     */
    fun parseFgetCommand(body: ByteArray): Pair<ByteArray, Int>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val fileId = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val index = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? Int
            if (fileId != null && index != null) Pair(fileId, index) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду FDEL: возвращает fileId.
     */
    fun parseFdelCommand(body: ByteArray): ByteArray? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду FINFO: возвращает fileId.
     */
    fun parseFinfoCommand(body: ByteArray): ByteArray? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
        } catch (_: Exception) { null }
    }

    /**
     * Генерирует случайный идентификатор файла (32 байта).
     */
    fun generateFileId(): ByteArray {
        val random = java.security.SecureRandom()
        val id = ByteArray(32)
        random.nextBytes(id)
        return id
    }

    /**
     * Вычисляет SHA-256 хеш данных чанка.
     * @param data данные чанка
     * @return 32-байтовый хеш
     */
    fun hashChunk(data: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
