package com.example.protocols.workers

import com.example.protocols.ProtocolOrchestrator
import com.example.protocols.ProtocolStatus
import com.example.data.TorEmbeddedController
import com.example.data.V2RayEmbeddedController
import com.example.data.SimpleXEmbeddedController
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for a protocol worker that bridges the registry (ProtocolRegistry)
 * to a real embedded controller. Workers no longer simulate — they delegate to
 * the actual Tor/V2Ray/SimpleX/ArchiveCloud/NodeMesh implementations.
 */
abstract class ProtocolWorker(
    val protocolId: String,
    protected val onLog: (String) -> Unit = {}
) {
    protected val _status = MutableStateFlow(ProtocolStatus.INSTALLED)
    val status: StateFlow<ProtocolStatus> = _status.asStateFlow()

    protected val _metrics = MutableStateFlow<Map<String, Any>>(emptyMap())
    val metrics: StateFlow<Map<String, Any>> = _metrics.asStateFlow()

    protected val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    abstract suspend fun start(config: Map<String, String>): Result<Unit>
    abstract suspend fun stop(): Result<Unit>
    abstract suspend fun healthCheck(): Boolean
}

/**
 * Real Tor worker — delegates to [TorEmbeddedController].
 * Requires: context + tor-android-binary in assets/bin/.
 */
class TorWorker(
    private val context: Context,
    onLog: (String) -> Unit = {},
    private val torControllerFactory: () -> TorEmbeddedController = {
        TorEmbeddedController(context, onLog = onLog, onStatusChange = {})
    }
) : ProtocolWorker("tor", onLog) {

    private var controller: TorEmbeddedController? = null

    override suspend fun start(config: Map<String, String>): Result<Unit> = runCatching {
        val socksPort = (config["socks_port"] ?: "9050").toInt()
        val useBridges = config["use_bridges"]?.toBooleanStrictOrNull() ?: false

        val ctrl = torControllerFactory()
        if (useBridges) {
            ctrl.setBridges(com.example.data.BridgeType.OBFS4)
        }
        ctrl.start()
        controller = ctrl

        _status.value = ProtocolStatus.RUNNING
        _metrics.value = mapOf(
            "socks_port" to socksPort,
            "bridge_type" to ctrl.getBridgeStatus(),
            "use_bridges" to useBridges
        )
        onLog("Tor worker started (real — TorEmbeddedController)")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        controller?.stop()
        controller = null
        _status.value = ProtocolStatus.INSTALLED
        onLog("Tor worker stopped")
    }

    override suspend fun healthCheck(): Boolean =
        controller?.let { it.getBootstrapProgress() >= 100 } ?: (_status.value == ProtocolStatus.RUNNING)
}

/**
 * Real V2Ray worker — delegates to [V2RayEmbeddedController].
 * Requires: context + xray binary in assets/bin/ (or fallback proxy).
 */
class V2RayWorker(
    private val context: Context,
    onLog: (String) -> Unit = {},
    private val v2rayControllerFactory: () -> V2RayEmbeddedController = {
        V2RayEmbeddedController(context, onLog = onLog, onStatusChange = {})
    }
) : ProtocolWorker("v2ray", onLog) {

    private var controller: V2RayEmbeddedController? = null

    override suspend fun start(config: Map<String, String>): Result<Unit> = runCatching {
        val ctrl = v2rayControllerFactory()
        ctrl.start()
        controller = ctrl

        _status.value = ProtocolStatus.RUNNING
        _metrics.value = mapOf(
            "using_fallback" to ctrl.isUsingFallback()
        )
        onLog("V2Ray worker started (real — V2RayEmbeddedController${if (ctrl.isUsingFallback()) " — fallback" else ""})")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        controller?.stop()
        controller = null
        _status.value = ProtocolStatus.INSTALLED
        onLog("V2Ray worker stopped")
    }

    override suspend fun healthCheck(): Boolean =
        controller?.isRunning() ?: (_status.value == ProtocolStatus.RUNNING)
}

/**
 * Real SimpleX worker — delegates to [SimpleXEmbeddedController].
 * Requires: context + simplex binary in assets/bin/.
 */
class SimpleXWorker(
    private val context: Context,
    onLog: (String) -> Unit = {},
    private val simplexControllerFactory: () -> SimpleXEmbeddedController = {
        SimpleXEmbeddedController(
            context,
            onLog = onLog,
            onStatusChange = {},
            onInvitationCreated = {},
            onMessageReceived = { _, _, _, _ -> },
            onContactRequest = { _, _, _ -> },
            onGroupInvite = { _, _, _ -> },
            onChannelMessage = { _, _, _ -> },
            onGroupMessage = { _, _, _, _ -> }
        )
    }
) : ProtocolWorker("simplex", onLog) {

    private var controller: SimpleXEmbeddedController? = null

    override suspend fun start(config: Map<String, String>): Result<Unit> = runCatching {
        val smpServers = config["smp_servers"] ?: ""
        val xftpServers = config["xftp_servers"] ?: ""

        val ctrl = simplexControllerFactory()
        ctrl.start(smpServers, xftpServers)
        controller = ctrl

        _status.value = ProtocolStatus.RUNNING
        _metrics.value = mapOf(
            "smp_servers" to smpServers.ifEmpty { "default" },
            "xftp_servers" to xftpServers.ifEmpty { "default" }
        )
        onLog("SimpleX worker started (real — SimpleXEmbeddedController)")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        controller?.stop()
        controller = null
        _status.value = ProtocolStatus.INSTALLED
        onLog("SimpleX worker stopped")
    }

    override suspend fun healthCheck(): Boolean =
        controller != null
}

/**
 * Real ArchiveCloud worker — delegates to the content-addressed P2P storage layer.
 */
class ArchiveCloudWorker(
    private val context: Context,
    onLog: (String) -> Unit = {}
) : ProtocolWorker("archive_cloud", onLog) {

    private var cloud: com.example.protocols.storage.ArchiveCloud? = null

    override suspend fun start(config: Map<String, String>): Result<Unit> = runCatching {
        val maxSpaceGb = (config["max_space_gb"] ?: "20").toLongOrNull() ?: 20L
        val replication = (config["replication"] ?: "3").toIntOrNull() ?: 3
        val port = (config["public_port"] ?: "9001").toIntOrNull() ?: 9001

        val cloudConfig = com.example.protocols.storage.CloudConfig(
            maxSpaceBytes = maxSpaceGb * 1024 * 1024 * 1024,
            replicationFactor = replication,
            port = port
        )
        val c = com.example.protocols.storage.ArchiveCloud(context, cloudConfig, onLog = onLog)
        c.start()
        cloud = c

        _status.value = ProtocolStatus.RUNNING
        _metrics.value = mapOf(
            "max_space_gb" to maxSpaceGb,
            "replication" to replication,
            "port" to port
        )
        onLog("ArchiveCloud worker started (real — content-addressed storage)")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        cloud?.stop()
        cloud = null
        _status.value = ProtocolStatus.INSTALLED
        onLog("ArchiveCloud worker stopped")
    }

    override suspend fun healthCheck(): Boolean =
        cloud?.stats?.value?.let { it.totalBlobs >= 0 } ?: (_status.value == ProtocolStatus.RUNNING)
}

/**
 * Real NodeMesh (Kademlia DHT) worker — delegates to [com.example.protocols.mesh.NodeMeshManager].
 */
class NodeMeshWorker(
    private val context: Context,
    onLog: (String) -> Unit = {}
) : ProtocolWorker("kademlia", onLog) {

    private var mesh: com.example.protocols.mesh.NodeMeshManager? = null

    override suspend fun start(config: Map<String, String>): Result<Unit> = runCatching {
        val port = (config["port"] ?: "9002").toIntOrNull() ?: 9002
        val bootstrapNodes = config["bootstrap_nodes"] ?: ""

        val m = com.example.protocols.mesh.NodeMeshManager(
            listenPort = port,
            bootstrapNodes = if (bootstrapNodes.isNotBlank()) bootstrapNodes.split(",").map { it.trim() } else emptyList(),
            onLog = onLog
        )
        m.start()
        mesh = m

        _status.value = ProtocolStatus.RUNNING
        _metrics.value = mapOf(
            "port" to port,
            "bootstrap_nodes" to bootstrapNodes
        )
        onLog("NodeMesh worker (Kademlia) started (real — NodeMeshManager)")
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        mesh?.stop()
        mesh = null
        _status.value = ProtocolStatus.INSTALLED
        onLog("NodeMesh worker stopped")
    }

    override suspend fun healthCheck(): Boolean =
        mesh != null
}

class WorkerManager {
    private val workers = ConcurrentHashMap<String, ProtocolWorker>()

    fun register(worker: ProtocolWorker) {
        workers[worker.protocolId] = worker
    }

    fun get(id: String): ProtocolWorker? = workers[id]

    suspend fun startAll(configs: Map<String, Map<String, String>>) {
        for ((id, config) in configs) {
            workers[id]?.start(config)
        }
    }

    suspend fun stopAll() {
        for (worker in workers.values) {
            worker.stop()
        }
    }

    fun getStatusMap(): Map<String, ProtocolStatus> =
        workers.entries.associate { (id, worker) -> id to worker.status.value }

    fun getMetricsMap(): Map<String, Map<String, Any>> =
        workers.entries.associate { (id, worker) -> id to worker.metrics.value }
}

class ProtocolWorkerOrchestrator(
    private val orchestrator: ProtocolOrchestrator,
    private val workerManager: WorkerManager = WorkerManager(),
    private val onLog: (String) -> Unit = {}
) {
    private val workerFactories: Map<String, (android.content.Context, (String) -> Unit) -> ProtocolWorker> = mapOf(
        "tor" to { ctx, log -> TorWorker(ctx, log) },
        "v2ray" to { ctx, log -> V2RayWorker(ctx, log) },
        "simplex" to { ctx, log -> SimpleXWorker(ctx, log) },
        "archive_cloud" to { ctx, log -> ArchiveCloudWorker(ctx, log) },
        "kademlia" to { ctx, log -> NodeMeshWorker(ctx, log) }
    )

    init {
        // Workers are registered on demand via startWorkers() — no eager init.
        // Context must be injected from the caller when starting.
    }

    suspend fun startWorkers(appContext: android.content.Context) {
        onLog("[ProtocolWorkerOrchestrator] Starting workers from registry with real controllers...")
        // Register workers on first use
        for ((id, factory) in workerFactories) {
            if (workerManager.get(id) == null) {
                workerManager.register(factory(appContext, onLog))
            }
        }
        for (instance in orchestrator.registry.getAll()) {
            val worker = workerManager.get(instance.info.id) ?: continue
            if (instance.status == ProtocolStatus.RUNNING || instance.status == ProtocolStatus.CONFIGURED) {
                val config = instance.config.toMap()
                worker.start(config).onSuccess {
                    orchestrator.registry.updateStatus(instance.info.id, ProtocolStatus.RUNNING)
                }
            }
        }
        onLog("[ProtocolWorkerOrchestrator] All workers started")
    }

    suspend fun stopWorkers() {
        onLog("[ProtocolWorkerOrchestrator] Stopping all workers...")
        for (instance in orchestrator.registry.getAll()) {
            val worker = workerManager.get(instance.info.id) ?: continue
            worker.stop().onSuccess {
                orchestrator.registry.updateStatus(instance.info.id, ProtocolStatus.INSTALLED)
            }
        }
        onLog("[ProtocolWorkerOrchestrator] All workers stopped")
    }

    fun getDetailedReport(): String = buildString {
        appendLine("=== ProtocolWorkers Detailed Report ===")
        appendLine()

        for ((id, status) in workerManager.getStatusMap().entries.sortedBy { it.key }) {
            appendLine("  Worker: $id")
            appendLine("    Status: $status")
            val metrics = workerManager.getMetricsMap()[id] ?: emptyMap()
            for ((key, value) in metrics) {
                appendLine("    $key: $value")
            }
            appendLine()
        }

        appendLine("-- Registry Sync --")
        for (instance in orchestrator.registry.getAll()) {
            val marker = if (workerManager.get(instance.info.id) != null) " [managed]" else ""
            appendLine("  ${instance.info.id}: ${instance.status}$marker")
        }

        appendLine()
        val phase = orchestrator.phase.value
        appendLine("Orchestrator Phase: $phase")
        appendLine("Orchestrator Started: ${orchestrator.isStarted}")
    }
}