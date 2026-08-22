package com.example.data

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Построение цепочки SOCKS5 прокси */
class Socks5Chain {
    private val tag = "Socks5Chain"

    /** Звено цепочки прокси */
    data class ChainLink(
        val host: String,
        val port: Int,
        val username: String = "",
        val password: String = ""
    )

    /** Конфигурация цепочки */
    data class ChainConfig(
        val torProxy: ChainLink = ChainLink("127.0.0.1", 9050),
        val v2rayProxy: ChainLink = ChainLink("127.0.0.1", 10808),
        val useV2RayBypass: Boolean = true
    )

    fun createChainedSocket(
        targetHost: String,
        targetPort: Int,
        config: ChainConfig = ChainConfig()
    ): Socket? {
        var socket: Socket? = null
        var v2raySocket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(config.torProxy.host, config.torProxy.port), 10000)

            performSocks5Handshake(
                socket.getOutputStream(),
                socket.getInputStream(),
                if (config.useV2RayBypass) config.v2rayProxy.host else targetHost,
                if (config.useV2RayBypass) config.v2rayProxy.port else targetPort,
                config.torProxy.username,
                config.torProxy.password
            )

            if (config.useV2RayBypass) {
                v2raySocket = Socket()
                v2raySocket.connect(InetSocketAddress(config.v2rayProxy.host, config.v2rayProxy.port), 10000)
                performSocks5Handshake(
                    v2raySocket.getOutputStream(),
                    v2raySocket.getInputStream(),
                    targetHost,
                    targetPort
                )
                socket.close()
                v2raySocket
            } else {
                socket
            }
        } catch (e: Exception) {
            Log.e(tag, "Chain failed", e)
            socket?.close()
            v2raySocket?.close()
            null
        }
    }

    private fun performSocks5Handshake(
        out: OutputStream,
        inp: InputStream,
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ) {
        val greetMsg = if (username.isNotEmpty()) {
            byteArrayOf(0x05, 0x02, 0x00, 0x02)
        } else {
            byteArrayOf(0x05, 0x01, 0x00)
        }
        out.write(greetMsg)
        out.flush()

        val greetResp = ByteArray(2)
        inp.read(greetResp)
        if (greetResp[0] != 0x05.toByte()) throw Exception("Invalid SOCKS version")

        if (greetResp[1] == 0x02.toByte() && username.isNotEmpty()) {
            val uBytes = username.toByteArray()
            val pBytes = password.toByteArray()
            val authMsg = byteArrayOf(0x01, uBytes.size.toByte()) + uBytes + pBytes.size.toByte() + pBytes
            out.write(authMsg)
            out.flush()
            val authResp = ByteArray(2)
            inp.read(authResp)
            if (authResp[1] != 0x00.toByte()) throw Exception("SOCKS auth failed")
        }

        val hostBytes = host.toByteArray()
        val req = byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()) + hostBytes +
                ((port shr 8) and 0xFF).toByte() + (port and 0xFF).toByte()
        out.write(req)
        out.flush()

        val resp = ByteArray(4)
        inp.read(resp)
        if (resp[1] != 0x00.toByte()) throw Exception("SOCKS connection refused")

        // Read BND.ADDR + BND.PORT
        val boundAddrType = resp[3].toInt() and 0xFF
        when (boundAddrType) {
            1 -> readFully(inp, ByteArray(6))
            3 -> { val len = inp.read(); readFully(inp, ByteArray(len + 2)) }
            4 -> readFully(inp, ByteArray(18))
        }
    }

    private fun readFully(inp: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = inp.read(buf, off, buf.size - off)
            if (n < 0) throw Exception("Unexpected EOF reading SOCKS bound address")
            off += n
        }
    }
}