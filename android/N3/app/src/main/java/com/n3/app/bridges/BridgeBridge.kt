package com.n3.app.bridges

import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.n3.app.bridge.*
import com.n3.app.util.LocalizedStrings
import kotlinx.coroutines.*

class BridgeBridge(private val ctx: Context, private val onProgress: (String) -> Unit) {
    private val config = BridgeConfig(ctx)
    private val orchestrator = BridgeOrchestrator(ctx)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @JavascriptInterface fun getAll(): String = gson.toJson(config.getAll())

    @JavascriptInterface fun addFromLink(link: String): String {
        val element = BridgeImporter.importV2Ray(link)
        if (element != null) { config.add(element); return element.id }
        return ""
    }

    @JavascriptInterface fun addFromType(type: String): String {
        val bt = try { BridgeType.valueOf(type.uppercase()) } catch (e: Exception) { return "" }
        val element = BridgeElement(type = bt, name = bt.name)
        config.add(element)
        return element.id
    }

    @JavascriptInterface fun remove(id: String) { config.remove(id) }

    @JavascriptInterface fun setEnabled(id: String, enabled: Boolean) { config.setEnabled(id, enabled) }

    @JavascriptInterface fun updateConfig(id: String, configJson: String) {
        val list = config.getAll()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) { list[idx] = list[idx].copy(config = configJson); config.save(list) }
    }

    @JavascriptInterface fun getActiveChain(): String = gson.toJson(config.getActiveChain())

    @JavascriptInterface fun testNetworkAsync(callback: String) {
        scope.launch {
            val status = orchestrator.checkNetwork()
            (ctx as? com.n3.app.MainActivity)?.evalJs("$callback(${gson.toJson(status)})")
        }
    }

    @JavascriptInterface fun testAllAsync(callback: String) {
        scope.launch {
            onProgress("Testing bridges...")
            val elements = config.getAll().filter { it.enabled }
            val results = elements.map { orchestrator.testElement(it) }
            (ctx as? com.n3.app.MainActivity)?.evalJs("$callback(${gson.toJson(results)})")
        }
    }

    @JavascriptInterface fun buildChainAsync(callback: String) {
        scope.launch {
            onProgress("Building chain...")
            val elements = config.getAll()
            val results = orchestrator.buildAndTestChain(elements)
            (ctx as? com.n3.app.MainActivity)?.evalJs("$callback(${gson.toJson(results)})")
        }
    }

    @JavascriptInterface fun getStrings(): String = LocalizedStrings.getAll(ctx)

    @JavascriptInterface fun startMonitor(callback: String) {
        orchestrator.startChainMonitor(scope) { msg ->
            onProgress(msg)
            (ctx as? com.n3.app.MainActivity)?.evalJs("$callback(${gson.toJson(msg)})")
        }
    }

    @JavascriptInterface fun getChainHealth(): String {
        val chain = config.getActiveChain()
        val all = config.getAll()
        val health = chain.map { id ->
            if (id == "tor") {
                val ok = try { java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2000) }; true } catch (e: Exception) { false }
                mapOf("id" to "tor", "name" to "Tor", "ok" to ok)
            } else {
                val el = all.find { it.id == id }
                mapOf("id" to id, "name" to (el?.name ?: id.take(8)), "ok" to true)
            }
        }
        return gson.toJson(health)
    }

    fun destroy() { scope.cancel() }
}
