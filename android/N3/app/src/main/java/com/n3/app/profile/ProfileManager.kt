package com.n3.app.profile

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.n3.app.crypto.Bip39
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

class ProfileManager(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/Profile"
        private const val PREFS = "n3_profile"
        private const val KEY_MNEMONIC = "seed_mnemonic"
        private const val KEY_CREATED = "created_at"
        private const val KEY_VERIFIED = "verified"
    }

    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    fun hasProfile(): Boolean = prefs.contains(KEY_MNEMONIC)

    fun isVerified(): Boolean = prefs.getBoolean(KEY_VERIFIED, false)

    fun getMnemonic(): List<String>? {
        val raw = prefs.getString(KEY_MNEMONIC, null) ?: return null
        return Bip39.stringToPhrase(raw)
    }

    fun getCreatedAt(): Long = prefs.getLong(KEY_CREATED, 0)

    fun createProfile(userWords: List<String>): List<String> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val phrase = Bip39.generateMnemonic(userWords, today)
        prefs.edit()
            .putString(KEY_MNEMONIC, Bip39.phraseToString(phrase))
            .putLong(KEY_CREATED, System.currentTimeMillis())
            .putBoolean(KEY_VERIFIED, false)
            .apply()
        Log.i(TAG, "Profile created: ${phrase.size}-word mnemonic")
        return phrase
    }

    fun verifyWord(indices: List<Int>): Map<Int, String> {
        val phrase = getMnemonic() ?: return emptyMap()
        return indices.associateWith { phrase[it] }
    }

    fun markVerified() {
        prefs.edit().putBoolean(KEY_VERIFIED, true).apply()
        Log.i(TAG, "Profile verified")
    }

    fun getVerificationChallenge(): List<Int> {
        val phrase = getMnemonic() ?: return emptyList()
        val wordCount = phrase.size
        return rng.ints(3, 0, wordCount).distinct().limit(3).toArray().toList()
    }

    fun deleteProfile() { prefs.edit().clear().apply() }
}
