package com.n3.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.aead.AeadConfig
import com.n3.app.util.LocaleManager
import org.conscrypt.Conscrypt
import java.security.Security

class N3App : Application() {
    companion object {
        const val CH_TOR = "n3_tor"
        const val CH_SMP = "n3_smp"
        const val CH_MSG = "n3_messages"

        lateinit var instance: N3App; private set
        lateinit var securePrefs: android.content.SharedPreferences; private set
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleManager.detectAndApply(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        AeadConfig.register()

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        securePrefs = EncryptedSharedPreferences.create(
            this, "n3_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        createNotificationChannels()
        Log.i("N3App", "initialised locale=${LocaleManager.getCurrentLang(this)}")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            NotificationChannel(CH_TOR, "Tor", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Tor daemon"; setShowBadge(false)
            },
            NotificationChannel(CH_SMP, "SMP Server", NotificationManager.IMPORTANCE_LOW).apply {
                description = "SMP messaging"; setShowBadge(false)
            },
            NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming messages"; setShowBadge(true)
                enableVibration(true); vibrationPattern = longArrayOf(0, 100, 50, 100)
            }
        ).forEach { nm.createNotificationChannel(it) }
    }
}
