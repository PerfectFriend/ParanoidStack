package com.n3.app.bridges

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.JavascriptInterface
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.n3.app.BuildConfig
import com.n3.app.MainActivity
import com.n3.app.N3App
import com.n3.app.services.TorService
import com.n3.app.crypto.N3Crypto

class TorBridge(private val ctx: Context) {
    @Volatile private var bytesDown = 0L
    @Volatile private var bytesUp = 0L
    private val startTime = System.currentTimeMillis()

    @JavascriptInterface fun getSocksPort(): Int = TorService.SOCKS_PORT
    @JavascriptInterface fun getControlPort(): Int = TorService.CONTROL_PORT
    @JavascriptInterface fun isRunning(): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", TorService.CONTROL_PORT), 500) }; true
    } catch (e: Exception) { false }
    @JavascriptInterface fun getOnionAddress(): String {
        val f = java.io.File(ctx.filesDir, "hidden_service/hostname")
        return if (f.exists()) f.readText().trim() else ""
    }
    @JavascriptInterface fun reportTraffic(downBytes: Int, upBytes: Int) { bytesDown += downBytes; bytesUp += upBytes }

    @JavascriptInterface fun getBandwidth(): String {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val down = formatBytes(bytesDown)
        val up = formatBytes(bytesUp)
        val rateDown = if (elapsed > 0) formatRate(bytesDown / elapsed) else "0 B/s"
        val rateUp = if (elapsed > 0) formatRate(bytesUp / elapsed) else "0 B/s"
        return """{"down":"$down","up":"$up","rateDown":"$rateDown","rateUp":"$rateUp","elapsed":$elapsed}"""
    }

    private fun formatBytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
        else -> "%.1f MB".format(b / (1024.0 * 1024.0))
    }
    private fun formatRate(bps: Long): String = when {
        bps < 1024 -> "$bps B/s"
        bps < 1024 * 1024 -> "%.1f KB/s".format(bps / 1024.0)
        else -> "%.1f MB/s".format(bps / (1024.0 * 1024.0))
    }

    private var cachedIp = ""
    private var ipCacheTime = 0L

    @JavascriptInterface fun getStatus(): String {
        val running = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", TorService.CONTROL_PORT), 500) }; true
        } catch (e: Exception) { false }
        val onion = getOnionAddress()
        val ip = if (running) {
            if (System.currentTimeMillis() - ipCacheTime > 30000) {
                cachedIp = try {
                    val proxy = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT))
                    val conn = java.net.URL("https://check.torproject.org/").openConnection(proxy) as java.net.HttpURLConnection
                    conn.connectTimeout = 5000; conn.readTimeout = 3000
                    conn.inputStream.bufferedReader().use { it.readText() }.let { html ->
                        val r = Regex("""Your IP address appears to be:\s*(\d+\.\d+\.\d+\.\d+)""").find(html)?.groupValues?.getOrNull(1)
                        if (r != null) r else Regex("""(\d+\.\d+\.\d+\.\d+)""").find(html)?.groupValues?.getOrNull(1) ?: "unknown"
                    }
                } catch (e: Exception) { "unreachable" }
                ipCacheTime = System.currentTimeMillis()
            }
            cachedIp
        } else "stopped"
        val bw = getBandwidth()
        return Gson().toJson(mapOf(
            "running" to running, "onion" to (onion.ifEmpty { "" }),
            "ip" to (ip.ifEmpty { "" }), "socks" to TorService.SOCKS_PORT,
            "control" to TorService.CONTROL_PORT, "bandwidth" to parseBandwidth(bw)
        ))
    }

    private fun parseBandwidth(bwJson: String): Map<String, Any> = try {
        Gson().fromJson(bwJson, Map::class.java)
    } catch (e: Exception) { mapOf<String, Any>() }
}

class CryptoBridge {
    @JavascriptInterface fun encrypt(plaintext: String, ad: String = ""): String = try {
        N3Crypto.encryptString(plaintext, ad)
    } catch (e: Exception) { "" }
    @JavascriptInterface fun decrypt(ciphertext: String, ad: String = ""): String =
        try { N3Crypto.decryptString(ciphertext, ad) ?: "" } catch (e: Exception) { "" }
}

class KeystoreBridge {
    @JavascriptInterface fun put(key: String, value: String): Boolean = try {
        N3App.securePrefs.edit().putString(key, value).commit()
    } catch (e: Exception) { false }
    @JavascriptInterface fun get(key: String): String = try {
        N3App.securePrefs.getString(key, "") ?: ""
    } catch (e: Exception) { "" }
    @JavascriptInterface fun remove(key: String): Boolean = try {
        N3App.securePrefs.edit().remove(key).commit()
    } catch (e: Exception) { false }
}

class SystemBridge(private val ctx: Context) {
    @JavascriptInterface fun getInfo(): String = Gson().toJson(mapOf(
        "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL,
        "sdk" to Build.VERSION.SDK_INT, "version" to BuildConfig.VERSION_NAME
    ))
    @JavascriptInterface fun vibrate() {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator?
        if (v == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(100)
    }
    @JavascriptInterface fun openUrl(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class BiometricBridge(private val activity: MainActivity) {
    @JavascriptInterface fun authenticate(callback: String) {
        try {
            val mgr = BiometricManager.from(activity)
            if (mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
                activity.evalJs("$callback(false)"); return
            }
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                        activity.evalJs("$callback(true)")
                    }
                    override fun onAuthenticationFailed() { activity.evalJs("$callback(false)") }
                    override fun onAuthenticationError(code: Int, msg: CharSequence) {
                        activity.evalJs("$callback(false)")
                    }
                }).authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle("N3").setSubtitle("Authenticate")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
        } catch (e: Exception) { activity.evalJs("$callback(false)") }
    }
}

class AndroidBridge(private val activity: MainActivity) {
    @JavascriptInterface fun connectSmp(host: String, port: Int) { activity.connectSmp(host, port) }
    @JavascriptInterface fun sendSmp(json: String) { activity.sendSmp(json) }
}
