package com.n3.app.services

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.n3.app.MainActivity
import com.n3.app.N3App
import kotlinx.coroutines.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class TorService : Service() {
    companion object {
        const val SOCKS_PORT = 9050
        const val CONTROL_PORT = 9051
        private const val NOTIF_ID = 1001
        private const val TAG = "N3/Tor"
    }

    inner class TorBinder : Binder() { fun getService(): TorService = this@TorService }

    private val binder = TorBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var torProcess: Process? = null
    @Volatile var isRunning = false; private set
    @Volatile var onionAddress = ""

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, N3App.CH_TOR)
            .setContentTitle("N3 Tor").setContentText("Starting...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build())
        scope.launch { startTor() }
    }

    private suspend fun startTor() = withContext(Dispatchers.IO) {
        try {
            val torBinary = findTorBinary() ?: run {
                Log.w(TAG, "No tor binary, trying system tor")
                trySystemTor(); return@withContext
            }
            val dataDir = File(filesDir, "tor_data").also { it.mkdirs() }
            val hsDir = File(filesDir, "hidden_service").also { it.mkdirs() }
            val torrcFile = File(dataDir, "torrc")
            torrcFile.writeText(buildString {
                appendLine("SocksPort 127.0.0.1:$SOCKS_PORT")
                appendLine("ControlPort 127.0.0.1:$CONTROL_PORT")
                appendLine("DataDirectory $dataDir")
                appendLine("HiddenServiceDir $hsDir")
                appendLine("HiddenServicePort 5223 127.0.0.1:5223")
                appendLine("ExitNodes {de},{nl},{se},{ch}")
                appendLine("StrictNodes 0")
                appendLine("SafeLogging 1")
                appendLine("Log notice file $dataDir/tor.log")
                appendLine("AvoidDiskWrites 1")
            })
            val pb = ProcessBuilder(torBinary.absolutePath, "-f", torrcFile.absolutePath)
            pb.directory(dataDir)
            pb.environment()["HOME"] = dataDir.absolutePath
            pb.redirectErrorStream(true)
            torProcess = pb.start()
            scope.launch {
                torProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.forEachLine { Log.d(TAG, "tor: $it") }
                }
            }
            waitForPort(CONTROL_PORT, 30000)
            val hostnameFile = File(hsDir, "hostname")
            if (hostnameFile.exists()) onionAddress = hostnameFile.readText().trim()
            isRunning = true
            updateNotification("Tor · $onionAddress")
            Log.i(TAG, "Tor ready: SOCKS5:$SOCKS_PORT onion:$onionAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Tor start failed: ${e.message}")
            torProcess?.destroyForcibly(); torProcess = null
            updateNotification("Tor failed")
        }
    }

    private fun trySystemTor(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", SOCKS_PORT), 3000) }
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", CONTROL_PORT), 2000) }
        isRunning = true
        val hsDir = File(filesDir, "hidden_service")
        val f = File(hsDir, "hostname")
        if (f.exists()) onionAddress = f.readText().trim()
        updateNotification("Tor (system)")
        Log.i(TAG, "System Tor connected")
        true
    } catch (e: Exception) { updateNotification("Tor unavailable"); false }

    private fun findTorBinary(): File? {
        val nativeDir = applicationInfo.nativeLibraryDir
        listOf(
            File(nativeDir, "libtor.so"),
            File(getFilesDir(), "lib/libtor.so"),
            File("/system/bin/tor"),
            File(filesDir, "tor")
        ).forEach { if (it.exists()) return it }
        return null
    }

    private fun waitForPort(port: Int, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }; return }
            catch (e: Exception) { Thread.sleep(500) }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, NotificationCompat.Builder(this, N3App.CH_TOR)
            .setContentTitle("N3 Tor").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build())
    }

    override fun onDestroy() {
        torProcess?.destroy()
        torProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        torProcess?.destroyForcibly()
        scope.cancel()
        super.onDestroy()
    }
}
