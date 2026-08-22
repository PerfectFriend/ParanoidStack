package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.protocols.ProtocolOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProtocolOrchestratorService : Service() {

    private val tag = "ProtocolOrchSvc"
    private val NOTIFICATION_ID = 2003
    private var orchestrator: ProtocolOrchestrator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        orchestrator = ProtocolOrchestrator(
            context = this,
            onLog = { Log.i(tag, it) }
        )
        scope.launch { orchestrator?.startFullNode() }
        Log.i(tag, "ProtocolOrchestrator foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            orchestrator?.stopNode()
            orchestrator?.dispose()
            orchestrator = null
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        orchestrator?.stopNode()
        orchestrator?.dispose()
        orchestrator = null
        scope.cancel()
        Log.i(tag, "ProtocolOrchestrator service stopped")
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_SERVICE)
            .setContentTitle("N2 Protocol Node")
            .setContentText("Protocol orchestrator active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.example.action.STOP_PROTOCOL_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, ProtocolOrchestratorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProtocolOrchestratorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
