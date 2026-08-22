package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i("BootReceiver", "Boot completed — restarting foreground services")
            PushNotificationForegroundService.start(context)
            SmpNotificationService.start(context)
        }
    }
}
