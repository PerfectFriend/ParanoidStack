package com.n3.app.bridges

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexuschat.app.MainActivity
import com.nexuschat.app.NexusChatApp
import com.nexuschat.app.R
import com.n3.app.services.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyStore

/**
 * TorBridge — JS → Native bridge for Tor status & control.
 * Exposed to WebView as "AndroidTor". Methods: isRunning, newCircuit,
 * getOnionAddress, getSocksPort, getControlPort, getCircuitInfo, getTransportInfo.
 * Socket-based health check on control port (9051).
 */
class TorBridge(
    private val ctx: Context,
    private val onReady: (Int) -> Unit
) {
    companion object { const val TAG = "NexusChat/TorBridge" }

    @JavascriptInterface
    fun newCircuit(): Boolean {
        Log.i(TAG, "JS -> newCircuit()")
        return try {
            val intent = Intent("com.nexuschat.NEWNYM")
            ctx.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "newCircuit broadcast failed: ${e.message}")
            false
        }
    }

    @JavascriptInterface
    fun getSocksPort(): Int = TorService.SOCKS_PORT

    @JavascriptInterface
    fun getControlPort(): Int = TorService.CONTROL_PORT

    @JavascriptInterface
    fun isRunning(): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", TorService.CONTROL_PORT), 500)
                true
            }
        } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun getOnionAddress(): String {
        val f = File(ctx.filesDir, "hidden_service/hostname")
        return if (f.exists()) f.readText().trim() else ""
    }

    @JavascriptInterface
    fun getCircuitInfo(): String {
        var s: java.net.Socket? = null
        return try {
            s = java.net.Socket("127.0.0.1", TorService.CONTROL_PORT)
            val w = java.io.PrintWriter(s.getOutputStream(), true)
            val r = java.io.BufferedReader(java.io.InputStreamReader(s.getInputStream()))
            w.println("AUTHENTICATE")
            Thread.sleep(100)
            w.println("GETINFO circuit-status")
            Thread.sleep(200)
            val lines = mutableListOf<String>()
            while (true) {
                val line = r.readLine() ?: break
                lines.add(line)
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            "Error: ${e.message}"
        } finally {
            try { s?.close() } catch (_: Exception) {}
        }
    }

    @JavascriptInterface
    fun getTransportInfo(): String {
        try {
            val mgr = TransportManager.getInstance(ctx)
            val statuses = mgr.getAllStatuses()
            return com.google.gson.Gson().toJson(statuses.map {
                mapOf("type" to it.key.name, "available" to it.value.available,
                    "latencyMs" to it.value.latencyMs)
            })
        } catch (e: Exception) {
            return "[]"
        }
    }

    @JavascriptInterface
    fun setBridgesEnabled(enabled: Boolean) {
        TorService.useBridges = enabled
        Log.i(TAG, "Bridges ${if (enabled) "enabled" else "disabled"}")
    }

    @JavascriptInterface
    fun getBridgesEnabled(): Boolean = TorService.useBridges

    @JavascriptInterface
    fun getBridgeStatus(): String {
        return try {
            val orch = BridgeOrchestrator.getInstance(ctx)
            val statuses = orch.allStatuses
            com.google.gson.Gson().toJson(statuses.map { (proto, st) ->
                mapOf(
                    "protocol" to proto.name,
                    "available" to st.available,
                    "latencyMs" to st.latencyMs,
                    "error" to st.error
                )
            })
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun getActiveBridgeProtocol(): String {
        return try {
            BridgeOrchestrator.getInstance(ctx).activeProtocol?.name ?: "NONE"
        } catch (e: Exception) { "NONE" }
    }

    @JavascriptInterface
    fun forceBridge(protocol: String) {
        try {
            val proto = BridgeOrchestrator.BridgeProtocol.valueOf(protocol)
            BridgeOrchestrator.getInstance(ctx).forceBridge(proto)
            Log.i(TAG, "Bridge forced: $protocol")
        } catch (e: Exception) {
            Log.w(TAG, "Force bridge failed: ${e.message}")
        }
    }
}

interface TailscaleApi {
    @GET("tailnet/{tailnet}/devices")
    suspend fun getDevices(
        @Path("tailnet") tailnet: String,
        @Header("Authorization") auth: String
    ): TsDevicesResponse
}

data class TsDevicesResponse(val devices: List<TsDevice>? = null)
data class TsDevice(
    val name: String? = null,
    val addresses: List<String>? = null,
    val os: String? = null,
    val online: Boolean = false,
    val lastSeen: String? = null
)

/**
 * TailscaleBridge — JS bridge for Tailscale API + WireGuard keys.
 * Exposed as "AndroidTailscale". Fetches tailnet peers via OkHttp over Tor,
 * generates WireGuard X25519 keypairs via BouncyCastle.
 */
class TailscaleBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit
) {
    companion object { const val TAG = "NexusChat/TSBridge" }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun buildRetrofit(apiBase: String): Retrofit {
        val torProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT))
        val client = OkHttpClient.Builder()
            .proxy(torProxy)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("$apiBase/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @JavascriptInterface
    fun getDevices(apiBase: String, tailnet: String, authKey: String, callbackFn: String) {
        scope.launch {
            try {
                val api = buildRetrofit(apiBase).create(TailscaleApi::class.java)
                val resp = api.getDevices(tailnet.ifEmpty { "-" }, "Bearer $authKey")
                val json = com.google.gson.Gson().toJson(resp)
                Log.i(TAG, "TS devices: ${resp.devices?.size}")
                evalJs("$callbackFn($json)")
            } catch (e: Exception) {
                Log.e(TAG, "TS API error: ${e.message}")
                val errObj = mapOf("error" to (e.message ?: "unknown"))
                evalJs("$callbackFn(${com.google.gson.Gson().toJson(errObj)})")
            }
        }
    }

    @JavascriptInterface
    fun generateWgKeypair(): String {
        try {
            val wg = WireGuardConfig.getInstance(ctx)
            val (priv, pub) = wg.generateKeypair()
            return com.google.gson.Gson().toJson(mapOf("privateKey" to priv, "publicKey" to pub))
        } catch (e: Exception) {
            return com.google.gson.Gson().toJson(mapOf("error" to (e.message ?: "unknown")))
        }
    }

    @JavascriptInterface
    fun saveWgConfig(privateKey: String, publicKey: String, endpoint: String, peerPubkey: String, address: String) {
        val wg = WireGuardConfig.getInstance(ctx)
        wg.configure(WireGuardConfig.WgConfig(
            privateKey = privateKey,
            publicKey = publicKey,
            peerEndpoint = endpoint,
            peerPublicKey = peerPubkey,
            address = address
        ))
        wg.saveConfigToPrefs()
        Log.i(TAG, "WireGuard config saved")
    }
}

class KeystoreBridge(private val ctx: Context) {
    companion object { const val TAG = "NexusChat/Keystore" }

    @JavascriptInterface
    fun storeSecret(key: String, value: String): Boolean {
        return try {
            NexusChatApp.securePrefs.edit().putString(key, value).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Store failed: ${e.message}"); false
        }
    }

    @JavascriptInterface
    fun getSecret(key: String): String {
        return try {
            NexusChatApp.securePrefs.getString(key, "") ?: ""
        } catch (e: Exception) { "" }
    }

    @JavascriptInterface
    fun deleteSecret(key: String): Boolean {
        return try {
            NexusChatApp.securePrefs.edit().remove(key).commit()
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun clearAll(): Boolean {
        return try {
            NexusChatApp.securePrefs.edit().clear().commit()
        } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun listKeys(): String {
        return try {
            com.google.gson.Gson().toJson(NexusChatApp.securePrefs.all.keys.toList())
        } catch (_: Exception) { "[]" }
    }

    @JavascriptInterface
    fun isHardwareBacked(): Boolean {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        } catch (_: Exception) { false }
    }
}

class BiometricBridge(
    private val ctx: Context,
    private val onResult: (Boolean) -> Unit
) {
    @JavascriptInterface
    fun authenticate() {
        (ctx as? MainActivity)?.showBiometricPrompt { success -> onResult(success) }
    }

    @JavascriptInterface
    fun isAvailable(): Boolean {
        val mgr = androidx.biometric.BiometricManager.from(ctx)
        return mgr.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }
}

class NotificationBridge(private val ctx: Context) {
    companion object { const val TAG = "NexusChat/Notif" }

    @JavascriptInterface
    fun show(title: String, body: String, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(ctx, NexusChatApp.CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0,100,50,100))
            .build()
        NotificationManagerCompat.from(ctx).notify(tag.hashCode(), n)
    }

    @JavascriptInterface
    fun cancel(tag: String) {
        NotificationManagerCompat.from(ctx).cancel(tag.hashCode())
    }
}

class FileBridge(private val ctx: Context) {
    companion object {
        private fun sanitize(path: String): String {
            val parts = path.replace(File.separatorChar, '/').split("/")
            val result = mutableListOf<String>()
            for (part in parts) {
                when (part) {
                    ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                    ".", "" -> {}
                    else -> result.add(part)
                }
            }
            return result.joinToString(File.separator)
        }
    }

    @JavascriptInterface
    fun getFilesDir(): String = ctx.filesDir.absolutePath
    @JavascriptInterface
    fun getCacheDir(): String = ctx.cacheDir.absolutePath
    @JavascriptInterface
    fun readFile(path: String): String {
        return try { File(ctx.filesDir, sanitize(path)).readText() } catch (_: Exception) { "" }
    }
    @JavascriptInterface
    fun writeFile(path: String, content: String): Boolean {
        return try {
            val f = File(ctx.filesDir, sanitize(path))
            f.parentFile?.mkdirs()
            f.writeText(content); true
        } catch (_: Exception) { false }
    }
    @JavascriptInterface
    fun deleteFile(path: String): Boolean {
        try { return File(ctx.filesDir, sanitize(path)).delete() } catch (_: Exception) { return false }
    }
    @JavascriptInterface
    fun fileExists(path: String): Boolean {
        try { return File(ctx.filesDir, sanitize(path)).exists() } catch (_: Exception) { return false }
    }
    @JavascriptInterface
    fun listFiles(dir: String): String {
        val d = File(ctx.filesDir, sanitize(dir))
        if (!d.isDirectory) return "[]"
        return com.google.gson.Gson().toJson(d.list()?.toList() ?: emptyList<String>())
    }
}

class ClipboardBridge(private val ctx: Context) {
    @JavascriptInterface
    fun copy(text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("NexusChat", text))
    }
    @JavascriptInterface
    fun paste(): String {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }
}

class SystemBridge(private val ctx: Context) {
    @JavascriptInterface
    fun startBridgeVpn(): Boolean {
        return try {
            val prepareIntent = android.net.VpnService.prepare(ctx)
            if (prepareIntent != null) {
                ctx.startActivity(prepareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                android.util.Log.i("NexusChat/System", "VPN permission requested from user")
                return false
            }
            val svc = Intent(ctx, com.nexuschat.app.services.NexusVpnService::class.java).apply {
                putExtra("action", "start")
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("NexusChat/System", "Start VPN: ${e.message}")
            false
        }
    }
    @JavascriptInterface
    fun stopBridgeVpn() {
        val intent = Intent(ctx, com.nexuschat.app.services.NexusVpnService::class.java).apply {
            putExtra("action", "stop")
        }
        ctx.startService(intent)
    }
    @JavascriptInterface
    fun isBridgeVpnRunning(): Boolean = com.nexuschat.app.services.NexusVpnService.isRunning
    @JavascriptInterface
    fun getDeviceInfo(): String {
        return com.google.gson.Gson().toJson(mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "sdk" to Build.VERSION.SDK_INT,
            "android" to Build.VERSION.RELEASE,
        ))
    }
    @JavascriptInterface
    fun vibrate(pattern: String) {
        val vm = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vm.vibrate(android.os.VibrationEffect.createOneShot(100,
                android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else { @Suppress("DEPRECATION") vm.vibrate(100) }
    }
    @JavascriptInterface
    fun openUrl(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    @JavascriptInterface
    fun getVersion(): String = com.nexuschat.app.BuildConfig.VERSION_NAME
    @JavascriptInterface
    fun isDebug(): Boolean = com.nexuschat.app.BuildConfig.DEBUG
}

class SnowflakeBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit
) {
    companion object { const val TAG = "NexusChat/Snowflake" }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @JavascriptInterface
    fun startSnowflake(brokerUrl: String) {
        scope.launch {
            try {
                val transport = com.nexuschat.app.services.SnowflakeTransport(
                    ctx,
                    onDataReceived = { data ->
                        val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
                        val safe = com.google.gson.Gson().toJson(b64)
                        evalJs("window.onSnowflakeData && window.onSnowflakeData($safe)")
                    },
                    onStatusChange = { connected, msg ->
                        val safe = com.google.gson.Gson().toJson(msg)
                        evalJs("window.onSnowflakeStatus && window.onSnowflakeStatus($connected, $safe)")
                    }
                )
                transport.configure(com.nexuschat.app.services.SnowflakeTransport.SnowflakeConfig(
                    brokerUrl = brokerUrl.ifEmpty { "https://snowflake-broker.torproject.net/" }
                ))
                transport.connect()
                evalJs("window.onSnowflakeStatus && window.onSnowflakeStatus(false, 'connecting')")
            } catch (e: Exception) {
                val safe = com.google.gson.Gson().toJson(e.message ?: "unknown error")
                evalJs("window.onSnowflakeStatus && window.onSnowflakeStatus(false, $safe)")
            }
        }
    }
}

class BinaryProgressBridge(private val ctx: Context) {
    companion object { const val TAG = "NexusChat/BinaryProgress" }
    private val dl = BinaryDownloader.getInstance(ctx)

    @JavascriptInterface
    fun areAllAvailable(): Boolean =
        dl.areAllBinariesAvailable().values.all { it }

    @JavascriptInterface
    fun getAllStatus(): String {
        val m = dl.areAllBinariesAvailable()
        return com.google.gson.Gson().toJson(m.entries.associate { (k, v) ->
            k.name to if (v) "ready" else "missing"
        })
    }

    @JavascriptInterface
    fun detectAbi(): String = dl.detectAbi()

    @JavascriptInterface
    fun getVersion(): String = com.nexuschat.app.BuildConfig.VERSION_NAME
}
