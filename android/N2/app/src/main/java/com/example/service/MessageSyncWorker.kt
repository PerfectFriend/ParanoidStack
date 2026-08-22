package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SMPAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("MessageSyncWorker", "Running sync...")
        val agent = smpAgent
        if (agent == null) {
            Log.w("MessageSyncWorker", "SMPAgent not set, retrying")
            return@withContext Result.retry()
        }
        try {
            val contacts = agent.getContacts()
            if (contacts.isEmpty()) {
                Log.d("MessageSyncWorker", "No contacts, sync skipped")
                return@withContext Result.success()
            }
            val synced = agent.syncPendingMessages()
            Log.i("MessageSyncWorker", "Sync done: $synced/${contacts.size} contacts")
            Result.success()
        } catch (e: Exception) {
            Log.e("MessageSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "message_sync"
        var smpAgent: SMPAgent? = null
    }
}
