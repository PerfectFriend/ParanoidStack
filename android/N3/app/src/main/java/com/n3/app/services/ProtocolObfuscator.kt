package com.n3.app.services

import android.util.Log
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

class ProtocolObfuscator private constructor() {
    companion object {
        private const val TAG = "NexusChat/Obfuscator"
        @Volatile private var instance: ProtocolObfuscator? = null
        fun getInstance(): ProtocolObfuscator =
            instance ?: synchronized(this) {
                instance ?: ProtocolObfuscator().also { instance = it }
            }
    }

    private val rng = SecureRandom()

    data class TlsFingerprint(
        val cipherSuites: List<Int>,
        val curves: List<Int>,
        val signatureAlgorithms: List<Int>,
        val alpn: List<String>,
        val tlsVersion: String
    )

    val fingerprints = listOf(
        TlsFingerprint(
            listOf(0x1301, 0x1302, 0x1303, 0xC02B, 0xC02F, 0xCCA9, 0xCCA8, 0xC013, 0xC014, 0x009C, 0x009D, 0x002F, 0x0035),
            listOf(0x001D, 0x0017, 0x0018, 0x0019),
            listOf(0x0403, 0x0804, 0x0401, 0x0503, 0x0805, 0x0501, 0x0806, 0x0601),
            listOf("h2", "http/1.1"),
            "1.3"
        ),
        TlsFingerprint(
            listOf(0x1301, 0x1302, 0x1303, 0xC02B, 0xC02F, 0xC02C, 0xC030, 0xCCA9, 0xCCA8, 0xCCAA, 0x009C, 0x009D, 0x002F, 0x0035),
            listOf(0x001D, 0x0017, 0x0018, 0x001C, 0x001B),
            listOf(0x0403, 0x0503, 0x0603, 0x0804, 0x0805, 0x0806, 0x0401, 0x0501, 0x0601),
            listOf("h2", "http/1.1"),
            "1.3"
        ),
        TlsFingerprint(
            listOf(0xC009, 0xC00A, 0xC013, 0xC014, 0x0033, 0x0039, 0x002F, 0x0035, 0x000A),
            listOf(0x0017, 0x0018, 0x001D),
            listOf(0x0401, 0x0501, 0x0601, 0x0403, 0x0503, 0x0603),
            listOf("http/1.1"),
            "1.2"
        ),
    )

    fun randomFingerprint(): TlsFingerprint = fingerprints[rng.nextInt(fingerprints.size)]

    fun sslContextWithRandomFingerprint(): SSLContext {
        val fp = randomFingerprint()
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, null, null)
        return ctx
    }

    fun applyFingerprintToSocket(socket: SSLSocket, fingerprint: TlsFingerprint? = null) {
        val fp = fingerprint ?: randomFingerprint()
        try {
            socket.enabledCipherSuites = fp.cipherSuites.mapNotNull {
                try { SSLContext.getDefault().socketFactory.supportedCipherSuites.find { suite ->
                    suite.contains(it.toString(16), ignoreCase = true)
                } } catch (e: Exception) { null }
            }.toTypedArray()
            socket.enabledProtocols = arrayOf("TLSv1.3", "TLSv1.2")
            Log.d(TAG, "TLS fingerprint applied: ${fp.tlsVersion} ${fp.cipherSuites.size} suites")
        } catch (e: Exception) {
            Log.w(TAG, "Fingerprint apply error: ${e.message}")
        }
    }

    fun obfuscateHttpHeaders(headers: Map<String, String>): Map<String, String> {
        val result = headers.toMutableMap()
        result["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
        result["Accept-Language"] = listOf("en-US,en;q=0.9", "en;q=0.8", "fr;q=0.7", "de;q=0.6")[rng.nextInt(4)]
        result["Accept-Encoding"] = "gzip, deflate, br"
        result["Cache-Control"] = "no-cache"
        result["Pragma"] = "no-cache"
        result["Upgrade-Insecure-Requests"] = "1"
        if (rng.nextBoolean()) {
            result["DNT"] = if (rng.nextBoolean()) "1" else "0"
        }
        return result
    }

    fun randomPadding(min: Int = 16, max: Int = 512): ByteArray {
        val len = rng.nextInt(max - min) + min
        val padding = ByteArray(len)
        rng.nextBytes(padding)
        return padding
    }

    fun morphTlsClientHello(data: ByteArray): ByteArray {
        if (data.size < 5 || data[0] != 0x16.toByte()) return data
        val modified = data.copyOf()
        val sessionIdLenPos = 38 + (data[35].toInt() and 0xFF) * 2 + 2
        if (sessionIdLenPos < modified.size) {
            val origLen = modified[sessionIdLenPos].toInt() and 0xFF
            val newLen = rng.nextInt(32) + 1
            modified[sessionIdLenPos] = newLen.toByte()
            val diff = newLen - origLen
            if (diff > 0 && sessionIdLenPos + 1 + newLen <= modified.size) {
                val newSessionId = ByteArray(newLen)
                rng.nextBytes(newSessionId)
                System.arraycopy(newSessionId, 0, modified, sessionIdLenPos + 1, newLen)
            }
        }
        return modified
    }

    fun normalizePacketSize(data: ByteArray, blockSize: Int = 128): ByteArray {
        val remainder = data.size % blockSize
        if (remainder == 0) return data
        val padding = ByteArray(blockSize - remainder)
        rng.nextBytes(padding)
        return data + padding
    }

    fun addTimingJitter(baseDelay: Long = 50): Long {
        return baseDelay + rng.nextInt(150).toLong()
    }

    fun destroy() { instance = null }
}
