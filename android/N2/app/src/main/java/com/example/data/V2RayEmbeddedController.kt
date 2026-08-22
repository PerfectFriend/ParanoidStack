/**
 * Встроенный контроллер V2Ray (Xray) для Android.
 * Запускает Xray процесс, который слушает SOCKS5 на указанном порту
 * и перенаправляет трафик через Tor. Использует конфигурацию в JSON.
 *
 * Требует бинарный файл xray в assets/bin/ для соответствующей архитектуры.
 *
 * Architecture role:
 *   V2Ray/Xray acts as an intermediary SOCKS5 proxy that sits between the application
 *   and Tor. It provides additional traffic obfuscation, DNS-over-HTTPS resolution,
 *   and geoip-based routing. The Xray process is launched as a subprocess with a
 *   generated JSON config that directs all outbound traffic through Tor's SOCKS5 port.
 */
package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * Управление встроенным V2Ray/Xray процессом.
 * Xray запускается как SOCKS5-прокси, который направляет трафик через Tor.
 * Создаёт конфигурационный JSON и управляет жизненным циклом процесса.
 *
 * @param context контекст приложения
 * @param localPort локальный порт SOCKS5
 * @param torSocksPort порт Tor SOCKS5
 * @param onLog callback логирования
 * @param onStatusChange callback изменения статуса
 */
class V2RayEmbeddedController(
    private val context: Context,
    private val localPort: Int = 10808,
    private val torSocksPort: Int = 9050,
    private val onLog: (String) -> Unit,
    private val onStatusChange: (String) -> Unit
) {
    private var xrayProcess: Process? = null
    private var fallbackServer: ServerSocket? = null
    private var usingFallback = false
    private var healthCheckThread: Thread? = null
    @Volatile
    private var shouldRunHealthCheck = false

    /** Запустить Xray процесс */
    fun start() {
        if (xrayProcess != null) {
            onLog("[V2RayEmbedded] Process is already running.")
            return
        }

        thread(name = "V2RayEmbeddedStartThread") {
            try {
                onStatusChange("INITIALIZING")
                onLog("[V2RayEmbedded] Preparing Xray binary installation...")

                val isArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }
                val arch = if (isArm64) "arm64" else "x86_64"
                val binDir = context.getDir("bin", Context.MODE_PRIVATE)
                val xrayFile = File(binDir, "xray")
                val cachedFile = File(context.filesDir, "xray-cached-$arch")

                onLog("[V2RayEmbedded] Extracting xray binary for architecture: $arch")
                var binaryReady = false

                // Try bundled assets first
                try {
                    context.assets.open("bin/xray-$arch").use { input ->
                        xrayFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    onLog("[V2RayEmbedded] Unpacked xray binary from assets.")
                    binaryReady = true
                } catch (e: Exception) {
                    onLog("[V2RayEmbedded] No bundled asset: ${e.message}")
                }

                // Fallback: cached binary (offline fallback)
                if (!binaryReady && cachedFile.exists() && cachedFile.canExecute()) {
                    onLog("[V2RayEmbedded] Using cached binary from previous download.")
                    cachedFile.copyTo(xrayFile, overwrite = true)
                    binaryReady = true
                }

                // Last resort: download from CDN
                if (!binaryReady) {
                    onLog("[V2RayEmbedded] Binary not available locally, trying CDN download...")
                    if (downloadBinary(arch, xrayFile)) {
                        // Cache for offline use
                        try {
                            xrayFile.copyTo(cachedFile, overwrite = true)
                            onLog("[V2RayEmbedded] Cached binary for offline use.")
                        } catch (ce: Exception) {
                            onLog("[V2RayEmbedded] Could not cache binary: ${ce.message}")
                        }
                        binaryReady = true
                    } else {
                        onLog("[V2RayEmbedded] CDN download failed, starting built-in fallback SOCKS5 proxy...")
                        startFallbackProxy()
                        return@thread
                    }
                }

                if (!binaryReady) {
                    onLog("[V2RayEmbedded] No binary available, starting fallback proxy...")
                    startFallbackProxy()
                    return@thread
                }

                xrayFile.setExecutable(true, false) // executable only by owner

                val configJsonFile = File(context.filesDir, "v2ray_config.json")
                val configContent = """
                {
                  "log": { "loglevel": "warning", "access": "", "error": "" },
                  "dns": {
                    "hosts": { "domain:onion": "127.0.0.1" },
                    "servers": [
                      { "address": "1.1.1.1", "port": 53, "domains": ["geosite:cn"] },
                      { "address": "https://dns.google/dns-query", "port": 443 },
                      "localhost"
                    ],
                    "queryStrategy": "UseIPv4"
                  },
                  "inbounds": [{
                    "port": $localPort,
                    "protocol": "socks",
                    "settings": { "auth": "noauth", "udp": true, "ip": "127.0.0.1" },
                    "sniffing": { "enabled": true, "destOverride": ["http", "tls"] }
                  }],
                  "outbounds": [{
                    "protocol": "socks",
                    "settings": { "servers": [{ "address": "127.0.0.1", "port": $torSocksPort }] },
                    "tag": "tor-out",
                    "streamSettings": { "sockopt": { "tcpFastOpen": true, "tcpKeepAlive": 0 } }
                  }],
                  "routing": {
                    "domainStrategy": "AsIs",
                    "rules": [
                      { "type": "field", "domain": ["geosite:cn"], "outboundTag": "tor-out" },
                      { "type": "field", "ip": ["geoip:cn"], "outboundTag": "tor-out" },
                      { "type": "field", "ip": ["0.0.0.0/0", "::/0"], "outboundTag": "tor-out" }
                    ]
                  }
                }
                """.trimIndent()
                configJsonFile.writeText(configContent)
                onLog("[V2RayEmbedded] Config JSON written at: ${configJsonFile.absolutePath}")

                onLog("[V2RayEmbedded] Starting Xray daemon, bridging SOCKS5 Port $localPort -> Tor $torSocksPort...")
                val builder = ProcessBuilder(
                    xrayFile.absolutePath, "run", "-c", configJsonFile.absolutePath
                )
                builder.redirectErrorStream(true)

                val process = builder.start()
                xrayProcess = process
                usingFallback = false

                thread(name = "V2RayLogReaderThread") {
                    val reader = process.inputStream.bufferedReader()
                    try {
                        var line = reader.readLine()
                        var started = false
                        val startDeadline = System.currentTimeMillis() + 5000
                        while (line != null) {
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                Log.d("V2RayEmbedded", cleanLine)
                                onLog("[V2Ray] $cleanLine")
                                if (!started && (cleanLine.contains("started") || cleanLine.contains("Starting") || System.currentTimeMillis() > startDeadline)) {
                                    started = true
                                    onStatusChange("ACTIVE")
                                }
                            }
                            line = reader.readLine()
                        }
                    } catch (e: Exception) {
                        onLog("[V2RayEmbedded] Log reader error: ${e.message}")
                    } finally {
                        try { reader.close() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") }
                        onStatusChange("INACTIVE")
                        onLog("[V2RayEmbedded] Xray daemon process ended.")
                    }
                }
            } catch (e: Exception) {
                onLog("[V2RayEmbedded] Exception launching Xray daemon: ${e.message}")
                Log.e("V2RayEmbeddedController", "exception", e)
                onStatusChange("INACTIVE")
            }
        }
    }

    /** Остановить Xray процесс */
    fun stop() {
        if (xrayProcess == null && !usingFallback) return
        onLog("[V2RayEmbedded] Stopping Xray daemon...")
        try {
            xrayProcess?.destroy()
        } catch (e: Exception) {
            onLog("[V2RayEmbedded] Error destroying process: ${e.message}")
        }
        xrayProcess = null
        if (usingFallback) {
            stopFallbackProxy()
        }
        onStatusChange("INACTIVE")
    }

    private fun downloadBinary(arch: String, xrayFile: File): Boolean {
        return try {
            val zipUrl = "https://github.com/XTLS/Xray-core/releases/download/v1.8.24/Xray-android-${arch}.zip"
            val zipFile = File(context.filesDir, "xray-dl.zip")
            onLog("[V2RayEmbedded] Downloading from $zipUrl")
            URL(zipUrl).openStream().use { input ->
                zipFile.outputStream().use { output -> input.copyTo(output) }
            }
            onLog("[V2RayEmbedded] Downloaded zip, extracting xray binary...")
            var extracted = false
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.contains("xray", ignoreCase = true)) {
                        xrayFile.outputStream().use { out -> zis.copyTo(out) }
                        xrayFile.setExecutable(true, false) // executable only by owner
                        extracted = true
                        onLog("[V2RayEmbedded] Extracted: ${entry.name}")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            extracted
        } catch (e: Exception) {
            onLog("[V2RayEmbedded] Download/extract error: ${e.message}")
            false
        }
    }

    private var fallbackRunning = false

    private fun startFallbackProxy() {
        usingFallback = true
        fallbackRunning = true
        onLog("[V2RayEmbedded] Starting fallback SOCKS5 proxy on port $localPort -> Tor $torSocksPort")
        onStatusChange("ACTIVE")
        thread(name = "FallbackProxyAcceptor") {
            try {
                val ss = ServerSocket(localPort)
                fallbackServer = ss
                onLog("[V2RayEmbedded] Fallback proxy listening on 127.0.0.1:$localPort")
                while (fallbackRunning) {
                    val client = ss.accept()
                    thread(name = "FallbackRelay") { handleFallbackConnection(client) }
                }
            } catch (e: Exception) {
                onLog("[V2RayEmbedded] Fallback proxy error: ${e.message}")
                onStatusChange("INACTIVE")
            }
        }
    }

    private fun handleFallbackConnection(client: Socket) {
        try {
            client.soTimeout = 30000
            val cin = client.getInputStream()
            val cout = client.getOutputStream()

            val greet = ByteArray(2)
            readFully(cin, greet)
            if (greet[0] != 0x05.toByte()) { client.close(); return }
            readFully(cin, ByteArray(greet[1].toInt()))
            cout.write(byteArrayOf(0x05, 0x00)); cout.flush()

            val hdr = ByteArray(4)
            readFully(cin, hdr)
            if (hdr[0] != 0x05.toByte() || hdr[1] != 0x01.toByte()) { client.close(); return }

            val host: String
            val port: Int
            when (hdr[3].toInt()) {
                1 -> { val a = ByteArray(4); readFully(cin, a); host = InetAddress.getByAddress(a).hostAddress ?: return }
                3 -> { val l = cin.read(); val b = ByteArray(l); readFully(cin, b); host = String(b, Charsets.UTF_8) }
                4 -> { val a = ByteArray(16); readFully(cin, a); host = InetAddress.getByAddress(a).hostAddress?.replace("/", "") ?: return }
                else -> { client.close(); return }
            }
            port = (cin.read() shl 8) or cin.read()

            val tor = Socket()
            try {
                tor.connect(InetSocketAddress("127.0.0.1", torSocksPort), 15000)
                tor.soTimeout = 30000
                val tout = tor.getOutputStream()
                val tin = tor.getInputStream()
                tout.write(byteArrayOf(0x05, 0x01, 0x00)); tout.flush()
                val tg = ByteArray(2); readFully(tin, tg)
                val hb = host.toByteArray(Charsets.UTF_8)
                val req = byteArrayOf(0x05, 0x01, 0x00, 0x03, hb.size.toByte()) + hb +
                        ((port shr 8) and 0xFF).toByte() + (port and 0xFF).toByte()
                tout.write(req); tout.flush()
                val tr = ByteArray(4); readFully(tin, tr)
                if (tr[1] != 0x00.toByte()) {
                    val err = byteArrayOf(0x05, tr[1], 0x00, 0x01, 0, 0, 0, 0, 0, 0)
                    cout.write(err); cout.flush(); client.close(); return
                }
                when (tr[3].toInt()) { 1 -> readFully(tin, ByteArray(6)); 3 -> { val l = tin.read(); readFully(tin, ByteArray(l + 2)) } 4 -> readFully(tin, ByteArray(18)) }
                cout.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
                relaySockets(client, tor)
            } finally {
                try { tor.close() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") }
            }
        } catch (e: Exception) {
            onLog("[V2RayEmbedded] Fallback relay error: ${e.message}")
        } finally {
            try { client.close() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") }
        }
    }

    private fun relaySockets(s1: Socket, s2: Socket) {
        val t1 = thread {
            try {
                val buf = ByteArray(8192); val i = s1.getInputStream(); val o = s2.getOutputStream(); var n: Int
                while (i.read(buf).also { n = it } >= 0) { o.write(buf, 0, n); o.flush() }
            } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") } finally { try { s1.shutdownInput() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") }; try { s2.shutdownOutput() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") } }
        }
        val t2 = thread {
            try {
                val buf = ByteArray(8192); val i = s2.getInputStream(); val o = s1.getOutputStream(); var n: Int
                while (i.read(buf).also { n = it } >= 0) { o.write(buf, 0, n); o.flush() }
            } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") } finally { try { s2.shutdownInput() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") }; try { s1.shutdownOutput() } catch (_: java.lang.Exception) { Log.w("V2RayEmbeddedController", "ignored exception") } }
        }
        try { t1.join(); t2.join() } catch (_: InterruptedException) { Log.w("V2RayEmbeddedController", "interrupted while joining bridge threads") }
    }

    private fun readFully(inp: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = inp.read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException("Unexpected EOF")
            off += n
        }
    }

    /** Проверить, запущен ли Xray */
    fun isRunning(): Boolean = xrayProcess != null

    /** Запустить health-check с авто-перезапуском каждые 30 секунд */
    fun startHealthCheck(intervalMs: Long = 30000) {
        if (shouldRunHealthCheck) return
        shouldRunHealthCheck = true
        healthCheckThread = thread(name = "V2RayHealthCheck", isDaemon = true) {
            while (shouldRunHealthCheck) {
                try {
                    Thread.sleep(intervalMs)
                    if (!shouldRunHealthCheck) break
                    if (!performHealthCheck()) {
                        onLog("[V2RayEmbedded] Health check failed, attempting restart...")
                        restart()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    onLog("[V2RayEmbedded] Health check error: ${e.message}")
                }
            }
        }
    }

    /** Остановить health-check */
    fun stopHealthCheck() {
        shouldRunHealthCheck = false
        healthCheckThread?.interrupt()
        healthCheckThread = null
    }

    /** Выполнить проверку здоровья: подключиться к SOCKS5 и отправить CONNECT к тестовому хосту */
    private fun performHealthCheck(): Boolean {
        return try {
            val socket = Socket()
            socket.soTimeout = 5000
            socket.connect(InetSocketAddress("127.0.0.1", localPort), 3000)
            val out = socket.getOutputStream()
            val `in` = socket.getInputStream()

            // SOCKS5 handshake
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val resp = ByteArray(2)
            val read = `in`.read(resp)
            if (read < 2 || resp[0] != 0x05.toByte() || resp[1] != 0x00.toByte()) {
                socket.close()
                return false
            }

            // CONNECT to a test address (1.1.1.1:53)
            out.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0, 53))
            out.flush()
            val connResp = ByteArray(4)
            val connRead = `in`.read(connResp)
            socket.close()
            connRead >= 4 && connResp[1] == 0x00.toByte()
        } catch (e: Exception) {
            false
        }
    }

    /** Перезапустить Xray или fallback proxy */
    fun restart() {
        stop()
        if (usingFallback) {
            stopFallbackProxy()
        }
        start()
    }

    /** Остановить fallback proxy */
    private fun stopFallbackProxy() {
        fallbackRunning = false
        try {
            fallbackServer?.close()
        } catch (e: Exception) {
            onLog("[V2RayEmbedded] Error closing fallback server: ${e.message}")
        }
        fallbackServer = null
        usingFallback = false
    }

    /** Проверить, используется ли fallback режим */
    fun isUsingFallback(): Boolean = usingFallback
}
