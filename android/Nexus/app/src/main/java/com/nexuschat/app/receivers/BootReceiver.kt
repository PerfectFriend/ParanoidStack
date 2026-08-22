package com.nexuschat.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexuschat.app.services.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        Log.i("NexusChat/Boot", "Device booted — starting all services")
        ctx.startForegroundService(Intent(ctx, TorService::class.java))
        ctx.startForegroundService(Intent(ctx, V2RayService::class.java))
        ctx.startForegroundService(Intent(ctx, SmpServerService::class.java))
        ErrorRecoveryManager.ServiceType.values().forEach {
            ErrorRecoveryManager.getInstance().registerService(it)
        }
        ChainProxy.getInstance().start()
        DnsOverTor.getInstance().start()
        CoverTrafficScheduler.getInstance().start()
        Log.i("NexusChat/Boot", "All services started on boot")
    }
}
