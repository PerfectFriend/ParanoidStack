/**
 * Клиент для подключения к Tor через SOCKS5 прокси.
 * Предоставляет надёжное подключение с повторными попытками (exponential backoff),
 * SOCKS5 handshake с поддержкой аутентификации и DNS-резолвинг через DNS-over-HTTPS.
 */
package com.example.data

import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets

/** Клиент для работы с Tor SOCKS5 прокси */
class TorProxyClient(
    private val torHost: String = "127.0.0.1",      // хост Tor SOCKS5
    private val torPort: Int = 9050,                 // порт Tor SOCKS5
    private val socksUsername: String? = null,        // имя пользователя SOCKS5
    private val socksPassword: String? = null,        // пароль SOCKS5
    private val connectTimeoutMs: Int = 15000,        // таймаут подключения
    private val readTimeoutMs: Int = 15000            // таймаут чтения
) {

    /**
     * Подключиться к целевому хосту через Tor с повторными попытками.
     * @param targetHost целевой .onion или обычный хост
     * @param targetPort целевой порт
     * @param timeoutMs таймаут подключения
     * @return подключённый сокет
     */
    fun connectThroughTor(targetHost: String, targetPort: Int, connectTimeout: Int = connectTimeoutMs): Socket {
        return retryWithBackoff {
            connectDirect(targetHost, targetPort, connectTimeout)
        }
    }

    /** Прямое подключение через Tor SOCKS5 */
    private fun connectDirect(targetHost: String, targetPort: Int, connectTimeout: Int): Socket {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(torHost, torPort), connectTimeout)
            socket.soTimeout = readTimeoutMs

            val outputStream = socket.getOutputStream()
            val inputStream = socket.getInputStream()

            performSocks5Handshake(outputStream, inputStream)
            sendConnectionRequest(outputStream, inputStream, targetHost, targetPort)
        } catch (e: Exception) {
            socket.close()
            throw e
        }
        return socket
    }

    /** SOCKS5 handshake с поддержкой username/password аутентификации */
    private fun performSocks5Handshake(outputStream: OutputStream, inputStream: InputStream) {
        val useAuth = !socksUsername.isNullOrBlank() && !socksPassword.isNullOrBlank()

        // Send greeting: [ver:1=0x05][nmethods:1][methods...]
        // Offer 0x00 (no auth) and optionally 0x02 (username/password)
        val methods = if (useAuth) {
            byteArrayOf(0x05.toByte(), 0x02.toByte(), 0x00.toByte(), 0x02.toByte())
        } else {
            byteArrayOf(0x05.toByte(), 0x01.toByte(), 0x00.toByte())
        }
        outputStream.write(methods)
        outputStream.flush()

        // Read server's chosen method: [ver:1][method:1]
        val handshakeResponse = ByteArray(2)
        val bytesRead = readFully(inputStream, handshakeResponse)
        if (bytesRead < 2 || handshakeResponse[0] != 0x05.toByte()) {
            throw IllegalStateException("SOCKS5 handshake failed: invalid version")
        }

        val chosenMethod = handshakeResponse[1]
        when (chosenMethod.toInt()) {
            0x00 -> { } // no auth
            0x02 -> {   // username/password
                if (!useAuth) {
                    throw IllegalStateException("SOCKS5 server requires authentication but none provided")
                }
                performUserPassAuth(outputStream, inputStream)
            }
            0xFF -> throw IllegalStateException("SOCKS5 handshake failed: no acceptable methods")
            else -> throw IllegalStateException("SOCKS5 handshake failed: unknown method 0x${chosenMethod.toString(16)}")
        }
    }

    /** Аутентификация username/password для SOCKS5 */
    private fun performUserPassAuth(outputStream: OutputStream, inputStream: InputStream) {
        val userBytes = (socksUsername ?: "").toByteArray(StandardCharsets.UTF_8)
        val passBytes = (socksPassword ?: "").toByteArray(StandardCharsets.UTF_8)

        if (userBytes.size > 255 || passBytes.size > 255) {
            throw IllegalArgumentException("SOCKS5 username/password too long (max 255 bytes each)")
        }

        val authMsg = ByteArray(1 + 1 + userBytes.size + 1 + passBytes.size)
        var pos = 0
        authMsg[pos++] = 0x01.toByte()
        authMsg[pos++] = userBytes.size.toByte()
        userBytes.copyInto(authMsg, pos); pos += userBytes.size
        authMsg[pos++] = passBytes.size.toByte()
        passBytes.copyInto(authMsg, pos)

        outputStream.write(authMsg)
        outputStream.flush()

        val authResponse = ByteArray(2)
        readFully(inputStream, authResponse)
        if (authResponse[0] != 0x01.toByte() || authResponse[1] != 0x00.toByte()) {
            throw IllegalStateException("SOCKS5 username/password authentication failed (status: 0x${authResponse[1].toString(16)})")
        }
    }

    /** Отправить запрос на подключение через SOCKS5 */
    private fun sendConnectionRequest(outputStream: OutputStream, inputStream: InputStream, targetHost: String, targetPort: Int) {
        val requestHeader = byteArrayOf(0x05.toByte(), 0x01.toByte(), 0x00.toByte(), 0x03.toByte())
        outputStream.write(requestHeader)

        val hostBytes = targetHost.toByteArray(StandardCharsets.US_ASCII)
        if (hostBytes.size > 255) {
            throw IllegalArgumentException("Target host name is too long for SOCKS5")
        }
        outputStream.write(hostBytes.size)
        outputStream.write(hostBytes)

        outputStream.write((targetPort shr 8) and 0xFF)
        outputStream.write(targetPort and 0xFF)
        outputStream.flush()

        val responseHeader = ByteArray(4)
        val headerBytesRead = readFully(inputStream, responseHeader)
        if (headerBytesRead < 4 || responseHeader[0] != 0x05.toByte()) {
            throw IllegalStateException("Tor connection request failed: invalid response header")
        }

        val replyStatus = responseHeader[1]
        if (replyStatus != 0x00.toByte()) {
            val errorMsg = when (replyStatus.toInt()) {
                1 -> "general SOCKS server failure"
                2 -> "connection not allowed by ruleset"
                3 -> "Network unreachable"
                4 -> "Host unreachable"
                5 -> "Connection refused"
                6 -> "TTL expired"
                7 -> "Command not supported"
                8 -> "Address type not supported"
                else -> "unknown error $replyStatus"
            }
            throw IllegalStateException("Tor SOCKS5 connection refused: $errorMsg")
        }

        // Читаем BND.ADDR и BND.PORT (игнорируем)
        val boundAddressType = responseHeader[3]
        when (boundAddressType.toInt()) {
            1 -> { val dummy = ByteArray(6); readFully(inputStream, dummy) }
            3 -> { val len = inputStream.read(); val dummy = ByteArray(len + 2); readFully(inputStream, dummy) }
            4 -> { val dummy = ByteArray(18); readFully(inputStream, dummy) }
        }
    }

    /**
     * Разрешить доменное имя через DNS-over-HTTPS (Cloudflare).
     * Uses Cloudflare's DNS-over-HTTPS API (application/dns-json) to resolve hostnames,
     * which is more reliable over Tor than traditional UDP DNS (which Tor relays may block).
     *
     * @param host имя хоста
     * @return IP-адрес или null при ошибке
     */
    fun resolveViaDoh(host: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://cloudflare-dns.com/dns-query?name=$host&type=A")
            conn = url.openConnection() as? HttpURLConnection ?: return null
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/dns-json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.connect()

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(body)
                val answer = json.optJSONArray("Answer")
                if (answer != null && answer.length() > 0) {
                    val data = answer.getJSONObject(0).optString("data", null)
                    if (data != null) return data
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Проверить, запущен ли Tor (доступен ли SOCKS5 порт) */
    fun isTorRunning(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(torHost, torPort), 2000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Выполнить действие с повторными попытками и экспоненциальной задержкой (backoff).
     * Retries up to 3 times with delays of 1s, 2s, 4s between attempts.
     * Used to handle transient Tor connection failures (e.g., Tor still bootstrapping).
     */
    private fun retryWithBackoff(action: () -> Socket): Socket {
        var lastException: Exception? = null
        var delayMs = 1000L
        val maxRetries = 3

        for (attempt in 0..maxRetries) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    Thread.sleep(delayMs)
                    delayMs *= 2  // exponential backoff: 1s -> 2s -> 4s
                }
            }
        }

        throw IllegalStateException("TorProxyClient: all $maxRetries retries exhausted", lastException)
    }

    /** Прочитать ровно buffer.size байт из потока */
    private fun readFully(inputStream: InputStream, buffer: ByteArray): Int {
        var bytesReadTotal = 0
        while (bytesReadTotal < buffer.size) {
            val result = inputStream.read(buffer, bytesReadTotal, buffer.size - bytesReadTotal)
            if (result == -1) break
            bytesReadTotal += result
        }
        return bytesReadTotal
    }
}
