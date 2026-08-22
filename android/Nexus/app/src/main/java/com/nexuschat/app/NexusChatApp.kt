package com.nexuschat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.aead.AeadConfig
import org.conscrypt.Conscrypt
import java.security.Security

class NexusChatApp : Application() {
    companion object {
        const val TAG = "NexusChat"

        const val CH_TOR = "nexuschat_tor"
        const val CH_SMP = "nexuschat_smp"
        const val CH_MESSAGES = "nexuschat_messages"
        const val CH_CALLS = "nexuschat_calls"
        const val CH_TRANSPORT = "nexuschat_transport"

        lateinit var instance: NexusChatApp
            private set
        lateinit var securePrefs: android.content.SharedPreferences
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        Log.i(TAG, "Conscrypt TLS provider installed")

        AeadConfig.register()
        Log.i(TAG, "Tink AEAD registered")

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        securePrefs = EncryptedSharedPreferences.create(
            this, "nexuschat_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        Log.i(TAG, "Encrypted SharedPreferences initialised (Android Keystore)")

        createNotificationChannels()
        Log.i(TAG, "NexusChatApp initialised")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            NotificationChannel(CH_TOR, "Tor VPN Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tor daemon running in background"
                setShowBadge(false)
            },
            NotificationChannel(CH_SMP, "SMP Server",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "SimpleX SMP server service"
                setShowBadge(false)
            },
            NotificationChannel(CH_MESSAGES, "Messages",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming encrypted messages"
                setShowBadge(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 100, 50, 100)
            },
            NotificationChannel(CH_CALLS, "Voice Calls",
                NotificationManager.IMPORTANCE_MAX).apply {
                description = "Incoming encrypted voice calls"
                setShowBadge(true)
            },
            NotificationChannel(CH_TRANSPORT, "Transport",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Multi-transport status"
                setShowBadge(false)
            }
        ).forEach { nm.createNotificationChannel(it) }
        Log.i(TAG, "Notification channels created")
    }
}
