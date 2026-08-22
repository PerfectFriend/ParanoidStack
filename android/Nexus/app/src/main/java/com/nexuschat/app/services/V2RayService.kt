package com.nexuschat.app.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.nexuschat.app.MainActivity
import com.nexuschat.app.NexusChatApp
import com.nexuschat.app.R
import kotlinx.coroutines.*
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class V2RayService : Service() {
    companion object {
        private const val TAG = "NexusChat/V2Ray"
        private const val NOTIF_ID = 1003
        const val SOCKS5_PORT = 10808
        const val HTTP_PORT = 10809
        const val API_PORT = 10810
    }

    enum class Protocol {
        VMESS, VLESS, SHADOWSOCKS, TROJAN, SOCKS5, HTTP, DOKODEMI_DOOR, WIREGUARD
    }

    data class V2RayConfig(
        val protocol: Protocol = Protocol.VMESS,
        val address: String = "",
        val port: Int = 443,
        val uuid: String = "",
        val security: String = "auto",
        val encryption: String = "none",
        val flow: String = "",
        val network: String = "tcp",
        val tls: Boolean = true,
        val fingerprint: String = "chrome",
        val path: String = "/",
        val host: String = "",
        val serviceName: String = "",
        val password: String = "",
        val method: String = "chacha20-ietf-poly1305",
        val localSocksPort: Int = SOCKS5_PORT,
        val localHttpPort: Int = HTTP_PORT,
        val routingDomainStrategy: String = "AsIs",
        val sniffing: Boolean = true,
        val muxConcurrency: Int = 8,
        val streamSettings: Map<String, Any?> = emptyMap()
    )

    inner class V2RayBinder : Binder() {
        fun getService(): V2RayService = this@V2RayService
    }

    private val binder = V2RayBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private var v2rayProcess: Process? = null
    var isRunning = false
        private set
    var activeProtocol = Protocol.VMESS
        private set
    var configDir: File? = null
        private set

    private val configHistory = ConcurrentHashMap<String, V2RayConfig>()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("V2Ray proxy idle"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.getStringExtra("action") ?: "start"
            when (action) {
                "start" -> startV2Ray()
                "stop" -> stopV2Ray()
                "config" -> applyConfigFromIntent(it)
            }
        }
        return START_STICKY
    }

    fun startV2Ray(config: V2RayConfig? = null) {
        scope.launch {
            config?.let { applyConfig(it) }
            startV2RayProcess()
        }
    }

    fun stopV2Ray() {
        isRunning = false
        v2rayProcess?.destroy()
        v2rayProcess?.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
        v2rayProcess?.destroyForcibly()
        v2rayProcess = null
        updateNotification("V2Ray stopped")
        Log.i(TAG, "V2Ray stopped")
    }

    private fun applyConfig(config: V2RayConfig) {
        activeProtocol = config.protocol
        configDir = File(filesDir, "v2ray").also { it.mkdirs() }
        val configFile = File(configDir, "config.json")
        configFile.writeText(generateConfigJson(config))
        Log.i(TAG, "V2Ray config written: ${config.protocol} -> ${config.address}:${config.port}")
    }

    private fun applyConfigFromIntent(intent: Intent) {
        val config = V2RayConfig(
            protocol = Protocol.valueOf(intent.getStringExtra("protocol") ?: "VMESS"),
            address = intent.getStringExtra("address") ?: "",
            port = intent.getIntExtra("port", 443),
            uuid = intent.getStringExtra("uuid") ?: "",
            password = intent.getStringExtra("password") ?: "",
            method = intent.getStringExtra("method") ?: "chacha20-ietf-poly1305",
            network = intent.getStringExtra("network") ?: "tcp",
            path = intent.getStringExtra("path") ?: "/",
            host = intent.getStringExtra("host") ?: "",
            tls = intent.getBooleanExtra("tls", true)
        )
        applyConfig(config)
    }

    private suspend fun startV2RayProcess() = withContext(Dispatchers.IO) {
        try {
            var binary = findV2RayBinary()
            if (binary == null) {
                Log.w(TAG, "No v2ray binary found, attempting auto-download")
                binary = BinaryDownloader.getInstance(this@V2RayService)
                    .downloadBinary(BinaryDownloader.BinaryType.V2RAY)
                if (binary == null) {
                    Log.e(TAG, "V2Ray binary unavailable after download attempt — proxy will not start")
                    isRunning = false
                    updateNotification("V2Ray unavailable (no binary)")
                    return@withContext
                }
            }
            val cfgDir = configDir ?: File(filesDir, "v2ray").also { it.mkdirs() }
            val configFile = File(cfgDir, "config.json")
            val pb = ProcessBuilder(
                binary.absolutePath,
                "run",
                "-config", configFile.absolutePath
            )
            pb.directory(cfgDir)
            pb.environment()["v2ray.location.asset"] = cfgDir.absolutePath
            pb.environment()["v2ray.confdir"] = cfgDir.absolutePath
            pb.redirectErrorStream(true)
            v2rayProcess = pb.start()
            scope.launch {
                v2rayProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.forEachLine { Log.d(TAG, "v2ray: $it") }
                }
            }
            delay(2000)
            val portCheck = try {
                val s = java.net.Socket()
                s.connect(java.net.InetSocketAddress("127.0.0.1", SOCKS5_PORT), 1000)
                s.close()
                true
            } catch (e: Exception) { false }
            if (!portCheck) throw RuntimeException("V2Ray SOCKS5 port $SOCKS5_PORT not listening after launch")
            isRunning = true
            updateNotification("V2Ray ${activeProtocol.name} proxy")
            Log.i(TAG, "V2Ray started: ${activeProtocol.name} SOCKS5:$SOCKS5_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "V2Ray start failed: ${e.message}")
            isRunning = false
            v2rayProcess?.destroyForcibly()
            v2rayProcess = null
            updateNotification("V2Ray failed: ${e.message}")
        }
    }

    private fun findV2RayBinary(): File? {
        val paths = listOf(
            File(applicationInfo.nativeLibraryDir, "libv2ray.so"),
            File(applicationInfo.nativeLibraryDir, "libxray.so"),
            File(filesDir, "v2ray"),
            File(filesDir, "xray"),
            File(File(filesDir, "v2ray-core"), "v2ray"),
            File(filesDir, "v2ray"),
        )
        for (p in paths) {
            if (p.exists()) return p
        }
        return null
    }

    private val gson = Gson()

    fun generateConfigJson(config: V2RayConfig): String {
        val root = mapOf(
            "log" to mapOf(
                "loglevel" to "warning",
                "access" to "",
                "error" to ""
            ),
            "inbounds" to listOf(
                mapOf(
                    "port" to config.localSocksPort,
                    "listen" to "127.0.0.1",
                    "protocol" to "socks",
                    "settings" to mapOf("udp" to true, "auth" to "noauth")
                ),
                mapOf(
                    "port" to config.localHttpPort,
                    "listen" to "127.0.0.1",
                    "protocol" to "http",
                    "settings" to emptyMap<String, Any>()
                )
            ),
            "outbounds" to listOf(
                buildOutboundMap(config),
                mapOf("protocol" to "freedom", "tag" to "direct")
            ),
            "routing" to mapOf(
                "domainStrategy" to config.routingDomainStrategy,
                "rules" to listOf(
                    mapOf("type" to "field", "outboundTag" to "proxy", "domain" to listOf("geosite:google", "geosite:twitter", "geosite:telegram")),
                    mapOf("type" to "field", "outboundTag" to "direct", "ip" to listOf("geoip:private")),
                    mapOf("type" to "field", "outboundTag" to "direct", "domain" to listOf("geosite:cn"))
                )
            ),
            "dns" to mapOf("servers" to listOf("1.1.1.1", "8.8.8.8", "localhost"))
        )
        return gson.toJson(root)
    }

    private fun buildStreamSettingsMap(config: V2RayConfig): Map<String, Any?> {
        val settings = mutableMapOf<String, Any?>("network" to config.network)
        if (config.tls) {
            settings["security"] = "tls"
            settings["tlsSettings"] = mapOf(
                "serverName" to config.host,
                "fingerprint" to config.fingerprint,
                "allowInsecure" to false
            )
        }
        if (config.network == "ws" || config.network == "websocket") {
            settings["wsSettings"] = mapOf(
                "path" to config.path,
                "headers" to mapOf("Host" to config.host)
            )
        }
        if (config.network == "grpc") {
            settings["grpcSettings"] = mapOf(
                "serviceName" to config.serviceName,
                "multiMode" to false
            )
        }
        return settings
    }

    private fun buildOutboundMap(config: V2RayConfig): Map<String, Any?> {
        val protocolName = config.protocol.name.lowercase()
        val settings: Map<String, Any?> = when (config.protocol) {
            Protocol.VMESS -> mapOf(
                "vnext" to listOf(mapOf(
                    "address" to config.address,
                    "port" to config.port,
                    "users" to listOf(mapOf(
                        "id" to config.uuid,
                        "security" to config.security,
                        "encryption" to config.encryption
                    ))
                ))
            )
            Protocol.VLESS -> mapOf(
                "vnext" to listOf(mapOf(
                    "address" to config.address,
                    "port" to config.port,
                    "users" to listOf(mapOf(
                        "id" to config.uuid,
                        "flow" to config.flow,
                        "encryption" to "none"
                    ))
                ))
            )
            Protocol.SHADOWSOCKS -> mapOf(
                "servers" to listOf(mapOf(
                    "address" to config.address,
                    "port" to config.port,
                    "method" to config.method,
                    "password" to config.password,
                    "ota" to false
                ))
            )
            Protocol.TROJAN -> mapOf(
                "servers" to listOf(mapOf(
                    "address" to config.address,
                    "port" to config.port,
                    "password" to config.password,
                    "flow" to config.flow,
                    "level" to 0
                ))
            )
            else -> mapOf(
                "servers" to listOf(mapOf(
                    "address" to config.address,
                    "port" to config.port
                ))
            )
        }
        val outbound = mutableMapOf<String, Any?>(
            "protocol" to protocolName,
            "tag" to "proxy",
            "settings" to settings,
            "streamSettings" to buildStreamSettingsMap(config)
        )
        if (config.muxConcurrency > 0) {
            outbound["mux"] = mapOf("enabled" to true, "concurrency" to config.muxConcurrency)
        }
        return outbound
    }

    fun generateUUID(): String {
        val bytes = ByteArray(16)
        rng.nextBytes(bytes)
        bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
        return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
            *bytes.toList().map { it.toInt() and 0xff }.toTypedArray())
    }

    fun getClientInfo(): String {
        return gson.toJson(mapOf(
            "protocol" to activeProtocol.name,
            "running" to isRunning,
            "socks" to "127.0.0.1:$SOCKS5_PORT",
            "http" to "127.0.0.1:$HTTP_PORT"
        ))
    }

    fun generateShareLink(config: V2RayConfig): String {
        return when (config.protocol) {
            Protocol.VMESS -> {
                val tlsVal = if (config.tls) "tls" else "none"
                val json = gson.toJson(mapOf(
                    "v" to "2",
                    "ps" to "nexuschat",
                    "add" to config.address,
                    "port" to config.port,
                    "id" to config.uuid,
                    "aid" to "0",
                    "net" to config.network,
                    "type" to "none",
                    "host" to config.host,
                    "path" to config.path,
                    "tls" to tlsVal,
                    "scy" to "auto"
                ))
                "vmess://${Base64.getUrlEncoder().encodeToString(json.toByteArray())}"
            }
            Protocol.VLESS -> {
                val tlsVal = if (config.tls) "tls" else "none"
                "vless://${config.uuid}@${config.address}:${config.port}?type=${config.network}&security=$tlsVal&flow=${config.flow}&fp=${config.fingerprint}#nexuschat"
            }
            Protocol.SHADOWSOCKS -> "ss://${Base64.getUrlEncoder().encodeToString("${config.method}:${config.password}".toByteArray())}@${config.address}:${config.port}#nexuschat"
            Protocol.TROJAN -> "trojan://${config.password}@${config.address}:${config.port}?security=tls&type=tcp#nexuschat"
            else -> ""
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NexusChatApp.CH_TOR)
            .setContentTitle("NexusChat V2Ray")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopV2Ray()
        scope.cancel()
    }
}
