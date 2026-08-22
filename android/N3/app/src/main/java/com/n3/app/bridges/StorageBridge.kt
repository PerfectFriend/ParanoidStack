package com.n3.app.bridges

import android.content.Context
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.n3.app.storage.MessageStore
import com.n3.app.storage.StoredMessage
import com.n3.app.profile.ProfileManager

class StorageBridge(private val ctx: Context) {
    private val store = MessageStore(ctx)
    private val gson = Gson()

    @JavascriptInterface fun saveMessage(json: String): String {
        val msg = try { gson.fromJson(json, StoredMessage::class.java) } catch (e: Exception) { return "" }
        store.addMessage(msg)
        return msg.id
    }

    @JavascriptInterface fun getMessages(convId: String): String = try {
        gson.toJson(store.getConversationMessages(convId))
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun getConversations(): String = try {
        gson.toJson(store.getAllConversationIds())
    } catch (e: Exception) { "[]" }

    @JavascriptInterface fun deleteMessage(id: String) { try { store.deleteMessage(id) } catch (e: Exception) {} }

    @JavascriptInterface fun deleteConversation(convId: String) { try { store.deleteConversation(convId) } catch (e: Exception) {} }

    @JavascriptInterface fun getTotalCount(): Int = try { store.getTotalCount() } catch (e: Exception) { 0 }

    @JavascriptInterface fun pruneExpired() { try { store.pruneExpired() } catch (e: Exception) {} }

    @JavascriptInterface fun destroy() { try { store.destroy() } catch (e: Exception) {} }

    @JavascriptInterface fun createMessage(text: String, convId: String, expiresInSec: Long): String {
        val msg = StoredMessage(
            conversationId = convId,
            text = text,
            expiresAt = if (expiresInSec > 0) System.currentTimeMillis() + expiresInSec * 1000 else 0
        )
        store.addMessage(msg)
        return gson.toJson(msg)
    }

    @JavascriptInterface fun saveAttachment(msgId: String, dataB64: String): Boolean = store.saveAttachment(msgId, dataB64)
    @JavascriptInterface fun getAttachment(msgId: String): String? = store.getAttachment(msgId)
    @JavascriptInterface fun deleteAttachment(msgId: String) { store.deleteAttachment(msgId) }
    @JavascriptInterface fun getAttachmentMeta(msgId: String): String? = store.getAttachmentMeta(msgId)
}
