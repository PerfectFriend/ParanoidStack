package com.n3.app.bridges

import android.content.Context
import android.webkit.JavascriptInterface
import com.n3.app.MainActivity
import com.n3.app.security.SecurityManager

class SecurityBridge(private val ctx: Context) {
    private val sm = SecurityManager(ctx)

    @JavascriptInterface fun isBiometricLockEnabled(): Boolean = sm.isBiometricLockEnabled()
    @JavascriptInterface fun setBiometricLock(enabled: Boolean) { sm.setBiometricLock(enabled) }
    @JavascriptInterface fun getClipboardClearSecs(): Int = sm.getClipboardClearSecs()
    @JavascriptInterface fun setClipboardClearSecs(secs: Int) { sm.setClipboardClearSecs(secs) }
    @JavascriptInterface fun panic() { sm.panic() }

    @JavascriptInterface fun requestBiometricLock(callback: String) {
        val activity = ctx as? MainActivity ?: return
        if (sm.isBiometricLockEnabled()) return
        val mgr = androidx.biometric.BiometricManager.from(activity)
        if (mgr.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            activity.evalJs("$callback(false)"); return
        }
        androidx.biometric.BiometricPrompt(activity,
            androidx.core.content.ContextCompat.getMainExecutor(activity),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    sm.setBiometricLock(true)
                    activity.evalJs("$callback(true)")
                }
                override fun onAuthenticationFailed() { activity.evalJs("$callback(false)") }
                override fun onAuthenticationError(code: Int, msg: CharSequence) { activity.evalJs("$callback(false)") }
            }).authenticate(androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable App Lock").setSubtitle("Biometric lock will protect N3")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
    }
}
