package com.n3.app.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.n3.app.crypto.N3Crypto
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class StoredMessage(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String = "",
    val text: String = "",
    val senderKey: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val encrypted: Boolean = true,
    val expiresAt: Long = 0,
    val status: String = "sent",
    val attachments: List<String> = emptyList()
)

class MessageStore(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/MsgStore"
        private const val MAX_MESSAGES = 1000
    }

    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, MutableList<StoredMessage>>()
    private val msgDir = File(ctx.filesDir, "messages").also { it.mkdirs() }
    private val attachDir = File(ctx.filesDir, "attachments").also { it.mkdirs() }

    fun addMessage(msg: StoredMessage) {
        val list = getConversationMessages(msg.conversationId).toMutableList()
        list.add(msg)
        if (list.size > MAX_MESSAGES) list.removeAt(0)
        saveConversation(msg.conversationId, list)
        cache[msg.conversationId] = list.toMutableList()
    }

    fun getConversationMessages(convId: String): List<StoredMessage> {
        cache[convId]?.let { return it.toList() }
        val file = File(msgDir, sanitize(convId))
        if (!file.exists()) return emptyList()
        return try {
            val encrypted = file.readText()
            val decrypted = N3Crypto.decryptString(encrypted, "msgstore") ?: return emptyList()
            val type = object : TypeToken<List<StoredMessage>>() {}.type
            val msgs: List<StoredMessage> = gson.fromJson(decrypted, type) ?: emptyList()
            cache[convId] = msgs.toMutableList()
            msgs.filter { it.expiresAt == 0L || it.expiresAt > System.currentTimeMillis() }
        } catch (e: Exception) { Log.e(TAG, "Load ${e.message}"); emptyList() }
    }

    fun getAllConversationIds(): List<String> {
        return msgDir.listFiles()?.map { it.name }?.filter { it.length <= 64 }?.toList() ?: emptyList()
    }

    fun deleteMessage(id: String) {
        for ((convId, msgs) in cache) {
            val removed = msgs.removeAll { it.id == id }
            if (removed) { saveConversation(convId, msgs); return }
        }
    }

    fun deleteConversation(convId: String) {
        cache.remove(convId)
        File(msgDir, sanitize(convId)).delete()
    }

    fun pruneExpired() {
        for (convId in getAllConversationIds()) {
            val msgs = getConversationMessages(convId)
            val valid = msgs.filter { it.expiresAt == 0L || it.expiresAt > System.currentTimeMillis() }
            if (valid.size != msgs.size) saveConversation(convId, valid)
        }
    }

    fun getTotalCount(): Int {
        return getAllConversationIds().sumOf { getConversationMessages(it).size }
    }

    fun saveAttachment(msgId: String, dataB64: String): Boolean = try {
        val encrypted = N3Crypto.encryptString(dataB64, "att_$msgId")
        File(attachDir, sanitize(msgId)).writeText(encrypted)
        true
    } catch (e: Exception) { Log.e(TAG, "Save attach ${e.message}"); false }

    fun getAttachment(msgId: String): String? {
        return try {
            val file = File(attachDir, sanitize(msgId))
            if (!file.exists()) null
            else {
                val encrypted = file.readText()
                N3Crypto.decryptString(encrypted, "att_$msgId")
            }
        } catch (e: Exception) { Log.e(TAG, "Load attach ${e.message}"); null }
    }

    fun deleteAttachment(msgId: String) { File(attachDir, sanitize(msgId)).delete() }

    fun getAttachmentMeta(msgId: String): String? {
        val data = getAttachment(msgId) ?: return null
        return try {
            val meta = gson.fromJson(data, Map::class.java)
            gson.toJson(mapOf("name" to (meta["name"] ?: "file"), "size" to (meta["size"] ?: "0")))
        } catch (e: Exception) { null }
    }

    fun destroy() {
        cache.clear()
        msgDir.listFiles()?.forEach { it.delete() }
        attachDir.listFiles()?.forEach { it.delete() }
    }

    private fun saveConversation(convId: String, msgs: List<StoredMessage>) {
        try {
            val json = gson.toJson(msgs)
            val encrypted = N3Crypto.encryptString(json, "msgstore")
            File(msgDir, sanitize(convId)).writeText(encrypted)
        } catch (e: Exception) { Log.e(TAG, "Save ${e.message}") }
    }

    private fun sanitize(s: String): String {
        return s.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
    }
}
