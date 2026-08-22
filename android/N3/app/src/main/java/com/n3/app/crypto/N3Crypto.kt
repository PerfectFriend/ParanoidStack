package com.n3.app.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.SecureRandom

object N3Crypto {
    private const val KEYSET_NAME = "n3_aead_keyset"
    private const val MASTER_KEY_URI = "android-keystore://n3_master_key"
    private const val PREF_FILE = "n3_keyset_prefs"

    private var aead: Aead? = null
    private val rng = SecureRandom()

    fun init(ctx: android.content.Context) {
        if (aead != null) return
        val handle: KeysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(ctx, KEYSET_NAME, PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI).build().keysetHandle
        aead = handle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plaintext: ByteArray, ad: ByteArray = byteArrayOf()): String =
        Base64.encodeToString(aead!!.encrypt(plaintext, ad), Base64.NO_WRAP)

    fun decrypt(ciphertextB64: String, ad: ByteArray = byteArrayOf()): ByteArray? = try {
        aead!!.decrypt(Base64.decode(ciphertextB64, Base64.NO_WRAP), ad)
    } catch (e: Exception) { null }

    fun encryptString(plaintext: String, ad: String = ""): String =
        encrypt(plaintext.toByteArray(Charsets.UTF_8), ad.toByteArray(Charsets.UTF_8))

    fun decryptString(ciphertextB64: String, ad: String = ""): String? =
        decrypt(ciphertextB64, ad.toByteArray(Charsets.UTF_8))?.toString(Charsets.UTF_8)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }
    fun randomId(size: Int = 16): String =
        Base64.encodeToString(randomBytes(size), Base64.URL_SAFE or Base64.NO_WRAP)
}
