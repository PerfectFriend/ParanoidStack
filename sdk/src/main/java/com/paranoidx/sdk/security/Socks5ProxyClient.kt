/**
 * SOCKS5 proxy client implementation (RFC 1928).
 *
 * Performs full SOCKS5 handshake including method negotiation (NO AUTH / USER_PASS),
 * TCP connection establishment, and optional username/password authentication.
 */
package com.paranoidx.sdk.security

import com.paranoidx.sdk.security.SdkLogger
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Файл: Socks5ProxyClient.kt
 * Пакет: com.example.data.security
 * Назначение: Клиент протокола SOCKS5 (RFC 1928) для установки безопасных прокси-туннелей.
 * Реализует полное рукопожатие SOCKS5: согласование методов аутентификации (NO AUTH / USER_PASS),
 * подключение к целевому хосту с опциональной защитой от DNS-утечек (передача домена
 * на прокси-сервер вместо локального разрешения).
 *
 * Используется для маршрутизации трафика через Tor (луковые прокси) и другие
 * анонимные сети. Все операции синхронные, с таймаутами.
 */
object Socks5ProxyClient {
    private const val TAG = "Socks5ProxyClient"

    /**
     * Callback для логирования этапов SOCKS5 рукопожатия.
     * Позволяет UI отображать прогресс подключения.
     */
    interface LogCallback {
        fun onLog(message: String)
    }

    /**
     * Выполняет полное RFC 1928 SOCKS5 рукопожатие через raw Java Socket.
     * Этапы: 1) TCP-соединение с прокси 2) Согласование методов аутентификации
     * 3) USER/PASS аутентификация (если требуется) 4) Команда CONNECT к целевому хосту.
     *
     * @param proxyHost хост прокси-сервера
     * @param proxyPort порт прокси-сервера
     * @param authMode режим аутентификации: "NONE" или "USER_PASS"
     * @param username имя пользователя (для USER_PASS)
     * @param password пароль (для USER_PASS)
     * @param targetHost целевой хост для подключения
     * @param targetPort целевой порт
     * @param dnsLeakProtection если true, домен передаётся на прокси (защита от DNS-утечек)
     * @param callback колбэк для логирования
     * @return true, если туннель успешно установлен
     */
    fun testConnection(
        proxyHost: String,
        proxyPort: Int,
        authMode: String, // NONE, USER_PASS, ONION_COOKIE
        username: String = "",
        password: String = "",
        targetHost: String,
        targetPort: Int,
        dnsLeakProtection: Boolean,
        callback: LogCallback
    ): Boolean {
        var socket: Socket? = null
        try {
            callback.onLog("SOCKS5_CLIENT: Creating TCP socket to proxy at $proxyHost:$proxyPort...")
            socket = Socket()
            // Set 3-second connection timeout to keep UI snappy
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 3000)
            socket.soTimeout = 3000
            callback.onLog("SOCKS5_CLIENT: TCP Connection established. Initiating RFC 1928 handshake.")

            val outStream: OutputStream = socket.getOutputStream()
            val inStream: InputStream = socket.getInputStream()

            // 1. SOCKS5 greeting/methods negotiation
            // Protocol version: 5 (1 byte)
            // Number of methods supported: 1 or 2 (1 byte)
            // Methods: 0x00 (NO AUTH), 0x02 (USER/PASS)
            val methods = if (authMode == "USER_PASS") {
                byteArrayOf(0x00, 0x02)
            } else {
                byteArrayOf(0x00)
            }
            
            val greeting = ByteArray(2 + methods.size)
            greeting[0] = 0x05 // SOCKS version 5
            greeting[1] = methods.size.toByte()
            System.arraycopy(methods, 0, greeting, 2, methods.size)

            callback.onLog("SOCKS5_CLIENT: Sending greeting frame: [VER=0x05, NMETHODS=${methods.size}, METHODS=${methods.joinToString { "0x%02X".format(it) }}]")
            outStream.write(greeting)
            outStream.flush()

            // 2. Read greeting response
            // Response: version (1 byte), selected method (1 byte)
            val response = ByteArray(2)
            val readBytes = inStream.read(response)
            if (readBytes < 2) {
                callback.onLog("SOCKS5_CLIENT: Handshake error - Premature end of stream from proxy.")
                return false
            }

            val version = response[0].toInt() and 0xFF
            val selectedMethod = response[1].toInt() and 0xFF
            callback.onLog("SOCKS5_CLIENT: Proxy response received: [VER=0x%02X, METHOD=0x%02X]".format(version, selectedMethod))

            if (version != 0x05) {
                callback.onLog("SOCKS5_CLIENT: Handshake failed - Unsupported SOCKS version $version")
                return false
            }

            // 3. Negotiate Auth if requested and selected
            if (selectedMethod == 0x02) {
                if (authMode != "USER_PASS") {
                    callback.onLog("SOCKS5_CLIENT: Handshake error - Proxy demands USER/PASS but client configuration was NONE.")
                    return false
                }
                callback.onLog("SOCKS5_CLIENT: Negotiating Username/Password auth [Subnegotiation v1]...")
                val userBytes = username.toByteArray(Charsets.UTF_8)
                val passBytes = password.toByteArray(Charsets.UTF_8)

                // Subnegotiation greeting:
                // [VER = 1] [ULEN] [USER...] [PLEN] [PASS...]
                val authRequest = ByteArray(3 + userBytes.size + passBytes.size)
                authRequest[0] = 0x01 // Subnegotiation version 1
                authRequest[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authRequest, 2, userBytes.size)
                authRequest[2 + userBytes.size] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authRequest, 3 + userBytes.size, passBytes.size)

                callback.onLog("SOCKS5_CLIENT: Transmitting credentials block: User='${username}', Pass=******")
                outStream.write(authRequest)
                outStream.flush()

                // Auth response:
                // [VER = 1] [STATUS = 0x00 (SUCCESS)]
                val authResponse = ByteArray(2)
                val authRead = inStream.read(authResponse)
                if (authRead < 2) {
                    callback.onLog("SOCKS5_CLIENT: Handshake error - No response to authentication request.")
                    return false
                }
                val authStatus = authResponse[1].toInt() and 0xFF
                if (authStatus != 0x00) {
                    callback.onLog("SOCKS5_CLIENT: Authentication failed with status 0x%02X (Access Denied)".format(authStatus))
                    return false
                }
                callback.onLog("SOCKS5_CLIENT: Authentication success [Status = 0x00]")
            } else if (selectedMethod == 0xFF) {
                callback.onLog("SOCKS5_CLIENT: Handshake failed - No acceptable authentication methods selected by proxy.")
                return false
            } else if (selectedMethod == 0x00) {
                callback.onLog("SOCKS5_CLIENT: Anonymous tunnel selected (NO AUTH required by proxy).")
            }

            // 4. Send Connect Command
            // SOCKS CONNECT Request:
            // [VER = 0x05] [CMD = 0x01 (CONNECT)] [RSV = 0x00] [ATYP] [DST.ADDR] [DST.PORT]
            val targetBytes = targetHost.toByteArray(Charsets.UTF_8)
            val requestStream = ByteArrayOutputStream()
            requestStream.write(0x05) // VER
            requestStream.write(0x01) // CMD: CONNECT
            requestStream.write(0x00) // RSV: Reserved

            if (dnsLeakProtection) {
                requestStream.write(0x03) // ATYP: DOMAINNAME (Tor resolving onion addresses)
                requestStream.write(targetBytes.size) // ULEN (length byte)
                requestStream.write(targetBytes)
            } else {
                callback.onLog("SOCKS5_CLIENT: [RESOLVE WARNING] Resolving $targetHost locally (DNS Leak Vulnerability)...")
                val localAddress = java.net.InetAddress.getByName(targetHost)
                val addressBytes = localAddress.address
                if (addressBytes.size == 4) {
                    requestStream.write(0x01) // ATYP: IPv4
                    requestStream.write(addressBytes)
                } else {
                    requestStream.write(0x04) // ATYP: IPv6
                    requestStream.write(addressBytes)
                }
            }

            // DST.PORT: 2 bytes (Big Endian)
            requestStream.write((targetPort shr 8) and 0xFF)
            requestStream.write(targetPort and 0xFF)

            val connectRequest = requestStream.toByteArray()
            callback.onLog("SOCKS5_CLIENT: Sending CONNECT request to $targetHost:$targetPort [ATYP=${if (dnsLeakProtection) "0x03 DOMAIN" else "IP"}]")
            outStream.write(connectRequest)
            outStream.flush()

            // 5. Read response to CONNECT request
            val connectResponse = ByteArray(1024)
            val connRead = inStream.read(connectResponse)
            if (connRead < 4) {
                callback.onLog("SOCKS5_CLIENT: Error - Proxy severed connection during tunnel establishment.")
                return false
            }

            val replyCode = connectResponse[1].toInt() and 0xFF
            val replyMsg = when (replyCode) {
                0x00 -> "0x00 (Succeeded)"
                0x01 -> "0x01 (General SOCKS server failure)"
                0x02 -> "0x02 (Connection not allowed by ruleset)"
                0x03 -> "0x03 (Network unreachable)"
                0x04 -> "0x04 (Host unreachable)"
                0x05 -> "0x05 (Connection refused)"
                0x06 -> "0x06 (TTL expired)"
                0x07 -> "0x07 (Command not supported)"
                0x08 -> "0x08 (Address type not supported)"
                else -> "0x%02X (Unknown reply code)".format(replyCode)
            }

            callback.onLog("SOCKS5_CLIENT: Proxy reply received: $replyMsg")
            if (replyCode != 0x00) {
                callback.onLog("SOCKS5_CLIENT: SOCKS connection failed. Tunnel creation aborted.")
                return false
            }

            callback.onLog("SOCKS5_CLIENT: SOCKS5 PRIVACY TUNNEL FULLY ESTABLISHED!")
            return true
        } catch (e: SocketTimeoutException) {
            callback.onLog("SOCKS5_CLIENT: Connection timeout: ${e.localizedMessage}")
            SdkLogger.e(TAG, "SOCKS5 Timeout", e)
        } catch (e: java.net.ConnectException) {
            callback.onLog("SOCKS5_CLIENT: Connection Refused (Proxy is offline on port $proxyPort): ${e.localizedMessage}")
            SdkLogger.e(TAG, "SOCKS5 Connect Exception", e)
        } catch (e: Exception) {
            callback.onLog("SOCKS5_CLIENT: Handshake aborted: ${e.localizedMessage}")
            SdkLogger.e(TAG, "SOCKS5 Exception", e)
        } finally {
            try {
                socket?.close()
                callback.onLog("SOCKS5_CLIENT: Connection closed and resources released.")
            } catch (e: Exception) { SdkLogger.w(TAG, "close socket: ${e.message?.take(50)}") }
        }
        return false
    }
}
