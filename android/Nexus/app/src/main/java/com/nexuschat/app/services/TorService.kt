package com.nexuschat.app.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexuschat.app.MainActivity
import com.nexuschat.app.NexusChatApp
import com.nexuschat.app.R
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TorService — embedded Tor daemon lifecycle.
 *
 * DESIGN: starts libtor.so as a subprocess with a minimal torrc, manages
 * SOCKS5 (9050) + Control (9051) ports, and exposes a v3 onion hidden service
 * on port 5223. Falls back to Orbot if no embedded binary found.
 *
 * INTEGRATION: started by MainActivity via startForegroundService; status
 * exposed to JS WebView via TorBridge (isRunning, getOnionAddress, etc.).
 * BinaryDownloader auto-fetches libtor.so if not bundled.
 */
class TorService : Service() {
    companion object {
        private const val TAG = "NexusChat/Tor"
        private const val NOTIF_ID = 1001
        const val SOCKS_PORT = 9050
        const val CONTROL_PORT = 9051
        const val HTTP_TUNNEL_PORT = 9080
        const val HIDDEN_SERVICE_PORT = 5223
        const val TOR_BINARY = "libtor.so"
        @Volatile var useBridges = false
    }

    inner class TorBinder : Binder() {
        fun getService(): TorService = this@TorService
    }

    private val binder = TorBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var controlSocket: Socket? = null
    private var controlWriter: java.io.PrintWriter? = null
    private var controlReader: BufferedReader? = null
    private var torProcess: Process? = null
    var isRunning = false
        private set
    @get:JvmName("getOnionAddressProperty")
    var onionAddress = ""
        private set
    @get:JvmName("getSocksPortProperty")
    var socksPort = SOCKS_PORT
        private set
    private val responseQueue = ConcurrentLinkedQueue<String>()
    private var readThread: Thread? = null
    @Volatile private var controlReaderRunning = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Starting Tor daemon..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) scope.launch { startTorDaemon() }
        return START_STICKY
    }

    private suspend fun startTorDaemon() = withContext(Dispatchers.IO) {
        try {
            var torBinary = findTorBinary()
            if (torBinary == null) {
                Log.w(TAG, "No tor binary found, attempting auto-download")
                torBinary = BinaryDownloader.getInstance(this@TorService)
                    .downloadBinary(BinaryDownloader.BinaryType.TOR)
            }
            if (torBinary == null) {
                Log.w(TAG, "No embedded Tor binary — trying Orbot")
                trySystemTor()
                return@withContext
            }
            val dataDir = File(filesDir, "tor_data").also { it.mkdirs() }
            val hsDir = File(filesDir, "hidden_service").also { it.mkdirs() }
            val pidFile = File(dataDir, "tor.pid")
            val torrcFile = File(dataDir, "torrc")
            val bridgeLines = if (useBridges) {
                val orch = BridgeOrchestrator.getInstance(this@TorService)
                orch.getBridgeTorrcLines()
            } else ""
            torrcFile.writeText(buildString {
                appendLine("SocksPort 127.0.0.1:$SOCKS_PORT")
                appendLine("ControlPort 127.0.0.1:$CONTROL_PORT")
                appendLine("DataDirectory $dataDir")
                appendLine("HiddenServiceDir $hsDir")
                appendLine("HiddenServicePort $HIDDEN_SERVICE_PORT 127.0.0.1:$HIDDEN_SERVICE_PORT")
                appendLine("ExitNodes {de},{nl},{se},{ch},{is},{fi}")
                appendLine("StrictNodes 0")
                appendLine("EnforceDistinctSubnets 1")
                appendLine("MaxCircuitDirtiness 600")
                appendLine("NewCircuitPeriod 30")
                appendLine("CircuitBuildTimeout 60")
                appendLine("LearnCircuitBuildTimeout 0")
                appendLine("SafeLogging 1")
                appendLine("Log notice file $dataDir/tor.log")
                appendLine("AvoidDiskWrites 1")
                if (bridgeLines.isNotBlank()) {
                    appendLine(bridgeLines)
                    Log.i(TAG, "Bridges enabled via BridgeOrchestrator: ${bridgeLines.length} chars")
                }
            })
            val pb = ProcessBuilder(
                torBinary.absolutePath,
                "-f", torrcFile.absolutePath,
                "--PidFile", pidFile.absolutePath
            )
            pb.directory(dataDir)
            pb.environment()["HOME"] = dataDir.absolutePath
            pb.redirectErrorStream(true)
            torProcess = pb.start()
            scope.launch {
                try {
                    val reader = torProcess?.inputStream?.bufferedReader() ?: return@launch
                    while (isRunning) {
                        val line = reader.readLine() ?: break
                        Log.d(TAG, "tor: $line")
                    }
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "Tor log reader error: ${e.message}")
                }
            }
            Log.i(TAG, "Tor daemon started, waiting for control port...")
            waitForPort(CONTROL_PORT, 60000)
            connectControlPort()
            val hostnameFile = File(hsDir, "hostname")
            if (hostnameFile.exists()) {
                onionAddress = hostnameFile.readText().trim()
                Log.i(TAG, "Hidden service .onion: $onionAddress")
            }
            val portCheck = try {
                val s = java.net.Socket()
                s.connect(java.net.InetSocketAddress("127.0.0.1", SOCKS_PORT), 2000)
                s.close()
                true
            } catch (e: Exception) { false }
            if (!portCheck) throw RuntimeException("Tor SOCKS5 port $SOCKS_PORT not listening after launch")
            isRunning = true
            socksPort = SOCKS_PORT
            updateNotification("Tor · $onionAddress")
            Log.i(TAG, "Tor daemon fully operational — SOCKS5:$SOCKS_PORT Control:$CONTROL_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Tor start failed: ${e.message}", e)
            isRunning = false
            torProcess?.destroyForcibly()
            torProcess = null
            updateNotification("Tor failed: ${e.message}")
        }
    }

    private fun findTorBinary(): File? {
        val nativeDir = applicationInfo.nativeLibraryDir
        val torLib = File(nativeDir, TOR_BINARY)
        if (torLib.exists()) return torLib
        val altLibDir = File(getFilesDir(), "lib")
        val altTor = File(altLibDir, "libtor.so")
        if (altTor.exists()) return altTor
        val systemTor = File("/system/bin/tor")
        if (systemTor.exists()) return systemTor
        val dataTor = File(filesDir, "tor")
        if (dataTor.exists()) return dataTor
        return null
    }

    private fun trySystemTor(): Boolean {
        Log.w(TAG, "No embedded Tor binary — trying external Orbot")
        try {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 3000)
            s.close()
            val cs = Socket()
            cs.connect(InetSocketAddress("127.0.0.1", CONTROL_PORT), 2000)
            cs.close()
            connectControlPort()
            isRunning = true
            socksPort = SOCKS_PORT
            val hsDir = File(filesDir, "hidden_service")
            val hostnameFile = File(hsDir, "hostname")
            if (hostnameFile.exists()) {
                onionAddress = hostnameFile.readText().trim()
            } else {
                onionAddress = ""
            }
            updateNotification("Tor via Orbot")
            Log.i(TAG, "Connected to external Orbot Tor daemon")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "External Tor not available: ${e.message}")
            isRunning = false
            updateNotification("Tor unavailable")
            return false
        }
    }

    private fun waitForPort(port: Int, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 500)
                    return
                }
            } catch (e: Exception) {
                Thread.sleep(500)
            }
        }
    }

    private fun closeControlConnection() {
        controlReaderRunning = false
        try { controlWriter?.close() } catch (_: Exception) {}
        try { controlReader?.close() } catch (_: Exception) {}
        try { controlSocket?.close() } catch (_: Exception) {}
        readThread?.join(1000)
        controlSocket = null
        controlWriter = null
        controlReader = null
        readThread = null
    }

    private fun connectControlPort() {
        try {
            val sock = Socket("127.0.0.1", CONTROL_PORT)
            controlSocket = sock
            controlWriter = java.io.PrintWriter(sock.getOutputStream(), true)
            controlReader = BufferedReader(InputStreamReader(sock.getInputStream()))
            controlReaderRunning = true
            readThread = Thread {
                try {
                    var line: String?
                    while (controlReaderRunning) {
                        line = controlReader?.readLine() ?: break
                        responseQueue.offer(line)
                    }
                } catch (e: Exception) { Log.d(TAG, "Control read stopped: ${e.message}") }
            }.apply {
                isDaemon = true
                start()
            }
            sendControlCommand("AUTHENTICATE")
            Thread.sleep(200)
            sendControlCommand("TAKEOWNERSHIP")
            Log.i(TAG, "Tor control port authenticated")
        } catch (e: Exception) {
            Log.e(TAG, "Control port connection failed: ${e.message}")
            closeControlConnection()
        }
    }

    private fun sendControlCommand(cmd: String) {
        try {
            controlWriter?.println(cmd)
            controlWriter?.flush()
        } catch (e: Exception) { Log.w(TAG, "Control cmd failed: ${e.message}") }
    }

    fun newCircuit(): Boolean {
        return try {
            sendControlCommand("SIGNAL NEWNYM")
            Log.i(TAG, "NEWNYM sent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "NEWNYM failed: ${e.message}")
            false
        }
    }

    fun getCircuitStatus(): String {
        sendControlCommand("GETINFO circuit-status")
        Thread.sleep(100)
        val lines = mutableListOf<String>()
        while (true) {
            val line = responseQueue.poll() ?: break
            lines.add(line)
        }
        return lines.joinToString("\n")
    }

    @JvmName("getSocksPortValue")
    fun getSocksPort(): Int = socksPort

    @JvmName("getOnionAddressValue")
    fun getOnionAddress(): String = onionAddress

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NexusChatApp.CH_TOR)
            .setContentTitle("NexusChat Tor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { sendControlCommand("SIGNAL SHUTDOWN") } catch (_: Exception) {}
        torProcess?.destroy()
        torProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        torProcess?.destroyForcibly()
        closeControlConnection()
        scope.cancel()
        Log.i(TAG, "TorService destroyed")
    }
}
