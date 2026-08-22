package com.n3.app.bridges

import android.content.Context
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.n3.app.contact.Contact
import com.n3.app.contact.ContactManager
import com.n3.app.crypto.IdentityManager
import com.n3.app.profile.ProfileManager

class ContactBridge(private val ctx: Context) {
    private val cm = ContactManager(ctx)
    private val pm = ProfileManager(ctx)
    private val gson = Gson()

    @JavascriptInterface fun getAll(): String = try { gson.toJson(cm.getAll()) } catch (e: Exception) { "[]" }

    @JavascriptInterface fun get(id: String): String = try { gson.toJson(cm.get(id)) } catch (e: Exception) { "null" }

    @JavascriptInterface fun add(json: String): Boolean {
        val c = try { gson.fromJson(json, Contact::class.java) } catch (e: Exception) { return false }
        return cm.add(c)
    }

    @JavascriptInterface fun update(json: String): Boolean {
        val c = try { gson.fromJson(json, Contact::class.java) } catch (e: Exception) { return false }
        return cm.update(c)
    }

    @JavascriptInterface fun remove(id: String): Boolean = cm.remove(id)

    @JavascriptInterface fun search(query: String): String = try { gson.toJson(cm.search(query)) } catch (e: Exception) { "[]" }

    @JavascriptInterface fun getMyPublicKey(): String = try { IdentityManager.getPublicKey() } catch (e: Exception) { "" }

    @JavascriptInterface fun getMyFingerprint(): String = try { IdentityManager.getFingerprint() } catch (e: Exception) { "" }

    @JavascriptInterface fun initIdentity(): Boolean = try {
        IdentityManager.initFromProfile(pm)
        IdentityManager.isReady()
    } catch (e: Exception) { false }

    @JavascriptInterface fun importContact(name: String, pubKey: String, smpAddr: String): Boolean {
        if (name.isBlank() || pubKey.isBlank()) return false
        if (pubKey.length < 16) return false
        return cm.add(Contact(name = name.trim(), publicKey = pubKey.trim(), smpAddress = smpAddr.trim()))
    }

    @JavascriptInterface fun count(): Int = try { cm.count() } catch (e: Exception) { 0 }
}
