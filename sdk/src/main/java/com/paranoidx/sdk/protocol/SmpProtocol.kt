/**
 * SimpleX Messaging Protocol (SMP) implementation.
 *
 * Defines frame encoding/decoding, command formats (NEW, SEND, SUB, ACK, OFF,
 * DEL, NID, PING), response codes (IDS, OK, ERR, MSG, PONG), and protocol
 * constants (default port, frame sizes, queue and message limits).
 *
 * @see SmpQueueStore
 * @see SmpCbor
 */
package com.paranoidx.sdk.protocol

import java.nio.ByteBuffer

/**
 * Файл: SmpProtocol.kt
 * Пакет: com.example.data.protocol.smp
 * Назначение: Реализация протокола SimpleX Messaging Protocol (SMP).
 * Определяет кодирование/декодирование фреймов, форматы команд и ответов,
 * константы протокола (порт, размеры фреймов, лимиты очередей/сообщений).
 *
 * Команды: NEW (создать очередь), SEND (отправить), SUB (подписаться),
 * ACK (подтвердить), OFF (отписаться), DEL (удалить), NID (новый ID), PING.
 * Ответы: IDS, OK, ERR, MSG, PONG.
 *
 * Все фреймы кодируются в бинарном формате:
 * [4 байта длина тела][2 байта код команды][тело (CBOR)].
 *
 * @see SmpCbor
 * @see SmpQueueStore
 */
object SmpProtocol {
    const val SIG_SIZE = 64

    /**
     * Кодирует фрейм SMP-протокола.
     * Формат: [4 байта длина тела][2 байта код команды][тело][подпись (64 байта) — опционально]
     * @param commandCode код команды/ответа
     * @param body тело фрейма (обычно CBOR)
     * @param signKey опциональный закрытый ключ для подписи фрейма
     * @return закодированный фрейм
     */
    fun encodeFrame(commandCode: Int, body: ByteArray, signKey: ByteArray? = null): ByteArray {
        val sig = if (signKey != null) {
            com.paranoidx.sdk.security.SmpKeyManager.sign(cmdBytes(commandCode) + body, signKey) ?: ByteArray(0)
        } else ByteArray(0)
        val frameBody = if (sig.size == SIG_SIZE) body + sig else body
        val buf = ByteBuffer.allocate(FRAME_HEADER_SIZE + frameBody.size)
        buf.putInt(frameBody.size)
        buf.putShort(commandCode.toShort())
        buf.put(frameBody)
        return buf.array()
    }

    /**
     * Проверяет Ed25519 подпись фрейма.
     * @param frame закодированный фрейм
     * @param pubKey открытый ключ для проверки
     * @return true, если подпись валидна
     */
    fun verifyFrameSignature(frame: ByteArray, pubKey: ByteArray): Boolean {
        if (frame.size < FRAME_HEADER_SIZE + 2) return false
        val bodyLen = ((frame[0].toInt() and 0xff) shl 24) or
                       ((frame[1].toInt() and 0xff) shl 16) or
                       ((frame[2].toInt() and 0xff) shl 8) or
                       (frame[3].toInt() and 0xff)
        if (bodyLen < SIG_SIZE) return false
        val cmd = ((frame[4].toInt() and 0xff) shl 8) or (frame[5].toInt() and 0xff)
        val bodyEnd = FRAME_HEADER_SIZE + bodyLen
        val sig = frame.copyOfRange(bodyEnd - SIG_SIZE, bodyEnd)
        val payload = cmdBytes(cmd) + frame.copyOfRange(FRAME_HEADER_SIZE, bodyEnd - SIG_SIZE)
        return com.paranoidx.sdk.security.SmpKeyManager.verify(payload, sig, pubKey)
    }

    /**
     * Проверяет, содержит ли фрейм подпись.
     */
    fun hasSignature(frame: ByteArray): Boolean {
        if (frame.size < FRAME_HEADER_SIZE + SIG_SIZE) return false
        val bodyLen = ((frame[0].toInt() and 0xff) shl 24) or
                       ((frame[1].toInt() and 0xff) shl 16) or
                       ((frame[2].toInt() and 0xff) shl 8) or
                       (frame[3].toInt() and 0xff)
        return bodyLen >= SIG_SIZE
    }

    private fun cmdBytes(cmd: Int): ByteArray = byteArrayOf((cmd shr 8).toByte(), cmd.toByte())
    const val SMP_PORT = 5273
    const val FRAME_HEADER_SIZE = 6
    const val MAX_FRAME_SIZE = 65536
    const val MAX_QUEUES = 10000
    const val MAX_MESSAGES_PER_QUEUE = 1000
    const val MESSAGE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L

    /**
     * Коды команд SMP-протокола.
     * Используются в поле commandCode фрейма при отправке.
     */
    object Cmd {
        const val NEW = 1
        const val SEND = 2
        const val SUB = 3
        const val ACK = 4
        const val OFF = 5
        const val DEL = 6
        const val NID = 7
        const val PING = 8

        fun name(code: Int): String = when (code) {
            NEW -> "NEW"
            SEND -> "SEND"
            SUB -> "SUB"
            ACK -> "ACK"
            OFF -> "OFF"
            DEL -> "DEL"
            NID -> "NID"
            PING -> "PING"
            else -> "UNKNOWN($code)"
        }
    }

    @Deprecated("Use Cmd", ReplaceWith("Cmd"))
    val CmdCode = Cmd

    /**
     * Коды ответов SMP-протокола.
     * Используются в поле commandCode фрейма при ответе сервера.
     */
    object Resp {
        const val IDS = 1
        const val OK = 2
        const val ERR = 3
        const val MSG = 4
        const val PONG = 5

        fun name(code: Int): String = when (code) {
            IDS -> "IDS"
            OK -> "OK"
            ERR -> "ERR"
            MSG -> "MSG"
            PONG -> "PONG"
            else -> "UNKNOWN($code)"
        }
    }

    @Deprecated("Use Resp", ReplaceWith("Resp"))
    val RespCode = Resp

    /**
     * Коды ошибок SMP-протокола.
     */
    object ErrCode {
        const val BLOCKED = 1
        const val NOT_FOUND = 2
        const val EXISTS = 3
        const val QUOTA = 4
        const val LARGE_MSG = 5
        const val NO_MSG = 6
        const val INTERNAL = 7
        const val AUTH = 8
        const val DUPLICATE = 9

        fun name(code: Int): String = when (code) {
            BLOCKED -> "BLOCKED"
            NOT_FOUND -> "NOT_FOUND"
            EXISTS -> "EXISTS"
            QUOTA -> "QUOTA"
            LARGE_MSG -> "LARGE_MSG"
            NO_MSG -> "NO_MSG"
            INTERNAL -> "INTERNAL"
            AUTH -> "AUTH"
            DUPLICATE -> "DUPLICATE"
            else -> "UNKNOWN($code)"
        }
    }

    /**
     * Модель декодированного фрейма SMP.
     * @property commandCode код команды/ответа
     * @property body тело фрейма
     */
    data class Frame(val commandCode: Int, val body: ByteArray)

    /**
     * Декодирует фрейм из бинарных данных.
     * @param data сырые байты фрейма
     * @return Frame или null при некорректных данных
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
     * Кодирует ответ IDS (идентификаторы очереди и хеш ключа).
     */
    fun encodeIdsResponse(queueId: ByteArray, sndKeyHash: ByteArray): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(queueId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeBytes(sndKeyHash)
        ))
        return encodeFrame(Resp.IDS, body)
    }

    /**
     * Кодирует ответ OK (успех).
     */
    fun encodeOkResponse(): ByteArray {
        val body = SmpCbor.encodeMap(emptyList())
        return encodeFrame(Resp.OK, body)
    }

    /**
     * Кодирует ответ PONG.
     */
    fun encodePongResponse(): ByteArray {
        val body = SmpCbor.encodeMap(emptyList())
        return encodeFrame(Resp.PONG, body)
    }

    /**
     * Кодирует ответ ERR (ошибка) с кодом и сообщением.
     */
    fun encodeErrorResponse(errCode: Int, message: String): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeInt(errCode),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeText(message)
        ))
        return encodeFrame(Resp.ERR, body)
    }

    /**
     * Кодирует ответ MSG (новое сообщение).
     */
    fun encodeMessageResponse(queueId: ByteArray, msgId: ByteArray, timestamp: Long, body: ByteArray): ByteArray {
        val tsHigh = (timestamp shr 32).toInt()
        val tsLow = (timestamp and 0xffffffff).toInt()
        val bodyEnc = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(queueId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeBytes(msgId),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeInt(tsHigh) + SmpCbor.encodeInt(tsLow),
            SmpCbor.encodeIntKey(3) to SmpCbor.encodeBytes(body)
        ))
        return encodeFrame(Resp.MSG, bodyEnc)
    }

    /**
     * Кодирует команду NEW (создать очередь).
     * @param rcptKey открытый ключ получателя
     * @param sndKey открытый ключ отправителя
     */
    fun encodeNewQueue(rcptKey: ByteArray, sndKey: ByteArray): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(rcptKey),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeBytes(sndKey)
        ))
        return encodeFrame(Cmd.NEW, body)
    }

    /**
     * Кодирует команду SEND (отправить сообщение).
     * @param queueId идентификатор очереди
     * @param msgBody тело сообщения
     * @param flags флаги сообщения
     */
    fun encodeSendMessage(queueId: ByteArray, msgBody: ByteArray, flags: Int = 0): ByteArray {
        val body = SmpCbor.encodeMap(listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeBytes(queueId),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeBytes(msgBody),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeInt(flags)
        ))
        return encodeFrame(Cmd.SEND, body)
    }

    /**
     * Разбирает ответ IDS: возвращает пару (queueId, sndKeyHash).
     */
    fun parseIdsResponse(body: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val qid = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val key = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            if (qid != null && key != null) Pair(qid, key) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду NEW: возвращает пару (recipientKey, senderKey).
     */
    fun parseNewCommand(body: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val rcpt = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val snd = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            if (rcpt != null && snd != null) Pair(rcpt, snd) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду SEND: возвращает тройку (queueId, msgBody, flags).
     */
    fun parseSendCommand(body: ByteArray): Triple<ByteArray, ByteArray, Int>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val qid = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val msg = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            val flags = pairs.firstOrNull { (k, _) -> (k as? Int) == 2 }?.second as? Int
            if (qid != null && msg != null && flags != null) Triple(qid, msg, flags) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду SUB: возвращает пару (queueId, key).
     */
    fun parseSubCommand(body: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val qid = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val key = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            if (qid != null && key != null) Pair(qid, key) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команду ACK: возвращает пару (queueId, msgId).
     */
    fun parseAckCommand(body: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val qid = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val mid = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            if (qid != null && mid != null) Pair(qid, mid) else null
        } catch (_: Exception) { null }
    }

    /**
     * Разбирает команды OFF (отписка) и DEL (удаление): возвращает (queueId, key).
     */
    fun parseOffDelCommand(body: ByteArray): Pair<ByteArray, ByteArray>? {
        return try {
            val (cbor, _) = SmpCbor.decode(body)
            val pairs = cbor.asMap()
            val qid = pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? ByteArray
            val key = pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? ByteArray
            if (qid != null && key != null) Pair(qid, key) else null
        } catch (_: Exception) { null }
    }

    /**
     * Генерирует идентификатор очереди как SHA-256 от открытого ключа получателя.
     * @param recipientKey открытый ключ получателя
     * @return 32-байтовый идентификатор очереди
     */
    fun generateQueueId(recipientKey: ByteArray): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(recipientKey)
    }

    /**
     * Генерирует случайный идентификатор сообщения (32 байта).
     */
    fun generateMessageId(): ByteArray {
        val random = java.security.SecureRandom()
        val id = ByteArray(32)
        random.nextBytes(id)
        return id
    }

    /**
     * Проверяет, совпадают ли ключ аутентификации и ключ очереди.
     */
    fun verifyKey(authKey: ByteArray, queueKey: ByteArray): Boolean {
        return java.util.Arrays.equals(authKey, queueKey)
    }
}
