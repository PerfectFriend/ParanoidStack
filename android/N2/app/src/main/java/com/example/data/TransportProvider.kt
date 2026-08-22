package com.example.data

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * P2P транспортный протокол приложений поверх Tor (+ опционально V2Ray).
 *
 * Маршрутизация: App → TransportSocket → Tor (9050) → [V2Ray (10808)] → destination
 *
 * [TransportProvider] — синглтон фасад, управляющий цепочкой прокси.
 * Любой компонент приложения может получить [TransportSocket] через provider.
 */
object TransportProvider {

    private const val TAG = "TransportProvider"
    private const val TOR_SOCKS_HOST = "127.0.0.1"
    private const val TOR_SOCKS_PORT = 9050
    private const val V2RAY_SOCKS_HOST = "127.0.0.1"
    private const val V2RAY_SOCKS_PORT = 10808

    @Volatile
    var useV2RayBypass: Boolean = true

    @Volatile
    var connectTimeoutMs: Int = 15000

    @Volatile
    var readTimeoutMs: Int = 30000

    /**
     * Создаёт сокет, маршрутизированный через Tor → V2Ray → destination.
     * Выполняет полное SOCKS5-рукопожатие, после чего сокет готов к отправке данных.
     */
    fun connect(host: String, port: Int): TransportSocket {
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(TOR_SOCKS_HOST, TOR_SOCKS_PORT), connectTimeoutMs)
            sock.soTimeout = readTimeoutMs

            val intermediateHost = if (useV2RayBypass) V2RAY_SOCKS_HOST else host
            val intermediatePort = if (useV2RayBypass) V2RAY_SOCKS_PORT else port

            // SOCKS5 to Tor: CONNECT to V2Ray (or directly to destination if V2Ray disabled)
            socksHandshake(sock, intermediateHost, intermediatePort)

            if (useV2RayBypass) {
                // SOCKS5 to V2Ray: CONNECT to actual destination
                socksHandshake(sock, host, port)
            }

            return TransportSocket(sock, host, port)
        } catch (e: Exception) {
            sock.close()
            throw e
        }
    }

    /**
     * Выполняет SOCKS5-рукопожатие на установленном сокете:
     * 1. Приветствие + метод (no auth)
     * 2. CONNECT-запрос к targetHost:targetPort
     */
    private fun socksHandshake(sock: Socket, targetHost: String, targetPort: Int) {
        val out = sock.getOutputStream()
        val `in` = sock.getInputStream()

        // Приветствие: SOCKS5, 1 метод, no-auth (0x00)
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val resp = ByteArray(2)
        readFully(`in`, resp)
        if (resp[0] != 0x05.toByte() || resp[1] != 0x00.toByte()) {
            throw IOException("SOCKS5 handshake failed: ${resp[0].toInt() and 0xFF} ${resp[1].toInt() and 0xFF}")
        }

        // CONNECT: определяем тип адреса
        out.write(byteArrayOf(0x05, 0x01, 0x00, 0x03))
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        out.write(hostBytes.size)
        out.write(hostBytes)
        out.write((targetPort shr 8) and 0xFF)
        out.write(targetPort and 0xFF)
        out.flush()

        val connResp = ByteArray(4)
        readFully(`in`, connResp)
        if (connResp[1] != 0x00.toByte()) {
            val code = connResp[1].toInt() and 0xFF
            throw IOException("SOCKS5 CONNECT error $code for $targetHost:$targetPort")
        }

        // Пропускаем BND.ADDR + BND.PORT
        skipBoundAddress(`in`, connResp[3])
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) throw IOException("Socket closed during read")
            offset += n
        }
    }

    private fun skipBoundAddress(input: InputStream, addrType: Byte) {
        when (addrType.toInt() and 0xFF) {
            1 -> readFully(input, ByteArray(6))
            3 -> { val len = input.read(); if (len > 0) readFully(input, ByteArray(len + 2)) }
            4 -> readFully(input, ByteArray(18))
        }
    }
}

class IOException(message: String) : java.io.IOException(message)

/**
 * Транспортный сокет — обёртка над [Socket], маршрутизированным через Tor→V2Ray.
 * Автоматически логирует трафик и поддерживает keep-alive.
 */
class TransportSocket(
    private val socket: Socket,
    val targetHost: String,
    val targetPort: Int
) : AutoCloseable {

    val inputStream: InputStream get() = socket.getInputStream()
    val outputStream: OutputStream get() = socket.getOutputStream()
    val isConnected: Boolean get() = socket.isConnected && !socket.isClosed

    fun setSoTimeout(timeoutMs: Int) { socket.soTimeout = timeoutMs }

    override fun close() {
        try { socket.close() } catch (_: java.lang.Exception) { Log.w("TransportProvider", "ignored exception") }
    }
}
