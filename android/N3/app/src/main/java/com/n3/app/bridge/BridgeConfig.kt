package com.n3.app.bridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * BridgeConfig - Persistent storage for bridge/VPN/proxy elements and active chain.
 * 
 * Uses SharedPreferences with Gson serialization to store:
 * - BridgeElement list (all configured bridges/VPNs/proxies)
 * - Active chain (ordered list of bridge IDs forming the transport chain)
 * 
 * Thread-safe: Operations are atomic at SharedPreferences level.
 * 
 * @param ctx Application context for SharedPreferences access
 */
class BridgeConfig(private val ctx: Context) {

    companion object {
        private const val TAG = "N3/BridgeCfg"
        private const val PREFS = "n3_bridges"
        private const val KEY_ELEMENTS = "bridge_elements"
        private const val KEY_CHAIN = "active_chain"
    }

    /** SharedPreferences instance for persistent storage. */
    private val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    
    /** Gson instance for JSON serialization/deserialization. */
    private val gson = Gson()

    /**
     * Get all stored bridge elements.
     * @return Mutable list of BridgeElement (empty if none stored)
     */
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

    /**
     * Save entire bridge element list (replaces existing).
     * @param elements List of BridgeElement to store
     */
    fun save(elements: List<BridgeElement>) {
        prefs.edit().putString(KEY_ELEMENTS, gson.toJson(elements)).apply()
    }

    /**
     * Add a new bridge element.
     * @param element BridgeElement to add
     */
    fun add(element: BridgeElement) {
        val list = getAll()
        list.add(element)
        save(list)
    }

    /**
     * Remove a bridge element by ID.
     * @param id Unique identifier of the element to remove
     */
    fun remove(id: String) {
        val list = getAll()
        list.removeAll { it.id == id }
        save(list)
    }

    /**
     * Update an existing bridge element.
     * @param element BridgeElement with updated values (matched by ID)
     */
    fun update(element: BridgeElement) {
        val list = getAll()
        val idx = list.indexOfFirst { it.id == element.id }
        if (idx >= 0) { list[idx] = element; save(list) }
    }

    /**
     * Enable or disable a bridge element.
     * @param id Element ID
     * @param enabled True to enable, false to disable
     */
    fun setEnabled(id: String, enabled: Boolean) {
        getAll().find { it.id == id }?.let { update(it.copy(enabled = enabled)) }
    }

    /**
     * Get the currently active transport chain.
     * Chain is an ordered list of bridge element IDs.
     * @return List of element IDs in chain order (empty if none)
     */
    fun getActiveChain(): List<String> {
        val raw = prefs.getString(KEY_CHAIN, null) ?: return emptyList()
        return try { gson.fromJson(raw, Array<String>::class.java).toList() } catch (e: Exception) { emptyList() }
    }

    /**
     * Set the active transport chain.
     * @param ids Ordered list of bridge element IDs
     */
    fun setActiveChain(ids: List<String>) {
        prefs.edit().putString(KEY_CHAIN, gson.toJson(ids)).apply()
    }

    /**
     * Clear the active chain (no transport chain configured).
     */
    fun clearChain() = prefs.edit().remove(KEY_CHAIN).apply()
}