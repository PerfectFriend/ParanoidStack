package com.n3.app.crypto

import android.util.Base64
import android.util.Log
import com.n3.app.profile.ProfileManager
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object IdentityManager {
    private const val TAG = "N3/Identity"
    private const val HKDF_INFO = "n3-identity-key"
    private const val SALT = "n3-identity-salt-v1"

    private var identitySeed: ByteArray? = null
    private var identityPubKey: String? = null

    fun initFromProfile(pm: ProfileManager) {
        val mnemonic = pm.getMnemonic() ?: run {
            Log.w(TAG, "No mnemonic found"); return
        }
        val seed = Bip39.mnemonicToSeed(Bip39.phraseToString(mnemonic))
        identitySeed = seed
        identityPubKey = derivePublicKey(seed)
        Log.i(TAG, "Identity: ${identityPubKey?.take(16)}...")
    }

    fun getPublicKey(): String = identityPubKey ?: ""

    fun getFingerprint(): String {
        val key = identityPubKey ?: return ""
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }

    fun deriveConversationKey(contactPubKey: String): ByteArray {
        val seed = identitySeed ?: throw IllegalStateException("Identity not initialized")
        return hkdf(seed, contactPubKey.toByteArray(Charsets.UTF_8), 32)
    }

    fun sign(data: ByteArray): ByteArray? {
        val seed = identitySeed ?: return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun verify(data: ByteArray, signature: ByteArray, contactPubKey: String): Boolean {
        val convKey = deriveConversationKey(contactPubKey)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(convKey, "HmacSHA256"))
        val expected = mac.doFinal(data)
        return MessageDigest.isEqual(signature, expected)
    }

    fun isReady(): Boolean = identitySeed != null

    fun destroy() { identitySeed = null; identityPubKey = null }

    private fun derivePublicKey(seed: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(seed)
        return Base64.encodeToString(hash, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun hkdf(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(SALT.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }.doFinal(ikm)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(info)
        mac.update(1)
        val result = mac.doFinal()
        return result.copyOf(length)
    }
}
