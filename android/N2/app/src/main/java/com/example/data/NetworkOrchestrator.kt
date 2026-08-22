package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Оркестратор сетевых компонентов: Tor → V2Ray → SimpleX.
 * Обеспечивает последовательный запуск с таймаутами, health-check и авто-восстановление.
 */
class NetworkOrchestrator(
    val torController: TorEmbeddedController,
    val v2rayController: V2RayEmbeddedController,
    val simplexController: SimpleXEmbeddedController,
    private val onLog: (String) -> Unit = {},
    private val onTorStatus: (Boolean) -> Unit = {},
    private val onV2RayStatus: (Boolean) -> Unit = {},
    private val onSimplexStatus: (Boolean) -> Unit = {},
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private var monitorJob: Job? = null
    private var _allStarted = false

    val isAllStarted: Boolean get() = _allStarted

    /** Запустить все компоненты последовательно с таймаутом */
    suspend fun startAll(
        smpOnionAddress: String = "",
        xftpOnionAddress: String = "",
        torTimeoutMs: Long = 120_000,
        v2rayTimeoutMs: Long = 30_000,
        simplexTimeoutMs: Long = 30_000
    ): Boolean {
        onLog("[Orchestrator] Starting all network components...")

        // 1. Tor
        if (!torController.isRunning()) {
            onLog("[Orchestrator] Starting Tor...")
            torController.start()
            val torReady = waitForCondition(torTimeoutMs) { torBootstrapCompleted() }
            if (!torReady) {
                onLog("[Orchestrator] Tor failed to bootstrap within ${torTimeoutMs}ms")
                onTorStatus(false)
                return false
            }
            onTorStatus(true)
            onLog("[Orchestrator] Tor is ACTIVE")
        } else {
            onLog("[Orchestrator] Tor already running")
        }

        // 2. V2Ray
        if (!v2rayController.isRunning()) {
            onLog("[Orchestrator] Starting V2Ray...")
            v2rayController.start()
            val v2rayReady = waitForCondition(v2rayTimeoutMs) { v2rayController.isRunning() }
            if (!v2rayReady) {
                onLog("[Orchestrator] V2Ray failed to start within ${v2rayTimeoutMs}ms")
                onV2RayStatus(false)
                return false
            }
            onV2RayStatus(true)
            onLog("[Orchestrator] V2Ray is ACTIVE")
        } else {
            onLog("[Orchestrator] V2Ray already running")
        }

        // 3. SimpleX
        if (!simplexController.isRunning()) {
            onLog("[Orchestrator] Starting SimpleX...")
            simplexController.start(smpOnionAddress, xftpOnionAddress)
            val simplexReady = waitForCondition(simplexTimeoutMs) { simplexController.isRunning() }
            if (!simplexReady) {
                onLog("[Orchestrator] SimpleX failed to start within ${simplexTimeoutMs}ms")
                onSimplexStatus(false)
                return false
            }
            onSimplexStatus(true)
            onLog("[Orchestrator] SimpleX is ACTIVE")
        } else {
            onLog("[Orchestrator] SimpleX already running")
        }

        _allStarted = true
        onLog("[Orchestrator] All network components are running")
        return true
    }

    /** Остановить все компоненты */
    fun stopAll() {
        onLog("[Orchestrator] Stopping all network components...")
        stopMonitor()
        simplexController.stop()
        onSimplexStatus(false)
        v2rayController.stop()
        onV2RayStatus(false)
        torController.stop()
        onTorStatus(false)
        _allStarted = false
        onLog("[Orchestrator] All network components stopped")
    }

    /** Перезапустить Tor (с авто-fallback мостов) */
    fun restartTor() {
        onLog("[Orchestrator] Restarting Tor...")
        torController.stop()
        Thread.sleep(2000)
        torController.start()
    }

    /** Перезапустить V2Ray */
    fun restartV2Ray() {
        onLog("[Orchestrator] Restarting V2Ray...")
        v2rayController.stop()
        Thread.sleep(1000)
        v2rayController.start()
    }

    /** Перезапустить SimpleX */
    fun restartSimplex(smpOnionAddress: String = "", xftpOnionAddress: String = "") {
        onLog("[Orchestrator] Restarting SimpleX...")
        simplexController.stop()
        Thread.sleep(1000)
        simplexController.start(smpOnionAddress, xftpOnionAddress)
    }

    /** Проверить, все ли компоненты работают */
    fun isAllRunning(): Boolean =
        torController.isRunning() && v2rayController.isRunning() && simplexController.isRunning()

    /** Получить статус всех компонентов */
    fun getStatusReport(): String = buildString {
        appendLine("=== Network Orchestrator Status ===")
        appendLine("Tor:       ${if (torController.isRunning()) "RUNNING" else "STOPPED"}")
        appendLine("V2Ray:     ${if (v2rayController.isRunning()) "RUNNING" else "STOPPED"}")
        appendLine("SimpleX:   ${if (simplexController.isRunning()) "RUNNING" else "STOPPED"}")
        appendLine("Tor bridges: ${torController.getBridgeStatus()}")
        appendLine("Onion addr:  ${torController.getOnionHostname() ?: "none"}")
        append("All started: $_allStarted")
    }

    /** Запустить мониторинг с авто-восстановлением */
    fun startMonitor(intervalMs: Long = 30_000) {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                try {
                    if (!torController.isRunning()) {
                        onLog("[Orchestrator] Monitor: Tor is down, restarting...")
                        restartTor()
                    }
                    if (!v2rayController.isRunning()) {
                        onLog("[Orchestrator] Monitor: V2Ray is down, restarting...")
                        restartV2Ray()
                    }
                    if (!simplexController.isRunning()) {
                        onLog("[Orchestrator] Monitor: SimpleX is down, restarting...")
                        restartSimplex()
                    }
                } catch (e: Exception) {
                    onLog("[Orchestrator] Monitor error: ${e.message}")
                }
            }
        }
        onLog("[Orchestrator] Monitor started (interval=${intervalMs}ms)")
    }

    /** Остановить мониторинг */
    fun stopMonitor() {
        monitorJob?.cancel()
        monitorJob = null
        onLog("[Orchestrator] Monitor stopped")
    }

    /** Освободить ресурсы */
    fun dispose() {
        stopAll()
        scope.cancel()
    }

    // --- private helpers ---

    private suspend fun waitForCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            delay(1000)
        }
        return condition()
    }

    private fun torBootstrapCompleted(): Boolean {
        return torController.isBootstrapCompleted()
    }
}
