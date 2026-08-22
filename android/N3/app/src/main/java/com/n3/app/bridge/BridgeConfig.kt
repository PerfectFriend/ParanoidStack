package com.n3.app.bridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BridgeConfig(private val ctx: Context) {
    companion object {
        private const val TAG = "N3/BridgeCfg"
        private const val PREFS = "n3_bridges"
        private const val KEY_ELEMENTS = "bridge_elements"
        private const val KEY_CHAIN = "active_chain"
    }

    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): MutableList<BridgeElement> {
        val raw = prefs.getString(KEY_ELEMENTS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<BridgeElement>>() {}.type
            gson.fromJson(raw, type) ?: mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse bridges: ${e.message}")
            mutableListOf()
        }
    }

    fun save(elements: List<BridgeElement>) {
        prefs.edit().putString(KEY_ELEMENTS, gson.toJson(elements)).apply()
    }

    fun add(element: BridgeElement) {
        val list = getAll()
        list.add(element)
        save(list)
    }

    fun remove(id: String) {
        val list = getAll()
        list.removeAll { it.id == id }
        save(list)
    }

    fun update(element: BridgeElement) {
        val list = getAll()
        val idx = list.indexOfFirst { it.id == element.id }
        if (idx >= 0) { list[idx] = element; save(list) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        getAll().find { it.id == id }?.let { update(it.copy(enabled = enabled)) }
    }

    fun getActiveChain(): List<String> {
        val raw = prefs.getString(KEY_CHAIN, null) ?: return emptyList()
        return try { gson.fromJson(raw, Array<String>::class.java).toList() } catch (e: Exception) { emptyList() }
    }

    fun setActiveChain(ids: List<String>) {
        prefs.edit().putString(KEY_CHAIN, gson.toJson(ids)).apply()
    }

    fun clearChain() = prefs.edit().remove(KEY_CHAIN).apply()
}
