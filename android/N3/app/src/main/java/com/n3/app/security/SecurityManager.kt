package com.n3.app.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.n3.app.contact.ContactManager
import com.n3.app.crypto.IdentityManager
import com.n3.app.profile.ProfileManager
import com.n3.app.storage.MessageStore

class SecurityManager(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/Security"
        private const val PREFS = "n3_security"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_CLIPBOARD_TIMER = "clipboard_clear_secs"
        private const val DEFAULT_CLIPBOARD_SECS = 30
    }

    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var clipboardTimer: Runnable? = null

    fun isBiometricLockEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    fun getClipboardClearSecs(): Int = prefs.getInt(KEY_CLIPBOARD_TIMER, DEFAULT_CLIPBOARD_SECS)

    fun setClipboardClearSecs(secs: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_TIMER, secs).apply()
    }

    fun scheduleClipboardClear(text: String) {
        val existing = clipboardTimer
        if (existing != null) mainHandler.removeCallbacks(existing)
        val secs = getClipboardClearSecs()
        if (secs <= 0) return
        val r = Runnable {
            try {
                val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val current = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (current == text || text in current) {
                    clip.setPrimaryClip(ClipData.newPlainText("", ""))
                    Log.i(TAG, "Clipboard cleared after ${secs}s")
                }
            } catch (e: Exception) { Log.w(TAG, "Clipboard clear: ${e.message}") }
        }
        clipboardTimer = r
        mainHandler.postDelayed(r, secs * 1000L)
    }

    fun cancelClipboardClear() {
        clipboardTimer?.let { mainHandler.removeCallbacks(it) }
        clipboardTimer = null
    }

    fun panic() {
        Log.w(TAG, "PANIC MODE ACTIVATED")
        cancelClipboardClear()
        IdentityManager.destroy()
        ContactManager(ctx).destroy()
        MessageStore(ctx).destroy()
        ProfileManager(ctx).deleteProfile()
        prefs.edit().clear().apply()

        try {
            val msgDir = java.io.File(ctx.filesDir, "messages")
            if (msgDir.exists()) msgDir.deleteRecursively()
            val hsDir = java.io.File(ctx.filesDir, "hidden_service")
            if (hsDir.exists()) hsDir.deleteRecursively()
            val torData = java.io.File(ctx.filesDir, "tor_data")
            if (torData.exists()) torData.deleteRecursively()
            val auditFile = java.io.File(ctx.filesDir, "audit_log.enc")
            if (auditFile.exists()) auditFile.delete()
        } catch (e: Exception) { Log.e(TAG, "Panic cleanup: ${e.message}") }

        Log.i(TAG, "Panic complete — all data wiped")
    }
}
