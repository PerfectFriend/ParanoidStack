package com.paranoidx.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ParanoidGuard - Security Monitor Service
 * Monitors: Network traffic, Process integrity, File system changes
 * Alerts via notification and MatrixKeyboard status LED
 */
class ParanoidGuardService : Service(), LifecycleEventObserver {

    private val TAG = "ParanoidGuard"
    private val coroutineJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + coroutineJob)
    private val networkMonitorJob = Job()
    private val processMonitorJob = Job()
    private val fileSystemMonitorJob = Job()
    private val alertJob = Job()

    private val knownProcesses = ConcurrentHashMap<String, ProcessInfo>()
    private val knownFiles = ConcurrentHashMap<String, FileInfo>()
    private val networkConnections = ConcurrentHashMap<String, NetworkConnection>()
    private val isMonitoring = AtomicBoolean(false)
    private var notificationManager: NotificationManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "paranoid_guard_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_INTENT_ACTION = "com.paranoidx.guard.ALERT"
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "ParanoidGuard Service created")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring.getAndSet(true)) {
            startMonitoring()
            showNotification("ParanoidGuard Active", "Monitoring: Network, Processes, Filesystem")
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startNetworkMonitoring()
        startProcessMonitoring()
        startFileSystemMonitoring()
        startAlertProcessor()
        Log.d(TAG, "All monitoring started")
    }

    private fun startNetworkMonitoring() {
        coroutineScope.launch(networkMonitorJob) {
            // Register network callback for real-time network changes
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    logNetworkEvent("NETWORK_AVAILABLE", network.toString())
                    analyzeNetwork(network)
                }

                override fun onLost(network: Network) {
                    logNetworkEvent("NETWORK_LOST", network.toString())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transport = when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                        else -> "UNKNOWN"
                    }
                    logNetworkEvent("CAPABILITIES_CHANGED", "$transport: $networkCapabilities")
                    checkForSuspiciousNetwork(network, networkCapabilities)
                }
            }

            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)

            // Periodic network analysis
            while (isMonitoring.get()) {
                analyzeAllNetworks()
                delay(30_000) // Every 30 seconds
            }
        }
    }

    private fun analyzeAllNetworks() {
        try {
            val networks = connectivityManager?.allNetworks ?: return
            for (network in networks) {
                val capabilities = connectivityManager?.getNetworkCapabilities(network)
                capabilities?.let { checkForSuspiciousNetwork(network, it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network analysis error: ${e.message}")
        }
    }

    private fun analyzeNetwork(network: Network) {
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        capabilities?.let { checkForSuspiciousNetwork(network, it) }
    }

    private fun checkForSuspiciousNetwork(network: Network, capabilities: NetworkCapabilities) {
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }

        val connKey = "$network-$transport"
        val existing = networkConnections.getOrPut(connKey) {
            NetworkConnection(network, transport, System.currentTimeMillis())
        }

        // Alert on unexpected VPN/proxy detection
        if (transport == "VPN" && !existing.vpnAlerted) {
            existing.vpnAlerted = true
            triggerAlert("SUSPICIOUS_NETWORK", "Unexpected VPN/Proxy detected: $transport", AlertSeverity.HIGH)
        }

        // Check for captive portal / MITM indicators
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            triggerAlert("CAPTIVE_PORTAL", "Captive portal detected on $transport", AlertSeverity.MEDIUM)
        }
    }

    private fun startProcessMonitoring() {
        coroutineScope.launch(processMonitorJob) {
            // Initial baseline
            scanProcesses()

            while (isMonitoring.get()) {
                scanProcesses()
                delay(10_000) // Every 10 seconds
            }
        }
    }

    private fun scanProcesses() {
        try {
            val currentProcesses = mutableMapOf<String, ProcessInfo>()

            // Read /proc for process info
            File("/proc").listFiles()?.forEach { file ->
                if (file.name.matches(Regex("\\d+"))) {
                    val pid = file.name.toIntOrNull() ?: return@forEach
                    val cmdline = File("/proc/$pid/cmdline").readText().replace('\u0000', ' ').trim()
                    val stat = File("/proc/$pid/stat").readText().split(" ")
                    
                    if (cmdline.isNotBlank()) {
                        val ppid = stat.getOrNull(3)?.toIntOrNull() ?: 0
                        val uid = File("/proc/$pid/status").readText()
                            .lines()
                            .firstOrNull { it.startsWith("Uid:") }
                            ?.split("\\s+".toRegex())
                            ?.getOrNull(1)
                            ?.toIntOrNull() ?: 0

                        val info = ProcessInfo(pid, cmdline, ppid, uid, System.currentTimeMillis())
                        currentProcesses[cmdline] = info

                        // Check for new suspicious processes
                        val known = knownProcesses[cmdline]
                        if (known == null) {
                            checkProcessSuspicious(info)
                        } else if (known.ppid != ppid || known.uid != uid) {
                            triggerAlert("PROCESS_ANOMALY", "Process changed: $cmdline (PPID: ${known.ppid}->$ppid, UID: ${known.uid}->$uid)", AlertSeverity.MEDIUM)
                        }
                    }
                }
            }

            // Check for terminated critical processes
            knownProcesses.keys.forEach { key ->
                if (!currentProcesses.containsKey(key) && isCriticalProcess(key)) {
                    triggerAlert("CRITICAL_PROCESS_DIED", "Critical process terminated: $key", AlertSeverity.HIGH)
                }
            }

            knownProcesses.clear()
            knownProcesses.putAll(currentProcesses)

        } catch (e: Exception) {
            Log.w(TAG, "Process scan error: ${e.message}")
        }
    }

    private fun checkProcessSuspicious(info: ProcessInfo) {
        val suspiciousPatterns = listOf(
            "su", "magisk", "supolicy", "frida", "xposed", "lsposed",
            "tcpdump", "wireshark", "tshark", "nmap", "netcat", "nc ",
            "socat", "strace", "ltrace", "gdb", "ida", "radare2",
            "metasploit", "msfconsole", "beef", "sqlmap", "hydra"
        )

        val cmdlineLower = info.cmdline.lowercase()
        for (pattern in suspiciousPatterns) {
            if (cmdlineLower.contains(pattern)) {
                triggerAlert("SUSPICIOUS_PROCESS", "Detected: ${info.cmdline} (PID: ${info.pid})", AlertSeverity.HIGH)
                break
            }
        }

        // Check for root shells
        if (info.uid == 0 && info.cmdline.contains("sh")) {
            triggerAlert("ROOT_SHELL", "Root shell detected: ${info.cmdline}", AlertSeverity.CRITICAL)
        }
    }

    private fun isCriticalProcess(cmdline: String): Boolean {
        val critical = listOf("system_server", "surfaceflinger", "zygote", "audioserver", "cameraserver", "mediadrmserver")
        return critical.any { cmdline.contains(it) }
    }

    private fun startFileSystemMonitoring() {
        coroutineScope.launch(fileSystemMonitorJob) {
            // Initial baseline for critical directories
            scanCriticalDirectories()

            while (isMonitoring.get()) {
                scanCriticalDirectories()
                delay(60_000) // Every minute
            }
        }
    }

    private fun scanCriticalDirectories() {
        val criticalDirs = listOf(
            "/data/data",
            "/system/bin",
            "/system/xbin",
            "/vendor/bin",
            "/etc",
            "/data/local/tmp"
        )

        for (dir in criticalDirs) {
            try {
                File(dir).walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val key = file.absolutePath
                        val lastModified = file.lastModified()
                        val size = file.length()
                        val hash = calculateFileHash(file)

                        val info = FileInfo(key, lastModified, size, hash, System.currentTimeMillis())
                        val known = knownFiles[key]

                        if (known == null) {
                            knownFiles[key] = info
                        } else if (known.hash != hash || known.lastModified != lastModified || known.size != size) {
                            triggerAlert("FILE_MODIFIED", "File changed: $key (hash: ${known.hash} -> $hash)", AlertSeverity.HIGH)
                            knownFiles[key] = info
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "FS scan error for $dir: ${e.message}")
            }
        }
    }

    private fun calculateFileHash(file: File): String {
        return try {
            val bytes = file.readBytes()
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "ERROR"
        }
    }

    private fun startAlertProcessor() {
        coroutineScope.launch(alertJob) {
            while (isMonitoring.get()) {
                // Process alert queue, send notifications, update LED
                delay(5_000)
            }
        }
    }

    private fun triggerAlert(type: String, message: String, severity: AlertSeverity) {
        Log.w(TAG, "ALERT [$severity] $type: $message")
        
        val alert = Alert(type, message, severity, System.currentTimeMillis(), SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()))
        
        // Send notification
        showNotification("🚨 ParanoidGuard Alert [$severity]", "$type: $message")
        
        // Broadcast for MatrixKeyboard LED
        val intent = Intent(ALERT_INTENT_ACTION).apply {
            putExtra("type", type)
            putExtra("message", message)
            putExtra("severity", severity.name)
            putExtra("timestamp", alert.timestamp)
        }
        sendBroadcast(intent)
    }

    private fun showNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ParanoidGuard Security Monitor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security monitoring alerts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ParanoidGuard::WakeLock")
        wakeLock?.acquire()
    }

    private fun logNetworkEvent(event: String, details: String) {
        Log.d(TAG, "NETWORK [$event] $details")
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            stopMonitoring()
        }
    }

    private fun stopMonitoring() {
        isMonitoring.set(false)
        coroutineJob.cancel()
        networkMonitorJob.cancel()
        processMonitorJob.cancel()
        fileSystemMonitorJob.cancel()
        alertJob.cancel()
        
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        wakeLock?.release()
        Log.d(TAG, "Monitoring stopped")
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        stopMonitoring()
        stopForeground(true)
        super.onDestroy()
        Log.d(TAG, "ParanoidGuard Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Boot receiver to auto-start service
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, ParanoidGuardService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}

// Data classes
data class ProcessInfo(
    val pid: Int,
    val cmdline: String,
    val ppid: Int,
    val uid: Int,
    val lastSeen: Long
)

data class FileInfo(
    val path: String,
    val lastModified: Long,
    val size: Long,
    val hash: String,
    val lastSeen: Long
)

data class NetworkConnection(
    val network: Network,
    val transport: String,
    val firstSeen: Long,
    var vpnAlerted: Boolean = false
)

data class Alert(
    val type: String,
    val message: String,
    val severity: AlertSeverity,
    val timestamp: Long,
    val timeString: String
)

enum class AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}