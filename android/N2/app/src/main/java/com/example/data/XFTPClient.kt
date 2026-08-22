/**
 * XFTP-клиент для передачи файлов через SimpleX FTP протокол.
 * Поддерживает регистрацию чанков, загрузку, скачивание и удаление.
 * Использует TLS-соединение с проверкой отпечатка сервера (SPKI pinning).
 */
package com.example.data

import android.util.Base64
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.concurrent.thread

/** Конфигурация XFTP-сервера */
data class XFTPServer(
    val serverIdentity: String,  // отпечаток ключа сервера (Base64 URL-safe)
    val host: String,            // хост сервера
    val port: Int = 443          // порт (обычно 443)
)

/** Клиент для протокола XFTP (передача файлов) */
class XFTPClient(
    val server: XFTPServer,
    private val onError: (Exception) -> Unit = {}
) {
    private var socket: SSLSocket? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null
    private var running = false
    private val tag = "XFTP-${server.host.take(16)}"
    private val lock = Object()
    private val pending = mutableMapOf<String, SMPProtocol.ParsedResponse>()
    private var readerThread: Thread? = null
    private val keyStore = mutableMapOf<String, ByteArray>()

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed != true

    /** Подключиться к XFTP-серверу через TLS с проверкой сертификата */
    fun connect(): Boolean {
        return try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(certs: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(certs: Array<out X509Certificate>?, authType: String?) {
                    if (certs.isNullOrEmpty()) throw SSLException("no certs")
                    val spki = certs[0].publicKey.encoded
                    val digest = MessageDigest.getInstance("SHA-256").digest(spki)
                    val expected = Base64.decode(server.serverIdentity, Base64.URL_SAFE)
                    if (!digest.contentEquals(expected))
                        throw SSLException("SPKI mismatch") // проверка отпечатка
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }), SecureRandom())
            val raw = ctx.socketFactory.createSocket(server.host, server.port)
            val ssl = raw as? SSLSocket
            if (ssl == null) { raw.close(); return false }
            try {
                ssl.useClientMode = true
                ssl.startHandshake()
                socket = ssl
                out = ssl.getOutputStream()
                inp = ssl.getInputStream()
            } catch (e: Exception) {
                ssl.close()
                throw e
            }
            running = true
            startReader()
            Log.i(tag, "Connected")
            true
        } catch (e: Exception) {
            Log.e(tag, "connect: ${e.message}"); onError(e); false
        }
    }

    /** Отключиться от сервера */
    fun disconnect() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        try { socket?.close() } catch (_: java.lang.Exception) { Log.w("XFTPClient", "ignored exception") }
        socket = null; inp = null; out = null; readerThread = null
    }

    /**
     * Информация о чанке (размер и SHA-256 дайджест).
     * Used to verify integrity of uploaded/downloaded file chunks.
     */
    class ChunkInfo(
        val size: Int,
        val digest: ByteArray
    ) {
        val digestB64: String get() = Base64.encodeToString(digest, Base64.NO_PADDING or Base64.URL_SAFE)
    }

    /** Результат регистрации чанка */
    data class ChunkUploadResult(
        val senderId: ByteArray,
        val recipientIds: List<ByteArray>
    )

    /**
     * Зарегистрировать новый чанк на сервере.
     * @param sndKey ключ отправителя
     * @param rcvKeys ключи получателей
     * @param size размер данных
     * @param digest SHA-256 дайджест данных
     * @param basicAuth базовая аутентификация
     * @return результат регистрации или null
     */
    /**
     * Зарегистрировать новый чанк на сервере.
     * Sends an FNEW command with the chunk metadata as a binary body:
     *   [sndKeyLen:1][sndKey][sizeStrLen:1][sizeStr][digestLen:1][digest][numRcvKeys:1][rcvKey1Len:1][rcvKey1]...[authFlag:1][authData?]
     * Server responds with SIDS containing the sender ID and recipient IDs.
     */
    fun registerChunk(sndKey: ByteArray, rcvKeys: List<ByteArray>, size: Int, digest: ByteArray, basicAuth: String = ""): ChunkUploadResult? {
        var body = byteArrayOf(sndKey.size.toByte()) + sndKey
        val sizeStr = size.toString().encodeToByteArray()
        body += byteArrayOf(sizeStr.size.toByte()) + sizeStr
        body += byteArrayOf(digest.size.toByte()) + digest
        body += byteArrayOf(rcvKeys.size.toByte())
        for (k in rcvKeys) body += byteArrayOf(k.size.toByte()) + k
        if (basicAuth.isEmpty()) body += byteArrayOf('0'.code.toByte())
        else body += byteArrayOf('1'.code.toByte()) + byteArrayOf(basicAuth.length.toByte()) + basicAuth.encodeToByteArray()
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, ByteArray(0), "FNEW ".encodeToByteArray() + body)
        val resp = waitForResponse(corrId)
        if (resp?.command != "SIDS") return null
        // Parse SIDS response: [senderIdLen:1][senderId][numRecipients:1][recIdLen:1][recId]...
        val d = resp.params; var p = 0
        val sl = d[p++].toInt() and 0xFF; val sid = d.copyOfRange(p, p + sl); p += sl
        val rl = d[p++].toInt() and 0xFF
        val rids = mutableListOf<ByteArray>()
        for (i in 0 until rl) { val rl2 = d[p++].toInt() and 0xFF; rids.add(d.copyOfRange(p, p + rl2)); p += rl2 }
        return ChunkUploadResult(sid, rids)
    }

    /** Загрузить данные чанка на сервер */
    fun uploadChunk(senderId: ByteArray, data: ByteArray): Boolean {
        val corrId = SMPProtocol.generateCorrId()
        val cmd = "FPUT".encodeToByteArray()
        sendTransmission(corrId, senderId, cmd)
        val block = SMPProtocol.buildTransportBlock(listOf(data))
        sendRaw(block)
        return waitForResponse(corrId)?.command == "OK"
    }

    /** Зарегистрировать ключ расшифровки для публичного ключа */
    fun addDecryptionKey(publicKey: ByteArray, secretKey: ByteArray) {
        keyStore[Base64.encodeToString(publicKey, Base64.NO_WRAP)] = secretKey
    }

    /**
     * Скачать чанк с сервера.
     * Sends FGET with the recipient's DH key, then waits for a FILE response (60s timeout).
     * The FILE response contains the sender's DH key and nonce for decryption.
     * The actual chunk data follows as a separate transport block.
     */
    fun downloadChunk(recipientId: ByteArray, dhKey: ByteArray): ByteArray? {
        val corrId = SMPProtocol.generateCorrId()
        val cmd = "FGET ".encodeToByteArray() + byteArrayOf(dhKey.size.toByte()) + dhKey
        sendTransmission(corrId, recipientId, cmd)
        val resp = waitForResponse(corrId, 60000)
        if (resp?.command != "FILE") return null
        val d = resp.params; var p = 0
        val sl = d[p++].toInt() and 0xFF; val sdh = d.copyOfRange(p, p + sl); p += sl
        val nl = d[p++].toInt() and 0xFF; val nonce = d.copyOfRange(p, p + nl)
        val chunkData = readBlock()
        if (chunkData != null && chunkData.size >= 16) {
            val ourSecretKey = keyStore[Base64.encodeToString(dhKey, Base64.NO_WRAP)]
            if (ourSecretKey != null) {
                return NaClCrypto.cryptoBoxOpen(chunkData, nonce, sdh, ourSecretKey)
            }
        }
        return chunkData
    }

    /** Удалить чанк с сервера */
    fun deleteChunk(senderId: ByteArray): Boolean {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, senderId, "FDEL".encodeToByteArray())
        return waitForResponse(corrId)?.command == "OK"
    }

    /** Подтвердить получение чанка */
    fun ackChunk(recipientId: ByteArray): Boolean {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, recipientId, "FACK".encodeToByteArray())
        return waitForResponse(corrId)?.command == "OK"
    }

    /** Отправить PING для поддержания соединения */
    fun sendPing() {
        sendTransmission(ByteArray(0), ByteArray(0), "PING".encodeToByteArray())
    }

    /** Отправить Transmission через SMP-протокол */
    private fun sendTransmission(corrId: ByteArray, entityId: ByteArray, cmd: ByteArray) {
        val t = SMPProtocol.encodeTransmission(ByteArray(0), corrId, entityId, cmd)
        val block = SMPProtocol.buildTransportBlock(listOf(t))
        sendRaw(block)
    }

    /** Отправить сырые данные */
    private fun sendRaw(data: ByteArray) {
        try { out?.write(data); out?.flush() }
        catch (e: Exception) { Log.e(tag, "send error", e); onError(e) }
    }

    /**
     * Прочитать блок данных фиксированного размера (16384 байт) из сокета.
     * Blocks until exactly TRANSPORT_BLOCK_SIZE bytes are received or the stream ends.
     * Returns null if the socket is closed before the full block is read.
     */
    private fun readBlock(): ByteArray? {
        return try {
            val buf = ByteArray(SMPProtocol.TRANSPORT_BLOCK_SIZE)
            var pos = 0
            while (pos < SMPProtocol.TRANSPORT_BLOCK_SIZE) {
                val n = inp?.read(buf, pos, SMPProtocol.TRANSPORT_BLOCK_SIZE - pos) ?: -1
                if (n < 0) return null
                pos += n
            }
            buf
        } catch (e: Exception) { null }
    }

    /** Запустить фоновый поток чтения ответов от сервера */
    private fun startReader() {
        readerThread = thread(name = "xftp-reader", isDaemon = true) { readerLoop() }
    }

    /** Фоновый поток чтения ответов от сервера */
    private fun readerLoop() {
        while (running) {
            try {
                val block = readBlock() ?: break
                val result = SMPProtocol.parseTransportBlock(block)
                for (r in result.transmissions) {
                    val key = Base64.encodeToString(r.corrId, Base64.NO_WRAP)
                    synchronized(lock) {
                        if (key in pending) { pending[key] = r; lock.notifyAll() }
                    }
                }
            } catch (e: Exception) {
                if (running) { Log.e(tag, "reader: ${e.message}"); onError(e) }
                running = false
            }
        }
    }

    /** Ожидание ответа с таймаутом */
    private fun waitForResponse(corrId: ByteArray, timeoutMs: Long = 15000): SMPProtocol.ParsedResponse? {
        val key = Base64.encodeToString(corrId, Base64.NO_WRAP)
        synchronized(lock) {
            pending[key] = SMPProtocol.ParsedResponse(ByteArray(0), ByteArray(0), "", ByteArray(0))
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val r = pending[key]
                    if (r != null && r.command.isNotEmpty()) return r
                    lock.wait(minOf(deadline - System.currentTimeMillis(), 100L))
                }
                return null
            } finally { pending.remove(key) }
        }
    }
}
