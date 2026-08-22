package com.n3.app.audit

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.n3.app.crypto.N3Crypto
import java.io.File

data class AuditEvent(
    val id: String,
    val timestamp: Long,
    val type: String,
    val source: String,
    val details: String = "",
    val level: String = "info"
)

class AuditLogManager(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/Audit"
        private const val MAX_EVENTS = 1000
        private const val PRUNE_AGE_MS = 30L * 24 * 60 * 60 * 1000
        private const val FILE_NAME = "audit_log.enc"
    }

    private val gson = Gson()
    private val file = File(ctx.filesDir, FILE_NAME)
    private var events: MutableList<AuditEvent> = load()

    private fun load(): MutableList<AuditEvent> {
        if (!file.exists()) return mutableListOf()
        return try {
            val encrypted = file.readText()
            val json = N3Crypto.decryptString(encrypted) ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<MutableList<AuditEvent>>() {}.type
                gson.fromJson<MutableList<AuditEvent>>(json, type) ?: mutableListOf()
            } else mutableListOf()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load audit log: ${e.message}")
            mutableListOf()
        }
    }

    fun save() {
        try {
            val json = gson.toJson(events)
            val encrypted = N3Crypto.encryptString(json)
            file.parentFile?.mkdirs()
            file.writeText(encrypted)
        } catch (e: Exception) { Log.w(TAG, "Failed to save audit log: ${e.message}") }
    }

    fun record(type: String, source: String, details: String = "", level: String = "info") {
        val event = AuditEvent(
            id = java.util.UUID.randomUUID().toString().take(8),
            timestamp = System.currentTimeMillis(),
            type = type,
            source = source,
            details = details,
            level = level
        )
        events.add(event)
        if (events.size > MAX_EVENTS) events.removeAt(0)
        save()
        Log.i(TAG, "$type[$source]: ${details.take(80)}")
    }

    fun getAll(): List<AuditEvent> = events.toList().sortedByDescending { it.timestamp }

    fun getRecent(limit: Int = 50): List<AuditEvent> = getAll().take(limit)

    fun getByType(type: String): List<AuditEvent> = events.filter { it.type == type }.sortedByDescending { it.timestamp }

    fun prune() {
        val cutoff = System.currentTimeMillis() - PRUNE_AGE_MS
        val before = events.size
        events.removeAll { it.timestamp < cutoff }
        if (events.size != before) save()
    }

    fun clear() {
        events.clear()
        if (file.exists()) file.delete()
    }

    fun destroy() { clear() }
}
