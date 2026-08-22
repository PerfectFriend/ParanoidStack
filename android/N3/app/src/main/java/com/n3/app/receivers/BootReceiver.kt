package com.n3.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.n3.app.services.TorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ctx.startForegroundService(Intent(ctx, TorService::class.java))
    }
}
