package com.paranoidx.sdk.transport

import com.paranoidx.sdk.security.SdkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

object TransportObfuscator {
    private const val TAG = "TransportObfuscator"

    enum class ObfuscationMode {
        TLS_ONLY, TLS_PADDING, FULL_OBFUSCATION, MIMIC_HTTP2
    }

    private val _mode = MutableStateFlow(ObfuscationMode.FULL_OBFUSCATION)
    val mode: StateFlow<ObfuscationMode> = _mode.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _bytesObfuscated = MutableStateFlow(0L)
    val bytesObfuscated: StateFlow<Long> = _bytesObfuscated.asStateFlow()

    private val _obfuscationLogs = MutableStateFlow<List<String>>(emptyList())
    val obfuscationLogs: StateFlow<List<String>> = _obfuscationLogs.asStateFlow()

    private const val PADDING_MIN = 32
    private const val PADDING_MAX = 1024
    private const val TIMING_DELAY_MIN = 1
    private const val TIMING_DELAY_MAX = 50

    private val random = SecureRandom()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val domainFrontingTargets = listOf(
        "www.google.com", "www.cloudflare.com",
        "www.microsoft.com", "www.github.com"
    )

    fun wrapOutputStream(originalOutput: OutputStream, targetHost: String = ""): OutputStream {
        return ObfuscatedOutputStream(originalOutput, targetHost)
    }

    fun wrapInputStream(originalInput: InputStream): InputStream {
        return DeobfuscatedInputStream(originalInput)
    }

    fun createObfuscatedConnection(host: String, port: Int, sniHostname: String? = null): SSLSocket {
        val useSni = sniHostname ?: domainFrontingTargets.random()
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, arrayOf(createTrustAllManager()), SecureRandom())
        val factory = sslContext.socketFactory
        val tlsSocket = factory.createSocket() as SSLSocket
        tlsSocket.connect(InetSocketAddress(host, port), 10000)
        tlsSocket.soTimeout = 30000
        try {
            val sslParams = tlsSocket.sslParameters
            val sniMethod = sslParams.javaClass.getMethod("setServerNames", List::class.java)
            val sniHostClass = Class.forName("javax.net.ssl.SNIHostName")
            val sni = sniHostClass.getConstructor(String::class.java).newInstance(useSni)
            sniMethod.invoke(sslParams, listOf(sni))
            tlsSocket.sslParameters = sslParams
        } catch (e: Exception) {
            SdkLogger.w(TAG, "SNI setting failed: ${e.message}")
        }
        tlsSocket.startHandshake()
        _bytesObfuscated.value = 0L
        _isActive.value = true
        addLog("TLS connection to $host:$port (SNI: $useSni)")
        return tlsSocket
    }

    fun obfuscatePacket(data: ByteArray): ByteArray {
        if (!_isActive.value) return data
        val paddingSize = if (_mode.value >= ObfuscationMode.TLS_PADDING)
            PADDING_MIN + random.nextInt(PADDING_MAX - PADDING_MIN + 1) else 0
        return if (paddingSize > 0) {
            val padding = ByteArray(paddingSize); random.nextBytes(padding)
            val bos = ByteArrayOutputStream()
            bos.write(0x17); bos.write(0x03); bos.write(0x03)
            val length = data.size + paddingSize
            bos.write((length shr 8) and 0xFF); bos.write(length and 0xFF)
            bos.write(data); bos.write(padding)
            _bytesObfuscated.value += data.size.toLong()
            bos.toByteArray()
        } else { _bytesObfuscated.value += data.size.toLong(); data }
    }

    fun deobfuscatePacket(data: ByteArray): ByteArray {
        if (!_isActive.value || data.size < 5) return data
        if (data[0] == 0x17.toByte() && data[1] == 0x03.toByte()) {
            val declaredLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            if (declaredLen > 0 && declaredLen <= data.size - 5) {
                return data.copyOfRange(5, 5 + declaredLen)
            }
        }
        return data
    }

    fun stop() {
        scope.cancel()
    }

    fun setMode(newMode: ObfuscationMode) { _mode.value = newMode; addLog("Mode set to $newMode") }
    fun activate() { _isActive.value = true; addLog("Activated") }
    fun deactivate() { _isActive.value = false; addLog("Deactivated") }

    private class ObfuscatedOutputStream(
        private val original: OutputStream, private val targetHost: String
    ) : OutputStream() {
        override fun write(b: Int) { write(byteArrayOf(b.toByte())) }
        override fun write(b: ByteArray, off: Int, len: Int) {
            val chunk = if (off == 0 && len == b.size) b else b.copyOfRange(off, off + len)
            val obfuscated = obfuscatePacket(chunk)
            if (_mode.value >= ObfuscationMode.FULL_OBFUSCATION) {
                val delay = TIMING_DELAY_MIN + random.nextInt(TIMING_DELAY_MAX - TIMING_DELAY_MIN + 1)
                if (delay > 0) Thread.sleep(delay.toLong())
            }
            synchronized(this) { original.write(obfuscated); original.flush() }
        }
        override fun flush() { original.flush() }
        override fun close() { original.close() }
    }

    private class DeobfuscatedInputStream(private val original: InputStream) : InputStream() {
        private val buffer = ByteArrayOutputStream()
        override fun read(): Int {
            if (buffer.size() > 0) {
                val b = buffer.toByteArray()[0]
                val rest = buffer.toByteArray().copyOfRange(1, buffer.size())
                buffer.reset(); buffer.write(rest); return b.toInt() and 0xFF
            }
            val raw = ByteArray(4096); val n = original.read(raw)
            if (n < 0) return -1
            val deobfuscated = deobfuscatePacket(raw.copyOfRange(0, n))
            if (deobfuscated.isEmpty()) return read()
            buffer.write(deobfuscated.copyOfRange(1, deobfuscated.size))
            return deobfuscated[0].toInt() and 0xFF
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = original.read(b, off, len)
            if (n < 0) return -1
            val deobfuscated = deobfuscatePacket(b.copyOfRange(off, off + n))
            deobfuscated.copyInto(b, off, 0, deobfuscated.size); return deobfuscated.size
        }
        override fun close() { original.close() }
    }

    private fun createTrustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    }

    private fun addLog(msg: String) {
        val newLogs = _obfuscationLogs.value.toMutableList().apply {
            add("[OB ${System.currentTimeMillis() % 100000}] $msg")
        }.takeLast(50)
        if (_obfuscationLogs.value != newLogs) _obfuscationLogs.value = newLogs
        SdkLogger.d(TAG, msg)
    }
}
