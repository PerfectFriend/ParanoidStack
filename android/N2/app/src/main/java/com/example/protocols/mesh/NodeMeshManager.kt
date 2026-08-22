package com.example.protocols.mesh

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random

data class NodeInfo(
    val nodeId: String,
    val host: String,
    val port: Int,
    val publicKey: String = "",
    val version: String = "N2/1.0",
    val protocols: List<String> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis(),
    val latencyMs: Int = 0,
    val isRelay: Boolean = false,
    val storageAvailable: Long = 0,
    val storageUsed: Long = 0
)

class NodeMeshManager(
    private val listenPort: Int = 9010,
    private val bootstrapNodes: List<String> = emptyList(),
    private val nodeId: String = generateNodeId(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private val _knownNodes = MutableStateFlow<List<NodeInfo>>(emptyList())
    val knownNodes: StateFlow<List<NodeInfo>> = _knownNodes.asStateFlow()

    private val _ourInfo = MutableStateFlow(NodeInfo(nodeId, "", 0))
    val ourInfo: StateFlow<NodeInfo> = _ourInfo.asStateFlow()

    private val _meshStats = MutableStateFlow(MeshStats(0, 0, 0))
    val meshStats: StateFlow<MeshStats> = _meshStats.asStateFlow()

    private val nodeMap = ConcurrentHashMap<String, NodeInfo>()
    val routingTable = RoutingTable(onLog)
    val rpcHandler = RpcHandler(routingTable, onLog)
    private var dhtSocket: ServerSocket? = null
    private var running = false

    data class MeshStats(val knownNodes: Int, val reachableNodes: Int, val messagesRelayed: Long, val bucketCount: Int = 0, val rpcStoreSize: Int = 0)

    companion object {
        fun generateNodeId(): String {
            val rand = ByteArray(32)
            java.security.SecureRandom().nextBytes(rand)
            return MessageDigest.getInstance("SHA-256").digest(rand)
                .joinToString("") { "%02x".format(it) }.take(16)
        }
    }

    /// Запустить mesh-сеть
    fun start(localHost: String = getLocalIp()) {
        if (running) return
        running = true

        _ourInfo.value = NodeInfo(
            nodeId = nodeId, host = localHost, port = listenPort,
            version = "N2/1.0", lastSeen = System.currentTimeMillis()
        )

        thread(name = "MeshDHTListener") { dhtListener() }
        onLog("[NodeMesh] Started on $localHost:$listenPort ID=$nodeId")

        // Bootstrap
        for (node in bootstrapNodes) {
            try {
                val parts = node.split(":")
                if (parts.size >= 2) {
                    discoverPeer(parts[0], parts[1].toIntOrNull() ?: listenPort)
                }
            } catch (_: java.lang.Exception) { Log.w("NodeMeshManager", "ignored exception") }
        }

        scope.launch { periodicDiscovery() }
        scope.launch { periodicStats() }
    }

    fun stop() {
        running = false
        dhtSocket?.close(); dhtSocket = null
        broadcastDeparture()
        onLog("[NodeMesh] Stopped")
    }

    /// Найти пира и получить его список узлов
    fun discoverPeer(host: String, port: Int): NodeInfo? {
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), 5000)
            sock.soTimeout = 5000
            val start = System.nanoTime()
            sock.getOutputStream().write("NODE_INFO\n".toByteArray()); sock.getOutputStream().flush()
            val rdr = sock.inputStream.bufferedReader()
            val infoLine = rdr.readLine() ?: return null
            sock.close()
            val latency = ((System.nanoTime() - start) / 1_000_000).toInt()
            val info = parseNodeInfo(infoLine, host, port, latency)
            if (info != null) {
                nodeMap[info.nodeId] = info
                _knownNodes.value = nodeMap.values.toList()
                onLog("[NodeMesh] Discovered: ${info.nodeId} @ $host:$port (${latency}ms)")
            }
            info
        } catch (e: Exception) {
            onLog("[NodeMesh] Discover failed: $host:$port - ${e.message}")
            null
        }
    }

    /// Отправить сообщение конкретному узлу
    fun sendDirect(nodeId: String, message: ByteArray): Boolean {
        val node = nodeMap[nodeId] ?: return false
        return try {
            val sock = Socket()
            sock.connect(InetSocketAddress(node.host, node.port), 5000)
            sock.soTimeout = 10000
            sock.getOutputStream().write("MSG ${message.size}\n".toByteArray())
            sock.getOutputStream().write(message)
            sock.getOutputStream().flush()
            val resp = sock.inputStream.bufferedReader().readLine()
            sock.close()
            resp == "OK"
        } catch (e: Exception) {
            onLog("[NodeMesh] Send to $nodeId failed: ${e.message}")
            nodeMap.remove(nodeId); _knownNodes.value = nodeMap.values.toList()
            false
        }
    }

    /// Широковещательный запрос по всей сети
    fun broadcast(message: ByteArray, ttl: Int = 3, exclude: Set<String> = emptySet()) {
        val targets = nodeMap.keys - exclude
        for (targetId in targets) {
            scope.launch {
                sendDirect(targetId, message)
            }
        }
    }

    /// Получить маршрут до узла
    fun findRoute(targetNodeId: String): List<NodeInfo> {
        if (nodeMap.containsKey(targetNodeId)) return listOf(nodeMap[targetNodeId] ?: return emptyList())
        // Простейший DHT: Kademlia-style iterative lookup
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(targetNodeId)
        while (queue.isNotEmpty() && visited.size < 20) {
            val current = queue.removeFirst()
            if (current in visited) continue
            visited.add(current)
            val node = nodeMap[current] ?: continue
            // Запросить у узла его ближайших соседей
            val neighbors = requestNeighbors(node)
            for (n in neighbors) {
                if (n == targetNodeId) return listOf(node)
                if (n !in visited) queue.add(n)
            }
        }
        return emptyList()
    }

    // ===== INTERNAL =====

    private fun dhtListener() {
        try {
            dhtSocket = ServerSocket(listenPort)
            dhtSocket?.soTimeout = 5000
            while (running) {
                try {
                    val client = dhtSocket?.accept() ?: continue
                    thread(name = "MeshDHT") { handleConnection(client) }
                } catch (_: java.net.SocketTimeoutException) { continue }
            }
        } catch (e: Exception) { onLog("[NodeMesh] Listener error: ${e.message}") }
    }

    private fun handleConnection(client: Socket) {
        try {
            client.soTimeout = 10000
            val rdr = client.inputStream.bufferedReader()
            val cmd = rdr.readLine() ?: return
            when {
                cmd == "NODE_INFO" -> respondNodeInfo(client)
                cmd.startsWith("MSG ") -> {
                    val size = cmd.removePrefix("MSG ").trim().toIntOrNull() ?: return
                    val msg = ByteArray(size); var off = 0
                    while (off < size) { val n = client.inputStream.read(msg, off, size - off); if (n < 0) break; off += n }
                    client.getOutputStream().write("OK\n".toByteArray()); client.getOutputStream().flush()
                    onLog("[NodeMesh] Relayed ${size}B message")
                }
                cmd == "GET_NEIGHBORS" -> {
                    val neighbors = nodeMap.keys.take(8).joinToString(",")
                    client.getOutputStream().write("NEIGHBORS $neighbors\n".toByteArray())
                }
                cmd.startsWith("FIND_NODE ") -> {
                    val target = cmd.removePrefix("FIND_NODE ").trim()
                    val closest = findClosestNodes(target)
                    client.getOutputStream().write("NODES ${closest.joinToString(",")}\n".toByteArray())
                }
                cmd == "DEPART" -> onLog("[NodeMesh] Peer departed") // handled by GC
            }
        } catch (_: java.lang.Exception) { Log.w("NodeMeshManager", "ignored exception") } finally { try { client.close() } catch (_: java.lang.Exception) { Log.w("NodeMeshManager", "ignored exception") } }
    }

    private fun respondNodeInfo(client: Socket) {
        val info = _ourInfo.value
        val line = "NODE:${info.nodeId}:${info.host}:${info.port}:${info.version}:${info.storageAvailable}:${info.storageUsed}"
        client.getOutputStream().write("$line\n".toByteArray())
    }

    private fun parseNodeInfo(line: String, host: String, port: Int, latency: Int): NodeInfo? {
        if (!line.startsWith("NODE:")) return null
        val parts = line.removePrefix("NODE:").split(":")
        if (parts.size < 4) return null
        return NodeInfo(
            nodeId = parts[0], host = parts[1], port = parts[2].toIntOrNull() ?: port,
            version = parts.getOrElse(3) { "unknown" },
            lastSeen = System.currentTimeMillis(), latencyMs = latency,
            storageAvailable = parts.getOrNull(4)?.toLongOrNull() ?: 0,
            storageUsed = parts.getOrNull(5)?.toLongOrNull() ?: 0
        )
    }

    private fun requestNeighbors(node: NodeInfo): List<String> = try {
        val sock = Socket()
        sock.connect(InetSocketAddress(node.host, node.port), 3000)
        sock.soTimeout = 3000
        sock.getOutputStream().write("GET_NEIGHBORS\n".toByteArray()); sock.getOutputStream().flush()
        val resp = sock.inputStream.bufferedReader().readLine() ?: return emptyList()
        sock.close()
        resp.removePrefix("NEIGHBORS ").split(",").filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    private fun findClosestNodes(targetId: String): List<String> {
        return nodeMap.keys
            .map { it to xorDistance(it, targetId) }
            .sortedBy { it.second }
            .take(8)
            .map { it.first }
    }

    private fun xorDistance(a: String, b: String): Long {
        val aBytes = a.hexToByteArray(); val bBytes = b.hexToByteArray()
        var dist = 0L
        for (i in 0 until minOf(aBytes.size, bBytes.size, 8)) {
            dist = (dist shl 8) or ((aBytes[i].toInt() xor bBytes[i].toInt()) and 0xFF).toLong()
        }
        return dist
    }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun broadcastDeparture() {
        broadcast("DEPART:${nodeId}".toByteArray(), ttl = 1)
    }

    private suspend fun periodicDiscovery() {
        while (running) {
            delay(30_000)
            for ((id, node) in nodeMap) {
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(node.host, node.port), 3000)
                    sock.getOutputStream().write("NODE_INFO\n".toByteArray())
                    val resp = sock.inputStream.bufferedReader().readLine()
                    sock.close()
                    if (resp != null) {
                        val updated = node.copy(lastSeen = System.currentTimeMillis())
                        nodeMap[id] = updated
                        _knownNodes.value = nodeMap.values.toList()
                    }
                } catch (_: Exception) {
                    nodeMap.remove(id)
                    _knownNodes.value = nodeMap.values.toList()
                    onLog("[NodeMesh] Lost: $id")
                }
            }
        }
    }

    private suspend fun periodicStats() {
        while (running) {
            delay(15_000)
            val reachable = nodeMap.count { (_, node) ->
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress(node.host, node.port), 2000)
                    sock.close(); true
                } catch (_: Exception) { false }
            }
            _meshStats.value = MeshStats(nodeMap.size, reachable, 0, routingTable.totalNodes(), rpcHandler.storeSize())
        }
    }

    private fun getLocalIp(): String = try {
        NetworkInterface.getNetworkInterfaces().asSequence().iterator().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "127.0.0.1"
    } catch (_: Exception) { "127.0.0.1" }

    fun dispose() { stop(); scope.cancel() }
}

// ===== Kademlia DHT Extensions =====

data class KBucket(
    val prefix: Int,
    val nodes: MutableList<NodeInfo> = mutableListOf(),
    val lastAccessTime: Long = System.currentTimeMillis(),
    val maxSize: Int = 20
) {
    fun addNode(node: NodeInfo): Boolean {
        if (nodes.size >= maxSize) return false
        if (nodes.none { it.nodeId == node.nodeId }) {
            nodes.add(node)
            return true
        }
        return false
    }

    fun removeNode(nodeId: String): Boolean = nodes.removeAll { it.nodeId == nodeId }

    fun contains(nodeId: String): Boolean = nodes.any { it.nodeId == nodeId }

    fun split(): Pair<KBucket, KBucket> {
        val first = KBucket(prefix * 2)
        val second = KBucket(prefix * 2 + 1)
        nodes.forEachIndexed { i, node ->
            if (i < nodes.size / 2) first.addNode(node) else second.addNode(node)
        }
        return Pair(first, second)
    }

    companion object {
        fun xorDistance(a: String, b: String): ByteArray {
            val aBytes = a.hexToByteArray()
            val bBytes = b.hexToByteArray()
            return aBytes.zip(bBytes) { x, y -> (x.toInt() xor y.toInt()).toByte() }.toByteArray()
        }

        private fun String.hexToByteArray(): ByteArray =
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

class RoutingTable(private val onLog: (String) -> Unit = {}) {
    private val buckets = Array(160) { KBucket(it) }
    private val lock = Any()

    fun findNode(nodeId: String): NodeInfo? {
        synchronized(lock) {
            val bucketIndex = xorLeadingBit(nodeId)
            return buckets[bucketIndex.coerceIn(0, 159)].nodes.find { it.nodeId == nodeId }
        }
    }

    fun addNode(node: NodeInfo): Boolean {
        synchronized(lock) {
            val bucketIndex = xorLeadingBit(node.nodeId).coerceIn(0, 159)
            val added = buckets[bucketIndex].addNode(node)
            if (added) onLog("[RoutingTable] Node ${node.nodeId.take(8)}... added to bucket $bucketIndex")
            return added
        }
    }

    fun removeNode(nodeId: String) {
        synchronized(lock) {
            val bucketIndex = xorLeadingBit(nodeId).coerceIn(0, 159)
            buckets[bucketIndex].removeNode(nodeId)
        }
    }

    fun findClosest(targetId: String, count: Int = 20): List<NodeInfo> {
        synchronized(lock) {
            val targetBucket = xorLeadingBit(targetId).coerceIn(0, 159)
            val range = (0..159).toList()
                .sortedBy { kotlin.math.abs(it - targetBucket) }

            val result = mutableListOf<NodeInfo>()
            for (idx in range) {
                result.addAll(buckets[idx].nodes)
                if (result.size >= count) break
            }
            return result.take(count)
        }
    }

    fun totalNodes(): Int = synchronized(lock) { buckets.sumOf { it.nodes.size } }

    fun allNodes(): List<NodeInfo> = synchronized(lock) { buckets.flatMap { it.nodes } }

    companion object {
        fun xorDistance(a: String, b: String): ByteArray {
            val aBytes = a.hexToByteArray()
            val bBytes = b.hexToByteArray()
            return aBytes.zip(bBytes) { x, y -> (x.toInt() xor y.toInt()).toByte() }.toByteArray()
        }

        fun xorLeadingBit(nodeId: String): Int {
            val bytes = nodeId.hexToByteArray()
            for ((i, byte) in bytes.withIndex()) {
                if (byte != 0.toByte()) {
                    for (j in 0..7) {
                        if ((byte.toInt() and (0x80 shr j)) != 0) return i * 8 + j
                    }
                }
            }
            return 0
        }

        fun xorKilobit(nodeId: String): Int {
            val bytes = nodeId.hexToByteArray()
            return if (bytes.isNotEmpty()) (bytes[0].toInt() and 0xFF) / 8 else 0
        }

        private fun String.hexToByteArray(): ByteArray {
            val len = length / 2
            return ByteArray(len) { Integer.parseInt(this[it * 2].toString() + this[it * 2 + 1], 16).toByte() }
        }
    }
}

sealed class RpcMessage {
    abstract val senderId: String

    data class Ping(override val senderId: String) : RpcMessage()
    data class Pong(override val senderId: String) : RpcMessage()
    data class FindNode(override val senderId: String, val targetId: String) : RpcMessage()
    data class FindNodeResponse(override val senderId: String, val nodes: List<NodeInfo>) : RpcMessage()
    data class Store(override val senderId: String, val key: String, val value: String) : RpcMessage()
    data class StoreResponse(override val senderId: String, val success: Boolean) : RpcMessage()
}

class RpcHandler(
    private val routingTable: RoutingTable,
    private val onLog: (String) -> Unit = {}
) {
    private val store = ConcurrentHashMap<String, String>()

    fun handle(message: RpcMessage): RpcMessage? = when (message) {
        is RpcMessage.Ping -> RpcMessage.Pong(message.senderId)
        is RpcMessage.FindNode -> RpcMessage.FindNodeResponse(message.senderId, routingTable.findClosest(message.targetId))
        is RpcMessage.Store -> {
            store[message.key] = message.value
            RpcMessage.StoreResponse(message.senderId, true)
        }
        is RpcMessage.Pong -> null
        is RpcMessage.FindNodeResponse -> null
        is RpcMessage.StoreResponse -> null
    }

    fun lookup(key: String): String? = store[key]
    fun storeSize(): Int = store.size
}
