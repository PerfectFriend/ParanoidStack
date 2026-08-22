/**
 * SMP (Simple Messaging Protocol) — the core transport protocol of the SimpleX messenger.
 *
 * Architecture role:
 *   This file defines the binary wire format for all communication between SimpleX clients and servers.
 *   SMP is a binary protocol layered over TLS. Messages are encoded into fixed-size "transport blocks"
 *   (16,384 bytes) that may contain multiple command transmissions. Each transmission carries
 *   an authorization field, correlation ID, entity (queue) ID, and a command body.
 *
 * Key responsibilities:
 *   - Encoding/decoding SMP binary transmissions
 *   - Building and parsing transport blocks (with random padding for traffic analysis resistance)
 *   - Parsing SMP queue URIs (smp://serverIdentity@host:port/queueId#/?v=1&dh=...&k=s)
 *   - Generating correlation IDs and encoding protocol commands (NEW, SEND, SUB, ACK, DEL, PING, KEY, NKEY)
 *
 * The protocol implements a request-response pattern: every client command gets a correlated
 * server response (OK, IDS, MSG, ERR, END, etc.).
 */
/**
 * Реализация SMP (Simple Messaging Protocol) — транспортного протокола SimpleX.
 * Отвечает за кодирование/декодирование сообщений, формирование транспортных блоков,
 * парсинг URI очередей и генерацию идентификаторов.
 *
 * SMP — бинарный протокол поверх TLS, использующий фиксированные транспортные блоки
 * (16384 байт) для передачи команд и данных.
 */
package com.example.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Конфигурация SMP (список серверов) */
data class SMPConfig(
    val servers: List<SMPQueueURI> = SMPProtocol.DEFAULT_SERVERS
)

/**
 * URI очереди SMP в формате:
 * smp://serverIdentity@host:port/queueId#/?v=1&dh=...&k=s
 *
 * @property serverIdentity отпечаток ключа сервера (Base64 URL-safe)
 * @property host хост сервера
 * @property port порт (по умолчанию 5223)
 * @property queueId ID очереди
 * @property dhPublicKey публичный ключ DH (опционально)
 * @property sndSecure флаг безопасности отправки
 */
data class SMPQueueURI(
    val serverIdentity: String,
    val host: String,
    val port: Int = 5223,
    val queueId: ByteArray,
    val dhPublicKey: ByteArray? = null,
    val sndSecure: Boolean = false
) {
    /** Сериализовать URI в строку */
    fun toUri(): String {
        val qid = Base64.getUrlEncoder().withoutPadding().encodeToString(queueId)
        val dh = dhPublicKey?.let {
            "&dh=" + Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        } ?: ""
        val ss = if (sndSecure) "&k=s" else ""
        return "smp://$serverIdentity@$host:$port/$qid#/?v=1$dh$ss"
    }

    /** Получить отпечаток сервера */
    fun toFingerprint(): ByteArray = Base64.getUrlDecoder().decode(serverIdentity)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SMPQueueURI) return false
        return queueId.contentEquals(other.queueId)
    }
    override fun hashCode(): Int = queueId.contentHashCode()
}

/** Объект-синглтон с реализацией протокола SMP */
object SMPProtocol {
    const val TAG = "SMPProtocol"
    const val TRANSPORT_BLOCK_SIZE = 16384   // размер транспортного блока (16 КБ)
    const val MSG_BODY_PADDED_SIZE = 15744   // размер тела сообщения с паддингом
    const val NONCE_SIZE = 24                // размер nonce

    val RANDOM = SecureRandom()

    /** Серверы SimpleX по умолчанию (публичные ноды) */
    val DEFAULT_SERVERS: List<SMPQueueURI> = listOf(
        SMPQueueURI("0YuTwO05YJWS8rkjn9eLJDjQhFKvIYd8d4xG8X1blIU=", "smp8.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("SkIkI6EPd2D63F4xFKfHk7I1UGZVNn6k1QWZ5rcyr6w=", "smp9.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("6iIcWT_dF2zN_w5xzZEY7HI2Prbh3ldP07YTyDexPjE=", "smp10.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("1OwYGt-yqOfe2IyVHhxz3ohqo3aCCMjtB-8wn4X_aoY=", "smp11.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("UkMFNAXLXeAAe0beCa4w6X_zp18PwxSaSjY17BKUGXQ=", "smp12.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("enEkec4hlR3UtKx2NMpOUK_K4ZuDxjWBO1d9Y4YXVaA=", "smp14.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("h--vW7ZSkXPeOUpfxlFGgauQmXNFOzGoizak7Ult7cw=", "smp15.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("hejn2gVIqNU6xjtGM3OwQeuk8ZEbDXVJXAlnSBJBWUA=", "smp16.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("ZKe4uxF4Z_aLJJOEsC-Y6hSkXgQS5-oc442JQGkyP8M=", "smp17.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("PtsqghzQKU83kYTlQ1VKg996dW4Cw4x_bvpKmiv8uns=", "smp18.simplex.im", 5223, ByteArray(0)),
        SMPQueueURI("N_McQS3F9TGoh4ER0QstUf55kGnNSd-wXfNPZ7HukcM=", "smp19.simplex.im", 5223, ByteArray(0))
    )

    /** Трансмиссия SMP — бинарный пакет команды */
    data class SmpTransmission(
        val authorization: ByteArray = ByteArray(0),  // авторизация
        val corrId: ByteArray = ByteArray(0),          // ID корреляции
        val entityId: ByteArray = ByteArray(0),        // ID сущности (очереди)
        val command: ByteArray                         // тело команды
    )

    /** Разобранный ответ сервера */
    data class ParsedResponse(
        val corrId: ByteArray,    // ID корреляции
        val entityId: ByteArray,  // ID сущности
        val command: String,      // команда (MSG, OK, IDS, ERR...)
        val params: ByteArray     // параметры команды
    )

    /**
     * Разобрать SMP URI в структуру SMPQueueURI.
     * Формат: smp://serverIdentity@host:port/queueId#/?v=1&dh=...&k=s
     */
    fun parseQueueUri(uri: String): SMPQueueURI? {
        return try {
            if (!uri.startsWith("smp://")) return null
            val rest = uri.removePrefix("smp://")
            val atIdx = rest.indexOf('@')
            if (atIdx < 0) return null
            val serverIdentity = rest.substring(0, atIdx)
            val afterAt = rest.substring(atIdx + 1)
            val hashIdx = afterAt.indexOf('#')
            val hostPortQid = if (hashIdx >= 0) afterAt.substring(0, hashIdx) else afterAt
            val slashIdx = hostPortQid.indexOf('/')
            if (slashIdx < 0) return null
            val hostPort = hostPortQid.substring(0, slashIdx)
            val qidB64 = hostPortQid.substring(slashIdx + 1)
            var host = hostPort
            var port = 5223
            val colonIdx = hostPort.lastIndexOf(':')
            if (colonIdx >= 0 && hostPort.substring(colonIdx + 1).all { it.isDigit() }) {
                host = hostPort.substring(0, colonIdx)
                port = hostPort.substring(colonIdx + 1).toInt()
            }
            val qid = Base64.getUrlDecoder().decode(qidB64)
            var dhKey: ByteArray? = null
            var sndSecure = false
            if (hashIdx >= 0) {
                val fragment = afterAt.substring(hashIdx + 1)
                val paramsStr = fragment.substringAfter("?", "")
                for (param in paramsStr.split("&")) {
                    when {
                        param.startsWith("dh=") -> {
                            val dhB64 = param.removePrefix("dh=")
                            dhKey = Base64.getUrlDecoder().decode(dhB64)
                        }
                        param == "k=s" -> sndSecure = true
                    }
                }
            }
            SMPQueueURI(serverIdentity, host, port, qid, dhKey, sndSecure)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "parseQueueUri failed: $uri", e)
            null
        }
    }

    /** Сгенерировать случайный ID корреляции (24 байта) */
    fun generateCorrId(): ByteArray {
        val id = ByteArray(NONCE_SIZE)
        RANDOM.nextBytes(id)
        return id
    }

    /** Закодировать короткую строку (до 255 байт) */
    fun encodeShortString(data: ByteArray): ByteArray {
        if (data.size > 255) throw IllegalArgumentException("shortString too long: ${data.size}")
        return byteArrayOf(data.size.toByte()) + data
    }

    /** Закодировать строку с паддингом до фиксированного размера */
    fun encodePaddedString(data: ByteArray, paddedLen: Int): ByteArray {
        if (data.size > paddedLen - 2) throw IllegalArgumentException("data too large for padded string")
        val lenBytes = byteArrayOf((data.size shr 8).toByte(), data.size.toByte())
        val pad = paddedLen - 2 - data.size
        val padding = ByteArray(pad) { '#'.code.toByte() }
        return lenBytes + data + padding
    }

    /** Декодировать строку с паддингом */
    fun decodePaddedString(block: ByteArray, offset: Int): Pair<ByteArray, Int> {
        val len = ((block[offset].toInt() and 0xFF) shl 8) or (block[offset + 1].toInt() and 0xFF)
        val data = block.copyOfRange(offset + 2, offset + 2 + len)
        return Pair(data, offset + 2 + len)
    }

    /**
     * Закодировать трансмиссию SMP.
     * Формат: [auth][sessionId][corrId][entityId][command]
     */
    fun encodeTransmission(auth: ByteArray, corrId: ByteArray, entityId: ByteArray, cmd: ByteArray): ByteArray {
        val sessId = byteArrayOf(0)
        val authEnc = if (auth.isEmpty()) byteArrayOf(0) else encodeShortString(auth)
        val corrEnc = if (corrId.isEmpty()) byteArrayOf(0) else byteArrayOf(0x18) + corrId
        val eidEnc = encodeShortString(entityId)
        return authEnc + sessId + corrEnc + eidEnc + cmd
    }

    /** Закодировать команду SEND с флагами и телом */
    fun encodeSendMessage(msgBody: ByteArray, notification: Boolean = false): ByteArray {
        val flags = if (notification) byteArrayOf(1) else byteArrayOf(0)
        val body = encodePaddedString(msgBody, MSG_BODY_PADDED_SIZE)
        return "SEND ".encodeToByteArray() + flags + body
    }

    /** Закодировать команду NEW (создание очереди) */
    fun encodeNewQueue(rAuthKey: ByteArray, rDhKey: ByteArray, sndSecure: Boolean = false): ByteArray {
        val cmd = StringBuilder("NEW ")
        cmd.append(rAuthKey.size.toChar())
        cmd.append(Base64.getEncoder().withoutPadding().encodeToString(rAuthKey))
        cmd.append(rDhKey.size.toChar())
        cmd.append(Base64.getEncoder().withoutPadding().encodeToString(rDhKey))
        cmd.append("0S") // no basic auth, subscribe
        cmd.append(if (sndSecure) "T" else "F")
        return cmd.toString().encodeToByteArray()
    }

    /** Закодировать SEND в сервер (создаёт трансмиссию) */
    fun encodeSendToServer(senderId: ByteArray, msgBody: ByteArray): ByteArray {
        return encodeTransmission(
            ByteArray(0), // пустая авторизация для начальной отправки
            generateCorrId(),
            senderId,
            encodeSendMessage(msgBody)
        )
    }

    /**
     * Собрать транспортный блок (фиксированного размера 16384 байт).
     * Содержит несколько трансмиссий, остаток заполняется случайными данными.
     */
    fun buildTransportBlock(transmissions: List<ByteArray>): ByteArray {
        val payload = ByteArray(256) // достаточно для заголовка
        var pos = 1 // первый байт — количество трансмиссий
        for (t in transmissions) {
            val len = t.size
            if (pos + 2 + len > TRANSPORT_BLOCK_SIZE) break
            payload[pos++] = (len shr 8).toByte()
            payload[pos++] = len.toByte()
            System.arraycopy(t, 0, payload, pos, len)
            pos += len
        }
        payload[0] = (pos - 1).coerceAtMost(255).toByte()
        val result = ByteArray(TRANSPORT_BLOCK_SIZE)
        System.arraycopy(payload, 0, result, 0, pos)
        // Заполняем остаток случайными байтами (для сокрытия длины)
        val pad = ByteArray(TRANSPORT_BLOCK_SIZE - pos)
        RANDOM.nextBytes(pad)
        System.arraycopy(pad, 0, result, pos, pad.size)
        return result
    }

    /** Результат разбора транспортного блока */
    data class ParseResult(
        val transmissions: List<ParsedResponse>,
        val rawBlock: ByteArray
    )

    /**
     * Разобрать транспортный блок.
     * Извлекает все трансмиссии и парсит каждую.
     */
    fun parseTransportBlock(block: ByteArray): ParseResult {
        val count = block[0].toInt() and 0xFF
        val transmissions = mutableListOf<ParsedResponse>()
        var pos = 1
        repeat(count.coerceAtMost(32)) {
            if (pos + 2 > block.size) return@repeat
            val len = ((block[pos].toInt() and 0xFF) shl 8) or (block[pos + 1].toInt() and 0xFF)
            pos += 2
            if (pos + len > block.size) return@repeat
            val t = block.copyOfRange(pos, pos + len)
            pos += len
            try {
                transmissions.add(parseTransmission(t))
            } catch (e: Exception) {
                android.util.Log.w(TAG, "parse transmission error", e)
            }
        }
        return ParseResult(transmissions, block)
    }

    /**
     * Разобрать одну трансмиссию.
     *
     * Binary format: [authLen:1][auth:authLen][sessLen:1][sess:sessLen][corrLen:1][corr:corrLen][eidLen:1][eid:eidLen][cmd:...]
     * Each length prefix is a single byte (0-255). The command portion is a text string like "SEND ...flags+body"
     * or "SUB", "ACK", etc. If the command contains a space, everything after it is treated as parameters.
     */
    private fun parseTransmission(t: ByteArray): ParsedResponse {
        var pos = 0
        val authLen = t[pos++].toInt() and 0xFF
        if (authLen > 0) pos += authLen
        val sessLen = t[pos++].toInt() and 0xFF
        if (sessLen > 0) pos += sessLen
        val corrLen = t[pos++].toInt() and 0xFF
        val corrId = if (corrLen > 0) {
            t.copyOfRange(pos, pos + corrLen).also { pos += corrLen }
        } else ByteArray(0)
        val eidLen = t[pos++].toInt() and 0xFF
        val entityId = if (eidLen > 0) {
            t.copyOfRange(pos, pos + eidLen).also { pos += eidLen }
        } else ByteArray(0)
        val cmdBytes = t.copyOfRange(pos, t.size)
        val cmdStr = cmdBytes.decodeToString()
        val spaceIdx = cmdStr.indexOf(' ')
        return if (spaceIdx >= 0) {
            ParsedResponse(corrId, entityId, cmdStr.substring(0, spaceIdx), cmdBytes.copyOfRange(spaceIdx + 1, cmdBytes.size))
        } else {
            ParsedResponse(corrId, entityId, cmdStr, ByteArray(0))
        }
    }
}
