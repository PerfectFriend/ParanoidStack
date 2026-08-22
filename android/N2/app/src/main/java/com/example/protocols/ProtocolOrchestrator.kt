package com.example.protocols

import android.content.Context
import com.example.protocols.mesh.NodeMeshManager
import com.example.protocols.storage.ArchiveCloud
import com.example.protocols.storage.CloudConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ProtocolOrchestrator(
    private val context: Context,
    private val onLog: (String) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    val registry = ProtocolRegistry(context)
    val autoDetector = NetworkAutoDetector(context, registry, scope, onLog)
    val archiveCloud = ArchiveCloud(context, CloudConfig(), scope, onLog)
    val nodeMesh = NodeMeshManager(
        listenPort = 9010,
        bootstrapNodes = listOf(
            "bootstrap1.n2mesh.net:9010",
            "bootstrap2.n2mesh.net:9010"
        ),
        scope = scope, onLog = onLog
    )

    private val prefs = context.getSharedPreferences("protocol_orch_prefs", Context.MODE_PRIVATE)
    private val _phase = MutableStateFlow("IDLE")
    val phase: StateFlow<String> = _phase.asStateFlow()

    private val _started = MutableStateFlow(false)
    val isStarted: Boolean get() = _started.value

    companion object {
        private const val CONFIG_VERSION = 1
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_STRATEGY = "persisted_strategy"
        private const val KEY_TRANSPORT = "persisted_transport"
    }

    /// Полный старт: детекция → обход → транспорт → облако → mesh
    suspend fun startFullNode() {
        if (_started.value) return
        _started.value = true
        onLog("[ProtocolOrchestrator] Starting full node...")

        // Migration: check config version
        migrateConfig()

        // Try restoring last working config
        val savedStrategy = prefs.getString(KEY_STRATEGY, null)
        val savedTransport = prefs.getString(KEY_TRANSPORT, null)

        if (savedStrategy != null && savedTransport != null) {
            onLog("[ProtocolOrch] Restoring saved config: strategy=$savedStrategy transport=$savedTransport")
            _phase.value = "DETECT"
            val state = autoDetector.runFullDetection()
            _phase.value = "TRANSPORT"
            for (protoId in listOf(savedTransport)) {
                val instance = registry.get(protoId)
                if (instance != null && instance.status != ProtocolStatus.RUNNING) {
                    registry.updateStatus(protoId, ProtocolStatus.RUNNING)
                    onLog("[ProtocolOrch] Restored transport: $protoId")
                }
            }
            _phase.value = "STORAGE"
            archiveCloud.start()
            if (state.publicIp.isNotEmpty()) {
                archiveCloud.discoverPeers(listOf("bootstrap.n2cloud.net:9001"))
            }
            onLog("[ProtocolOrch] ArchiveCloud started")
            _phase.value = "MESH"
            nodeMesh.start(state.publicIp.ifEmpty { "127.0.0.1" })
            onLog("[ProtocolOrch] NodeMesh started")
            _phase.value = "RUNNING"
            onLog("[ProtocolOrch] Full node is ACTIVE (restored)")
        } else {
            // Full detection on first run
            _phase.value = "DETECT"
            val (state, strategy, transport) = autoDetector.autoConfigure()
            onLog("[ProtocolOrch] Network: ${state.publicIp}, strategy: ${strategy.name}, transport: ${transport.transportName}")

            // Persist selected config
            prefs.edit()
                .putString(KEY_STRATEGY, strategy.name)
                .putString(KEY_TRANSPORT, transport.transportId)
                .apply()

            _phase.value = "TRANSPORT"
            for (protoId in strategy.protocolStack) {
                val instance = registry.get(protoId)
                if (instance != null && instance.status != ProtocolStatus.RUNNING) {
                    registry.updateStatus(protoId, ProtocolStatus.RUNNING)
                    onLog("[ProtocolOrch] Started transport: $protoId")
                }
            }

            _phase.value = "STORAGE"
            archiveCloud.start()
            if (state.publicIp.isNotEmpty()) {
                archiveCloud.discoverPeers(listOf("bootstrap.n2cloud.net:9001"))
            }
            onLog("[ProtocolOrch] ArchiveCloud started")

            _phase.value = "MESH"
            nodeMesh.start(state.publicIp.ifEmpty { "127.0.0.1" })
            onLog("[ProtocolOrch] NodeMesh started")

            _phase.value = "RUNNING"
            onLog("[ProtocolOrch] Full node is ACTIVE")
        }
    }

    private fun migrateConfig() {
        val version = prefs.getInt(KEY_CONFIG_VERSION, 0)
        if (version < CONFIG_VERSION) {
            onLog("[ProtocolOrch] Migrating config from v$version to v$CONFIG_VERSION")
            when (version) {
                0 -> {
                    // v0 → v1: clear legacy keys, set default
                    prefs.edit().clear().putInt(KEY_CONFIG_VERSION, CONFIG_VERSION).apply()
                }
            }
            prefs.edit().putInt(KEY_CONFIG_VERSION, CONFIG_VERSION).apply()
        }
    }

    /// Остановить ноду
    fun stopNode() {
        _phase.value = "STOPPING"
        nodeMesh.stop()
        archiveCloud.stop()
        registry.dispose()
        _started.value = false
        _phase.value = "IDLE"
        onLog("[ProtocolOrch] Node stopped")
    }

    /// Сгенерировать отчёт о состоянии
    fun getStatusReport(): String = buildString {
        appendLine("=== ProtocolOrchestrator Status ===")
        appendLine("Phase: ${_phase.value}")
        appendLine()

        appendLine("-- Registry --")
        for (instance in registry.getAll().sortedBy { it.info.category.name }) {
            appendLine("  ${instance.info.name} (${instance.info.category}): ${instance.status}")
            if (instance.errorMessage.isNotEmpty()) appendLine("    Error: ${instance.errorMessage}")
        }

        appendLine()
        appendLine("-- Network --")
        val ns = autoDetector.networkState.value
        appendLine("  Online: ${ns.isOnline}, IP: ${ns.publicIp}")
        appendLine("  DNS: ${ns.hasDns}, Firewall: ${ns.detectedFirewall}")
        appendLine("  Blocked: ${ns.blockedDomains.size} domains")

        val ts = autoDetector.optimalTransport.value
        if (ts != null) appendLine("  Optimal transport: ${ts.transportName} (score=${ts.score})")

        appendLine()
        appendLine("-- ArchiveCloud --")
        val cs = archiveCloud.stats.value
        appendLine("  Blobs: ${cs.totalBlobs}, Size: ${cs.totalSizeBytes / 1024}KB")
        appendLine("  Used: ${cs.usedSpaceBytes / 1024 / 1024}MB / Peers: ${cs.peerCount}")

        appendLine()
        appendLine("-- NodeMesh --")
        val ms = nodeMesh.meshStats.value
        appendLine("  Nodes: ${ms.knownNodes} (${ms.reachableNodes} reachable)")
        appendLine("  Our ID: ${nodeMesh.ourInfo.value.nodeId}")
    }

    fun getStatusMap(): Map<String, String> = mapOf(
        "phase" to _phase.value,
        "ip" to autoDetector.networkState.value.publicIp,
        "transport" to (autoDetector.optimalTransport.value?.transportName ?: "none"),
        "nodes" to nodeMesh.meshStats.value.knownNodes.toString(),
        "blobs" to archiveCloud.stats.value.totalBlobs.toString(),
        "peers" to archiveCloud.stats.value.peerCount.toString()
    )

    fun dispose() {
        stopNode()
        scope.cancel()
    }
}
