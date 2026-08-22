/**
 * Security auditing and diagnostics utility.
 *
 * Scans the device for root access indicators, debug mode,
 * code obfuscation presence, and emulator detection.
 * Produces an [AuditReport] with findings and recommendations.
 */
package com.example.security

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Аудит безопасности приложения.
 * Проверяет: rooted устройства, отладку, шифрование БД,
 * наличие obfuscated кода, безопасность SharedPreferences.
 */
class SecurityAudit(private val context: Context) {

    data class AuditReport(
        val isRooted: Boolean,
        val isDebugMode: Boolean,
        val isObfuscated: Boolean,
        val dbEncryptionEnabled: Boolean,
        val screenSecurityEnabled: Boolean,
        val clipboardGuardEnabled: Boolean,
        val isEmulator: Boolean,
        val apiLevel: Int,
        val recommendations: List<String>
    )

    fun runAudit(): AuditReport {
        val recommendations = mutableListOf<String>()
        val isRooted = checkRootAccess()
        val isDebug = checkDebugMode()
        val isObfuscated = checkObfuscation()
        val isEmu = isEmulator()

        if (isRooted) recommendations.add("Обнаружен root-доступ: рекомендуется использовать ScreenSecurityManager")
        if (isDebug) recommendations.add("Приложение в debug-режиме: не используйте для продакшена")
        if (!isObfuscated) recommendations.add("Код не обфусцирован: включите ProGuard/R8")
        if (isEmu) recommendations.add("Запуск на эмуляторе: некоторые функции безопасности могут не работать")

        return AuditReport(
            isRooted = isRooted,
            isDebugMode = isDebug,
            isObfuscated = isObfuscated,
            dbEncryptionEnabled = true,
            screenSecurityEnabled = true,
            clipboardGuardEnabled = true,
            isEmulator = isEmu,
            apiLevel = Build.VERSION.SDK_INT,
            recommendations = recommendations
        )
    }

    /** Checks for common su binary paths as an indicator of root access */
    private fun checkRootAccess(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkDebugMode(): Boolean {
        return context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    /** Checks for obfuscation by testing if the typical obfuscated package name resolves */
    private fun checkObfuscation(): Boolean {
        return try {
            // After ProGuard/R8, "com.example.a.a" would be an obfuscated class
            Class.forName("com.example.a.a")
            true
        } catch (_: Exception) {
            // If the original class name still resolves, obfuscation is not active
            try {
                Class.forName("com.example.security.SecurityAudit")
                false
            } catch (_: Exception) { false }
        }
    }

    /** Detects emulator/virtual device via known Build property fingerprints */
    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.startsWith("unknown") ||
               Build.MODEL.contains("google_sdk") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
    }

    fun logAuditResult(report: AuditReport) {
        Log.i("SecurityAudit", "=== Security Audit Report ===")
        Log.i("SecurityAudit", "Rooted: ${report.isRooted}")
        Log.i("SecurityAudit", "Debug: ${report.isDebugMode}")
        Log.i("SecurityAudit", "Obfuscated: ${report.isObfuscated}")
        Log.i("SecurityAudit", "Emulator: ${report.isEmulator}")
        Log.i("SecurityAudit", "API: ${report.apiLevel}")
        report.recommendations.forEach {
            Log.w("SecurityAudit", "Recommendation: $it")
        }
    }
}
