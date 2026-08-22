/**
 * SMP-клиент — низкоуровневый сетевой клиент для SMP-протокола.
 * Управляет TLS-соединением с SMP-сервером, отправкой/приёмом
 * транспортных блоков, синхронными запросами с ожиданием ответа.
 *
 * Поддерживает проверку сертификата сервера по SPKI отпечатку.
 *
 * Architecture role:
 *   Provides the transport layer for SimpleX messaging. Each SMPClient manages a single
 *   TLS 1.3 connection to an SMP server, authenticating the server via SPKI (Subject Public
 *   Key Info) fingerprint pinning. It sends SMP command transmissions and receives responses
 *   through a background reader thread. The request-response model uses correlation IDs
 *   (corrId) with a wait/notify pattern — the `pending` map stores expected responses,
 *   and the reader thread dispatches matched responses back to the waiting caller.
 *
 *   Supports: queue creation (NEW), subscription (SUB), sending (SEND), acknowledgment (ACK),
 *   deletion (DEL), secure queue setup (KEY), push notifications (NKEY/NDEL), and keep-alive (PING).
 */
package com.example.data

import android.util.Base64
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManager
import kotlin.concurrent.thread

/** Низкоуровневый клиент SMP-протокола */
class SMPClient(
    val server: SMPQueueURI,                         // SMP-сервер для подключения
    private val onServerMessage: (SMPProtocol.ParsedResponse) -> Unit = {},  // callback входящего сообщения
    private val onError: (Exception) -> Unit = {}     // callback ошибки
) {
    private var socket: SSLSocket? = null
    private var out: OutputStream? = null
    private var inp: InputStream? = null
    private var running = false
    private val tag = "SMP-${server.host.take(16)}"
    private val pending = mutableMapOf<String, SMPProtocol.ParsedResponse>()  // ожидающие ответы
    private val lock = Object()

    var serverDhKey: ByteArray? = null
        private set

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed != true

    /**
     * Подключиться к SMP-серверу через TLS с проверкой SPKI.
     * Пробует TLSv1.3, при ошибке — TLSv1.2. Устанавливает SNI и таймауты.
     */
    fun connect(): AppResult<Unit> {
        val protocols = listOf("TLSv1.3", "TLSv1.2")
        var lastError: Exception? = null
        for (proto in protocols) {
            try {
                val ctx = SSLContext.getInstance(proto)
                val tm = object : X509TrustManager {
                    override fun checkClientTrusted(certs: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(certs: Array<out X509Certificate>?, authType: String?) {
                        if (certs.isNullOrEmpty()) throw AppException.ProtocolException("no certs")
                        val spki = certs[0].publicKey.encoded
                        val digest = MessageDigest.getInstance("SHA-256").digest(spki)
                        if (!digest.contentEquals(server.toFingerprint()))
                            throw AppException.ProtocolException("SPKI mismatch on $proto")
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
                ctx.init(null, arrayOf<TrustManager>(tm), SecureRandom())
                val raw = ctx.socketFactory.createSocket(server.host, server.port)
                val ssl = raw as? SSLSocket
                if (ssl == null) { raw.close(); continue }
                try {
                    ssl.useClientMode = true
                    ssl.soTimeout = 15000
                    try {
                        val sni = javax.net.ssl.SNIHostName(server.host)
                        val params = ssl.sslParameters
                        val sniList = params.serverNames?.toMutableList() ?: mutableListOf()
                        sniList.add(sni)
                        params.serverNames = sniList
                        ssl.sslParameters = params
                    } catch (_: java.lang.Exception) { Log.w("SMPClient", "ignored exception") }
                    ssl.startHandshake()
                    socket = ssl
                } catch (e: Exception) {
                    ssl.close()
                    throw e
                }
                out = ssl.getOutputStream()
                inp = ssl.getInputStream()
                running = true
                Log.i(tag, "Connected via $proto")
                return AppResult.Success(Unit)
            } catch (e: AppException) {
                lastError = e; Log.w(tag, "$proto: ${e.message}")
            } catch (e: Exception) {
                lastError = e; Log.w(tag, "$proto: ${e.message}")
            }
        }
        Log.e(tag, "connect failed: ${lastError?.message}")
        onError(lastError ?: Exception("All TLS versions failed"))
        return AppException.NetworkException(lastError?.message ?: "Connection failed").asError()
    }

    /** Отключиться от сервера */
    fun disconnect() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        try { socket?.close() } catch (_: java.lang.Exception) { Log.w("SMPClient", "ignored exception") }
        try { inp?.close() } catch (_: java.lang.Exception) { Log.w("SMPClient", "ignored exception") }
        try { out?.close() } catch (_: java.lang.Exception) { Log.w("SMPClient", "ignored exception") }
        socket = null; inp = null; out = null
    }

    /** Отправить трансмиссию (корреляция, сущность, команда, авторизация) */
    fun sendTransmission(corrId: ByteArray, entityId: ByteArray, cmd: ByteArray, auth: ByteArray = ByteArray(0)) {
        val t = SMPProtocol.encodeTransmission(auth, corrId, entityId, cmd)
        val block = SMPProtocol.buildTransportBlock(listOf(t))
        sendRaw(block)
    }

    /** Отправить сырые данные в сокет */
    fun sendRaw(data: ByteArray) {
        try { out?.write(data); out?.flush() }
        catch (e: Exception) { Log.e(tag, "send error", e); onError(e) }
    }

    /** Запустить фоновый поток чтения ответов сервера */
    fun startReader() {
        thread(name = "smp-reader", isDaemon = true) {
            val buf = ByteArray(SMPProtocol.TRANSPORT_BLOCK_SIZE)
            while (running && !Thread.currentThread().isInterrupted) {
                try {
                    var pos = 0
                    while (pos < SMPProtocol.TRANSPORT_BLOCK_SIZE && running) {
                        val n = inp?.read(buf, pos, SMPProtocol.TRANSPORT_BLOCK_SIZE - pos) ?: -1
                        if (n < 0) { running = false; break }
                        pos += n
                    }
                    if (pos == SMPProtocol.TRANSPORT_BLOCK_SIZE) {
                        val result = SMPProtocol.parseTransportBlock(buf)
                        for (r in result.transmissions) {
                            val key = Base64.encodeToString(r.corrId, Base64.NO_WRAP)
                            synchronized(lock) {
                                if (key in pending) {
                                    pending[key] = r
                                    lock.notifyAll()
                                }
                            }
                            onServerMessage(r)
                        }
                    }
                } catch (e: Exception) {
                    if (running) { Log.e(tag, "reader: ${e.message}"); onError(e) }
                    running = false
                }
            }
            Log.w(tag, "reader stopped")
        }
    }

    /**
     * Создать очередь на сервере.
     * @param rAuthKey ключ авторизации получателя
     * @param rDhKey ключ DH получателя
     * @param sndSecure флаг безопасной отправки
     * @return результат создания очереди
     */
    fun createQueue(rAuthKey: ByteArray, rDhKey: ByteArray, sndSecure: Boolean = false): AppResult<QueueCreateResult> {
        return try {
            val corrId = SMPProtocol.generateCorrId()
            val cmd = byteArrayOf() +
                    byteArrayOf(rAuthKey.size.toByte()) + rAuthKey +
                    byteArrayOf(rDhKey.size.toByte()) + rDhKey +
                    "0S".encodeToByteArray() +
                    (if (sndSecure) "T" else "F").encodeToByteArray()
            sendTransmission(corrId, ByteArray(0), "NEW ".encodeToByteArray() + cmd)
            val resp = waitForResponse(corrId)
                ?: return AppException.TimeoutException("createQueue timed out").asError()
            if (resp.command != "IDS")
                return AppException.ProtocolException("Expected IDS, got ${resp.command}").asError()
            val d = resp.params; var p = 0
            val rl = d[p++].toInt() and 0xFF; val rid = d.copyOfRange(p, p + rl); p += rl
            val sl = d[p++].toInt() and 0xFF; val sid = d.copyOfRange(p, p + sl); p += sl
            val dl = d[p++].toInt() and 0xFF; val dh = d.copyOfRange(p, p + dl); p += dl
            serverDhKey = dh
            val ss = p < d.size && d[p].toInt() == 'T'.code
            QueueCreateResult(rid, sid, dh, ss).asSuccess()
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.ProtocolException("createQueue failed", e).asError()
        }
    }

    /** Подписаться на очередь (получение сообщений) */
    fun subscribe(entityId: ByteArray): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, entityId, "SUB".encodeToByteArray())
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK" || resp?.command == "MSG") AppResult.Success(Unit)
        else AppException.ProtocolException("subscribe failed: ${resp?.command}").asError()
    }

    /** Установить безопасный режим очереди (KEY) */
    fun secureQueue(entityId: ByteArray, senderKey: ByteArray): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        val cmd = "KEY ".encodeToByteArray() + byteArrayOf(senderKey.size.toByte()) + senderKey
        sendTransmission(corrId, entityId, cmd)
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK") AppResult.Success(Unit)
        else AppException.ProtocolException("secureQueue failed: ${resp?.command}").asError()
    }

    /** Отправить текстовое сообщение в очередь */
    fun sendMessage(text: String): AppResult<Unit> {
        return sendMessage(ByteArray(0), text.encodeToByteArray())
    }

    /** Отправить сообщение в очередь */
    fun sendMessage(entityId: ByteArray, msgBody: ByteArray, auth: ByteArray = ByteArray(0)): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        val flags = byteArrayOf(0)
        val body = SMPProtocol.encodePaddedString(msgBody, SMPProtocol.MSG_BODY_PADDED_SIZE)
        sendTransmission(corrId, entityId, "SEND ".encodeToByteArray() + flags + body, auth)
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK") AppResult.Success(Unit)
        else AppException.ProtocolException("sendMessage failed: ${resp?.command}").asError()
    }

    /** Подтвердить получение сообщения (ACK) */
    fun ackMessage(entityId: ByteArray): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, entityId, "ACK".encodeToByteArray())
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK") AppResult.Success(Unit)
        else AppException.ProtocolException("ackMessage failed: ${resp?.command}").asError()
    }

    /** Удалить очередь (DEL) */
    fun deleteQueue(entityId: ByteArray): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, entityId, "DEL".encodeToByteArray())
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK") AppResult.Success(Unit)
        else AppException.ProtocolException("deleteQueue failed: ${resp?.command}").asError()
    }

    /** Включить push-уведомления для очереди (NKEY) */
    fun enableNotifications(entityId: ByteArray): AppResult<NkeyResult> {
        return try {
            val corrId = SMPProtocol.generateCorrId()
            sendTransmission(corrId, entityId, "NKEY".encodeToByteArray())
            val resp = waitForResponse(corrId)
                ?: return AppException.TimeoutException("enableNotifications timed out").asError()
            if (resp.command != "NID")
                return AppException.ProtocolException("Expected NID, got ${resp.command}").asError()
            val d = resp.params; var p = 0
            val nl = d[p++].toInt() and 0xFF; val nid = d.copyOfRange(p, p + nl); p += nl
            val dl = d[p++].toInt() and 0xFF; val dh = d.copyOfRange(p, p + dl)
            NkeyResult(nid, dh).asSuccess()
        } catch (e: AppException) {
            AppResult.Error(e)
        } catch (e: Exception) {
            AppException.ProtocolException("enableNotifications failed", e).asError()
        }
    }

    /** Отключить push-уведомления (NDEL) */
    fun disableNotifications(entityId: ByteArray): AppResult<Unit> {
        val corrId = SMPProtocol.generateCorrId()
        sendTransmission(corrId, entityId, "NDEL".encodeToByteArray())
        val resp = waitForResponse(corrId)
        return if (resp?.command == "OK") AppResult.Success(Unit)
        else AppException.ProtocolException("disableNotifications failed: ${resp?.command}").asError()
    }

    /** Результат включения уведомлений */
    data class NkeyResult(
        val notifierId: ByteArray,   // ID уведомителя
        val serverDhKey: ByteArray   // ключ DH сервера для уведомлений
    )

    /** Отправить PING для поддержания соединения */
    fun sendPing() {
        sendTransmission(ByteArray(0), ByteArray(0), "PING".encodeToByteArray())
    }

    /** Результат создания очереди */
    data class QueueCreateResult(
        val recipientId: ByteArray,    // ID получателя
        val senderId: ByteArray,       // ID отправителя
        val serverDhKey: ByteArray,    // ключ DH сервера
        val canSndSecure: Boolean      // флаг безопасной отправки
    )

    /**
     * Ожидать ответ с заданным corrId (таймаут 15 сек).
     *
     * Uses a monitor-style wait/notify pattern: the reader thread (startReader) places inbound
     * responses into the `pending` map keyed by Base64-encoded correlation ID, then calls
     * lock.notifyAll(). This method blocks on lock.wait() until a matching response arrives
     * or the deadline expires. The pending entry is always cleaned up in the finally block
     * to prevent memory leaks from lost responses.
     */
    private fun waitForResponse(corrId: ByteArray, timeoutMs: Long = 15000): SMPProtocol.ParsedResponse? {
        val key = Base64.encodeToString(corrId, Base64.NO_WRAP)
        synchronized(lock) {
            pending[key] = SMPProtocol.ParsedResponse(ByteArray(0), ByteArray(0), "", ByteArray(0))
            try {
                val deadline = System.currentTimeMillis() + timeoutMs
                var remaining: Long
                while (System.currentTimeMillis() < deadline) {
                    val r = pending[key]
                    if (r != null && r.command.isNotEmpty()) return r
                    remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    lock.wait(minOf(remaining, 100L))
                }
                return null
            } finally {
                pending.remove(key)
            }
        }
    }
}
