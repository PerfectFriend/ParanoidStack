package com.n3.app.contact

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.n3.app.crypto.N3Crypto
import java.util.UUID

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val publicKey: String = "",
    val smpAddress: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = 0,
    val favorite: Boolean = false,
    val notes: String = ""
)

class ContactManager(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/Contacts"
        private const val PREFS = "n3_contacts"
        private const val KEY_DATA = "contacts_enc"
    }

    private val gson = Gson()
    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(): List<Contact> = decryptContacts()

    fun get(id: String): Contact? = getAll().find { it.id == id }

    fun add(contact: Contact): Boolean {
        return try {
            val list = getAll().toMutableList()
            if (list.any { it.id == contact.id || it.name == contact.name }) return false
            list.add(contact)
            encryptContacts(list); true
        } catch (e: Exception) { Log.e(TAG, "Add: ${e.message}"); false }
    }

    fun update(contact: Contact): Boolean {
        return try {
            val list = getAll().toMutableList()
            val idx = list.indexOfFirst { it.id == contact.id }
            if (idx < 0) return false
            list[idx] = contact
            encryptContacts(list); true
        } catch (e: Exception) { Log.e(TAG, "Update: ${e.message}"); false }
    }

    fun remove(id: String): Boolean = try {
        val list = getAll().toMutableList()
        list.removeAll { it.id == id }
        encryptContacts(list); true
    } catch (e: Exception) { Log.e(TAG, "Remove: ${e.message}"); false }

    fun search(query: String): List<Contact> {
        val q = query.lowercase()
        return getAll().filter { it.name.lowercase().contains(q) || it.publicKey.contains(q) }
    }

    private fun encryptContacts(contacts: List<Contact>) {
        val json = gson.toJson(contacts)
        val encrypted = N3Crypto.encryptString(json, "contacts")
        prefs.edit().putString(KEY_DATA, encrypted).apply()
    }

    private fun decryptContacts(): List<Contact> {
        val encrypted = prefs.getString(KEY_DATA, null) ?: return emptyList()
        val json = N3Crypto.decryptString(encrypted, "contacts") ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Contact>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun count(): Int = getAll().size
    fun destroy() { prefs.edit().clear().apply() }
}
