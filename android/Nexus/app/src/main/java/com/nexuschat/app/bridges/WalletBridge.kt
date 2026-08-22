package com.nexuschat.app.bridges

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.nexuschat.app.crypto.Bip39Wallet
import kotlinx.coroutines.*

class WalletBridge(private val ctx: android.content.Context) {
    companion object {
        private const val TAG = "NexusChat/WalletBridge"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wallet: Bip39Wallet? = null

    @JavascriptInterface
    fun generateWallet(wordCount: Int, passphrase: String): String {
        return try {
            val wc = if (wordCount == 24) 24 else 12
            val w = Bip39Wallet.generate(wc, passphrase)
            wallet = w
            val result = mapOf(
                "success" to true,
                "words" to w.getWords().size,
            )
            com.google.gson.Gson().toJson(result)
        } catch (e: Exception) {
            Log.e(TAG, "Generate wallet failed: ${e.message}")
            "{\"success\":false,\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
        }
    }

    @JavascriptInterface
    fun restoreWallet(mnemonic: String, passphrase: String): String {
        return try {
            val w = Bip39Wallet.fromMnemonic(mnemonic, passphrase)
            if (w != null) {
                wallet = w
                val result = mapOf(
                    "success" to true,
                    "words" to w.getWords().size,
                )
                com.google.gson.Gson().toJson(result)
            } else {
                "{\"success\":false,\"error\":\"Invalid mnemonic or checksum mismatch\"}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore wallet failed: ${e.message}")
            "{\"success\":false,\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
        }
    }

    @JavascriptInterface
    fun getAddress(type: String): String {
        val w = wallet ?: return "{\"success\":false,\"error\":\"No wallet loaded\"}"
        return try {
            when (type.lowercase()) {
                "btc", "bitcoin" -> {
                    val (pub, priv) = w.deriveEd25519Keypair()
                    "{\"success\":true,\"type\":\"$type\",\"publicKeyB64\":\"${Base64.encodeToString(pub, Base64.NO_WRAP)}\"}"
                }
                "eth", "ethereum" -> {
                    val (pub, priv) = w.deriveEd25519Keypair()
                    "{\"success\":true,\"type\":\"$type\",\"publicKeyB64\":\"${Base64.encodeToString(pub, Base64.NO_WRAP)}\"}"
                }
                else -> "{\"success\":false,\"error\":\"Unknown type: $type\"}"
            }
        } catch (e: Exception) {
            "{\"success\":false,\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
        }
    }

    @JavascriptInterface
    fun signMessage(message: String): String {
        val w = wallet ?: return "{\"success\":false,\"error\":\"No wallet loaded\"}"
        return try {
            val sig = w.signData(message.toByteArray(Charsets.UTF_8))
            val sigB64 = Base64.encodeToString(sig, Base64.NO_WRAP)
            val (_, pubKey) = w.deriveEd25519Keypair()
            val pubB64 = Base64.encodeToString(pubKey, Base64.NO_WRAP)
            "{\"success\":true,\"signature\":\"$sigB64\",\"publicKey\":\"$pubB64\"}"
        } catch (e: Exception) {
            "{\"success\":false,\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
        }
    }

    @JavascriptInterface
    fun verifyMessage(message: String, signatureB64: String, publicKeyB64: String): String {
        return try {
            val sig = Base64.decode(signatureB64, Base64.NO_WRAP)
            val pubKey = Base64.decode(publicKeyB64, Base64.NO_WRAP)
            val w = wallet ?: Bip39Wallet.generate()
            val valid = w.verifySignature(
                message.toByteArray(Charsets.UTF_8), sig, pubKey
            )
            "{\"success\":true,\"valid\":$valid}"
        } catch (e: Exception) {
            "{\"success\":false,\"error\":\"${e.message?.replace("\"", "\\\"")}\"}"
        }
    }

    @JavascriptInterface
    fun isWalletLoaded(): Boolean = wallet != null

    fun destroy() {
        scope.cancel()
    }
}
