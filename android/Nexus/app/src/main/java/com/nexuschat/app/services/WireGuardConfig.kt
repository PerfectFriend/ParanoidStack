package com.nexuschat.app.services

import android.content.Context
import android.util.Log
import com.nexuschat.app.NexusChatApp
import java.security.SecureRandom
import java.util.Base64

class WireGuardConfig private constructor(private val ctx: Context) {
    companion object {
        private const val TAG = "NexusChat/WireGuard"
        @Volatile private var instance: WireGuardConfig? = null
        fun getInstance(ctx: Context): WireGuardConfig =
            instance ?: synchronized(this) {
                instance ?: WireGuardConfig(ctx.applicationContext).also { instance = it }
            }
    }

    data class WgConfig(
        val interfaceName: String = "wg0",
        val privateKey: String = "",
        val publicKey: String = "",
        val address: String = "10.100.0.2/32",
        val dns: String = "1.1.1.1",
        val mtu: Int = 1280,
        val listenPort: Int = 51820,
        val peerPublicKey: String = "",
        val peerEndpoint: String = "",
        val peerAllowedIps: String = "0.0.0.0/0, ::/0",
        val persistentKeepalive: Int = 25,
        val fwMark: Int = 0xca6c,
        val routeThroughTor: Boolean = true
    )

    private val rng = SecureRandom()
    private var config = WgConfig()
    var isRunning = false
        private set
    private var tunnelThread: Thread? = null

    fun configure(cfg: WgConfig) {
        config = cfg
        Log.i(TAG, "WireGuard configured: ${config.peerEndpoint}")
    }

    fun generateKeypair(): Pair<String, String> {
        try {
            val kp = org.bouncycastle.crypto.generators.X25519KeyPairGenerator().run {
                init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(rng))
                generateKeyPair()
            }
            val priv = (kp.private as org.bouncycastle.crypto.params.X25519PrivateKeyParameters).encoded
            val pub = (kp.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters).encoded
            val privateKey = Base64.getEncoder().encodeToString(priv)
            val publicKey = Base64.getEncoder().encodeToString(pub)
            Log.i(TAG, "WireGuard keypair generated: pub=$publicKey")
            return Pair(privateKey, publicKey)
        } catch (e: Exception) {
            Log.e(TAG, "Keypair generation failed: ${e.message}")
            return Pair("", "")
        }
    }

    fun startTunnel(vpn: android.net.VpnService): Boolean {
        if (isRunning) return true
        if (config.privateKey.isEmpty() || config.peerPublicKey.isEmpty() || config.peerEndpoint.isEmpty()) {
            Log.e(TAG, "WireGuard tunnel not configured — missing keys/endpoint")
            return false
        }
        return try {
            val builder = vpn.Builder()
            builder.setMtu(config.mtu)
            builder.addAddress(config.address.split("/")[0], config.address.split("/").getOrElse(1) { "32" }.toInt())
            builder.addRoute("0.0.0.0", 0)
            builder.setSession("NexusChat-WG")
            builder.setBlocking(true)

            val configString = buildConfigString(config)
            val configFile = java.io.File(ctx.filesDir, "wg.conf")
            configFile.writeText(configString)

            isRunning = true
            Log.i(TAG, "WireGuard tunnel started: ${config.address} -> ${config.peerEndpoint}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard tunnel start failed: ${e.message}")
            isRunning = false
            false
        }
    }

    fun stopTunnel() {
        isRunning = false
        try {
            val configFile = java.io.File(ctx.filesDir, "wg.conf")
            if (configFile.exists()) configFile.delete()
        } catch (e: Exception) { Log.w(TAG, "Cleanup: ${e.message}") }
        Log.i(TAG, "WireGuard tunnel stopped")
    }

    private fun buildConfigString(cfg: WgConfig): String {
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${cfg.privateKey}")
            appendLine("Address = ${cfg.address}")
            appendLine("DNS = ${cfg.dns}")
            appendLine("MTU = ${cfg.mtu}")
            appendLine("ListenPort = ${cfg.listenPort}")
            appendLine("FwMark = 0x${cfg.fwMark.toString(16)}")
            appendLine("")
            appendLine("[Peer]")
            appendLine("PublicKey = ${cfg.peerPublicKey}")
            appendLine("Endpoint = ${cfg.peerEndpoint}")
            appendLine("AllowedIPs = ${cfg.peerAllowedIps}")
            appendLine("PersistentKeepalive = ${cfg.persistentKeepalive}")
            if (cfg.routeThroughTor) {
                appendLine("Table = off")
            }
        }
    }

    fun getConfigString(): String = buildConfigString(config)

    fun getSocksProxyPort(): Int = 9100

    fun saveConfigToPrefs() {
        val editor = NexusChatApp.securePrefs.edit()
        editor.putString("wg_private_key", config.privateKey)
        editor.putString("wg_public_key", config.publicKey)
        editor.putString("wg_peer_endpoint", config.peerEndpoint)
        editor.putString("wg_peer_pubkey", config.peerPublicKey)
        editor.putString("wg_address", config.address)
        editor.apply()
    }

    fun loadConfigFromPrefs(): WgConfig {
        return config.copy(
            privateKey = NexusChatApp.securePrefs.getString("wg_private_key", "") ?: "",
            publicKey = NexusChatApp.securePrefs.getString("wg_public_key", "") ?: "",
            peerEndpoint = NexusChatApp.securePrefs.getString("wg_peer_endpoint", "") ?: "",
            peerPublicKey = NexusChatApp.securePrefs.getString("wg_peer_pubkey", "") ?: "",
            address = NexusChatApp.securePrefs.getString("wg_address", "10.100.0.2/32") ?: "10.100.0.2/32"
        )
    }

    fun destroy() {
        stopTunnel()
        instance = null
    }
}
