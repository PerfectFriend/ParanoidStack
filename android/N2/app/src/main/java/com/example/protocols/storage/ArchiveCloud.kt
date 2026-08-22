package com.example.protocols.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random

data class StoredBlob(
    val hash: String,
    val size: Long,
    val pins: Int = 1,
    val chunks: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

data class CloudConfig(
    val storagePath: String = "archive_data",
    val maxSpaceBytes: Long = 10L * 1024 * 1024 * 1024,
    val replicationFactor: Int = 3,
    val port: Int = 9001,
    val publicHost: String = "",
    val enablePinning: Boolean = true
)

class ArchiveCloud(
    private val context: Context,
    private val config: CloudConfig = CloudConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private val storageDir: File get() = File(context.filesDir, config.storagePath).also { it.mkdirs() }
    private val blobsDir: File get() = File(storageDir, "blobs").also { it.mkdirs() }
    private val pinsDir: File get() = File(storageDir, "pins").also { it.mkdirs() }
    private val manifestFile: File get() = File(storageDir, "manifest.json")

    val manifestStore = ManifestStore(storageDir)
    val blobGC = BlobGC(storageDir, manifestStore, scope = scope, onLog = onLog)
    val pinningService = PinningService(manifestStore, onLog)

    private val localBlobs = ConcurrentHashMap<String, StoredBlob>()
    private val knownPeers = ConcurrentHashMap<String, PeerInfo>()
    private val _stats = MutableStateFlow(CloudStats(0, 0, 0, 0, 0))
    val stats: StateFlow<CloudStats> = _stats.asStateFlow()

    private var dhtServer: ServerSocket? = null
    private var running = false

    data class CloudStats(
        val totalBlobs: Long, val totalSizeBytes: Long,
        val usedSpaceBytes: Long, val peerCount: Int,
        val pinnedCount: Int = 0
    )
    data class PeerInfo(val host: String, val port: Int, val lastSeen: Long)

    /// Запустить облачное хранилище (DHT + HTTP API)
    fun start() {
        if (running) return
        running = true
        loadManifest()
        onLog("[ArchiveCloud] Starting on port ${config.port}...")
        scope.launch(Dispatchers.IO) { dhtListener() }
        scope.launch(Dispatchers.IO) { httpApiListener() }
        scope.launch { periodicGc() }
        blobGC.start()
        onLog("[ArchiveCloud] Started. Storage: ${storageDir.absolutePath}")
    }

    fun stop() {
        running = false
        dhtServer?.close(); dhtServer = null
        saveManifest()
        blobGC.stop()
        onLog("[ArchiveCloud] Stopped")
    }

    /// Сохранить файл в облако (content-addressed)
    suspend fun store(data: ByteArray, metadata: Map<String, String> = emptyMap()): String {
        val hash = sha256(data)
        val chunkSize = 512 * 1024 // 512KB chunks
        val chunks = mutableListOf<String>()

        if (data.size <= chunkSize) {
            val chunkHash = storeChunk(hash, data)
            chunks.add(chunkHash)
        } else {
            data.toList().chunked(chunkSize).forEachIndexed { i, chunk ->
                val chunkHash = storeChunk("${hash}_$i", chunk.toByteArray())
                chunks.add(chunkHash)
            }
        }

        val blob = StoredBlob(hash, data.size.toLong(), 1, chunks, metadata)
        localBlobs[hash] = blob
        saveManifest()

        // Реплицировать на пиров
        if (knownPeers.isNotEmpty()) {
            scope.launch { replicateToPeers(hash, data) }
        }

        _stats.value = computeStats()
        onLog("[ArchiveCloud] Stored: $hash (${data.size} bytes, ${chunks.size} chunks)")
        return hash
    }

    /// Загрузить файл из облака (по хешу)
    suspend fun retrieve(hash: String): ByteArray? {
        // Сначала проверить локально
        localBlobs[hash]?.let { blob ->
            val parts = blob.chunks.map { chunkHash ->
                val chunkFile = File(blobsDir, chunkHash)
                if (chunkFile.exists()) chunkFile.readBytes() else return@let null
            }.takeIf { it.none { b -> b == null } } ?: return@let null

            @Suppress("UNCHECKED_CAST")
            val validParts = (parts as? List<*>)?.filterIsInstance<ByteArray>() ?: return@let null
            return validParts.reduce { a, b -> a + b }
        }

        // Поиск у пиров
        for ((id, peer) in knownPeers) {
            try {
                val data = fetchFromPeer(peer, hash)
                if (data != null) return data
            } catch (_: Exception) { continue }
        }
        return null
    }

    /// Прикрепить файл к своему хранилищу (pinning)
    fun pin(hash: String) {
        localBlobs[hash]?.let {
            val newBlob = it.copy(pins = it.pins + 1)
            localBlobs[hash] = newBlob
            File(pinsDir, hash).writeText(hash)
            saveManifest()
            onLog("[ArchiveCloud] Pinned: $hash")
        }
    }

    fun unpin(hash: String) {
        localBlobs[hash]?.let {
            val newPins = it.pins - 1
            if (newPins <= 0) {
                localBlobs.remove(hash)
                it.chunks.forEach { chunk -> File(blobsDir, chunk).delete() }
                File(pinsDir, hash).delete()
            } else {
                localBlobs[hash] = it.copy(pins = newPins)
            }
            saveManifest()
            onLog("[ArchiveCloud] Unpinned: $hash")
        }
    }

    // ===== PEER MANAGEMENT =====

    fun discoverPeers(bootstrapNodes: List<String>) {
        for (node in bootstrapNodes) {
            try {
                val parts = node.split(":")
                val peer = PeerInfo(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: config.port, 0L)
                knownPeers[node] = peer
                // Попробовать получить их список пиров
                val peerList = requestPeerList(peer)
                for (p in peerList) {
                    val parts2 = p.split(":")
                    if (parts2.size >= 2) {
                        knownPeers[p] = PeerInfo(parts2[0], parts2[1].toIntOrNull() ?: config.port, 0L)
                    }
                }
            } catch (_: Exception) { continue }
        }
        onLog("[ArchiveCloud] Discovered ${knownPeers.size} peers")
    }

    fun getPeerList(): List<String> = knownPeers.map { "${it.value.host}:${it.value.port}" }

    // ===== INTERNAL =====

    private fun storeChunk(name: String, data: ByteArray): String {
        val hash = sha256(data)
        val file = File(blobsDir, hash)
        if (!file.exists()) file.writeBytes(data)
        return hash
    }

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun loadManifest() {
        if (!manifestFile.exists()) return
        try {
            val json = org.json.JSONObject(manifestFile.readText())
            val blobs = json.optJSONObject("blobs")
            if (blobs != null) {
                for (key in blobs.keys()) {
                    val b = blobs.getJSONObject(key)
                    val chunks = b.optJSONArray("chunks")
                    val chunkList = if (chunks != null) (0 until chunks.length()).map { chunks.getString(it) } else emptyList()
                    localBlobs[key] = StoredBlob(key, b.optLong("size"), b.optInt("pins", 1), chunkList)
                }
            }
        } catch (e: Exception) { Log.w("ArchiveCloud", "Manifest parse error", e) }
    }

    private fun saveManifest() {
        try {
            val blobs = org.json.JSONObject()
            localBlobs.forEach { (k, v) ->
                val jb = org.json.JSONObject().apply {
                    put("size", v.size); put("pins", v.pins)
                    put("chunks", org.json.JSONArray(v.chunks))
                }
                blobs.put(k, jb)
            }
            manifestFile.writeText(org.json.JSONObject().apply { put("blobs", blobs) }.toString(2))
        } catch (e: Exception) { Log.w("ArchiveCloud", "Manifest write error", e) }
    }

    private suspend fun dhtListener() {
        try {
            dhtServer = ServerSocket(config.port)
            dhtServer?.soTimeout = 5000
            while (running) {
                try {
                    val client = dhtServer?.accept() ?: continue
                    scope.launch(Dispatchers.IO) { handleDhtRequest(client) }
                } catch (_: java.net.SocketTimeoutException) { continue }
            }
        } catch (e: Exception) { onLog("[ArchiveCloud] DHT error: ${e.message}") }
    }

    private suspend fun handleDhtRequest(client: Socket) {
        try {
            client.soTimeout = 10000
            val rdr = client.inputStream.bufferedReader()
            val cmd = rdr.readLine() ?: return
            when {
                cmd.startsWith("PING") -> client.getOutputStream().write("PONG\n".toByteArray())
                cmd.startsWith("GET_PEERS") -> {
                    val peers = knownPeers.keys.joinToString(",")
                    client.getOutputStream().write("PEERS $peers\n".toByteArray())
                }
                cmd.startsWith("HAVE") -> {
                    val hash = cmd.removePrefix("HAVE ").trim()
                    val exists = localBlobs.containsKey(hash)
                    client.getOutputStream().write(if (exists) "YES\n".toByteArray() else "NO\n".toByteArray())
                }
                cmd.startsWith("GET_BLOB") -> {
                    val hash = cmd.removePrefix("GET_BLOB ").trim()
                    val data = retrieve(hash)
                    if (data != null) {
                        client.getOutputStream().write("BLOB ${data.size}\n".toByteArray())
                        client.getOutputStream().write(data)
                    } else {
                        client.getOutputStream().write("NOT_FOUND\n".toByteArray())
                    }
                }
            }
        } catch (_: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception") } finally { try { client.close() } catch (_: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception") } }
    }

    private suspend fun httpApiListener() {
        try {
            val apiServer = ServerSocket(config.port + 1)
            apiServer.soTimeout = 5000
            while (running) {
                try {
                    val client = apiServer.accept()
                    scope.launch(Dispatchers.IO) { handleHttpRequest(client) }
                } catch (_: java.net.SocketTimeoutException) { continue }
            }
        } catch (e: Exception) { onLog("[ArchiveCloud] API error: ${e.message}") }
    }

    private suspend fun handleHttpRequest(client: Socket) {
        try {
            client.soTimeout = 10000
            val rdr = client.inputStream.bufferedReader()
            val req = rdr.readLine() ?: return
            val (method, path) = req.split(" ").let { it[0] to it[1] }
            val resp = client.getOutputStream()

            when {
                method == "GET" && path == "/stats" -> {
                    val s = computeStats()
                    val json = org.json.JSONObject().apply {
                        put("totalBlobs", s.totalBlobs)
                        put("totalSizeBytes", s.totalSizeBytes)
                        put("usedSpaceBytes", s.usedSpaceBytes)
                        put("peerCount", s.peerCount)
                        put("version", "ArchiveCloud/1.0")
                    }
                    respondJson(resp, json.toString())
                }
                method == "GET" && path.startsWith("/blob/") -> {
                    val hash = path.removePrefix("/blob/")
                    val data = retrieve(hash)
                    if (data != null) respondData(resp, data, "application/octet-stream")
                    else respondError(resp, 404, "Not Found")
                }
                method == "PUT" && path.startsWith("/blob/") -> {
                    val hash = path.removePrefix("/blob/")
                    val data = rdr.readLine()?.toByteArray() ?: ByteArray(0)
                    store(data, mapOf("uploaded" to System.currentTimeMillis().toString()))
                    respondJson(resp, """{"hash":"$hash","status":"stored"}""")
                }
                else -> respondError(resp, 404, "Not Found")
            }
        } catch (_: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception") } finally { try { client.close() } catch (_: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception") } }
    }

    private fun respondJson(out: OutputStream, json: String) {
        val bytes = json.toByteArray()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private fun respondData(out: OutputStream, data: ByteArray, mime: String) {
        out.write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${data.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(data); out.flush()
    }

    private fun respondError(out: OutputStream, code: Int, msg: String) {
        val body = """{"error":"$msg"}"""
        val bytes = body.toByteArray()
        out.write("HTTP/1.1 $code $msg\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }

    private suspend fun replicateToPeers(hash: String, data: ByteArray) {
        val targets = knownPeers.entries.shuffled().take(config.replicationFactor)
        for ((_, peer) in targets) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(peer.host, peer.port), 5000)
                sock.getOutputStream().write("STOR $hash ${data.size}\n".toByteArray())
                sock.getOutputStream().write(data)
                sock.close()
            } catch (_: Exception) { continue }
        }
    }

    private suspend fun fetchFromPeer(peer: PeerInfo, hash: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(peer.host, peer.port), 5000)
                sock.soTimeout = 30000
                sock.getOutputStream().write("GET_BLOB $hash\n".toByteArray()); sock.getOutputStream().flush()
                val rdr = sock.inputStream.bufferedReader()
                val header = rdr.readLine() ?: return@withContext null
                if (header.startsWith("BLOB ")) {
                    val size = header.removePrefix("BLOB ").toIntOrNull() ?: return@withContext null
                    val data = ByteArray(size)
                    var off = 0
                    while (off < size) { val n = sock.inputStream.read(data, off, size - off); if (n < 0) break; off += n }
                    sock.close()
                    data
                } else { sock.close(); null }
            } catch (_: Exception) { null }
        }

    private fun requestPeerList(peer: PeerInfo): List<String> = try {
        val sock = Socket()
        sock.connect(InetSocketAddress(peer.host, peer.port), 3000)
        sock.getOutputStream().write("GET_PEERS\n".toByteArray()); sock.getOutputStream().flush()
        val resp = sock.inputStream.bufferedReader().readLine() ?: return emptyList()
        sock.close()
        resp.removePrefix("PEERS ").split(",").filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    private fun computeStats(): CloudStats {
        val blobs = localBlobs.size.toLong()
        val size = localBlobs.values.sumOf { it.size }
        val used = storageDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return CloudStats(blobs, size, used, knownPeers.size, pinningService.pinCount())
    }

    private suspend fun periodicGc() {
        while (running) {
            delay(60_000)
            val quota = config.maxSpaceBytes
            val used = storageDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (used > quota) {
                onLog("[ArchiveCloud] GC: ${used / 1_000_000}MB > ${quota / 1_000_000}MB quota")
                // Remove unpinned old blobs
                val pinned = pinsDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
                val toRemove = localBlobs.filter { it.key !in pinned }
                    .entries.sortedBy { it.value.pins }
                    .take((localBlobs.size * 0.2).toInt().coerceAtLeast(1))
                for ((hash, _) in toRemove) unpin(hash)
            }
            _stats.value = computeStats()
        }
    }
}

// ============================================================
// New classes for enhanced ArchiveCloud functionality
// ============================================================

data class BlobManifest(
    val hash: String,
    val sizeBytes: Long,
    val contentType: String = "application/octet-stream",
    val createdAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val replicationFactor: Int = 3,
    val peers: MutableSet<String> = mutableSetOf(),
    val originalName: String = "",
    val description: String = ""
)

class ManifestStore(private val storagePath: File) {
    private val lock = Any()
    private val manifests = ConcurrentHashMap<String, BlobManifest>()
    private val manifestFile = File(storagePath, "_manifests.json")

    init { if (manifestFile.exists()) load() }

    fun put(manifest: BlobManifest) { synchronized(lock) { manifests[manifest.hash] = manifest; save() } }
    fun get(hash: String): BlobManifest? = manifests[hash]
    fun getAll(): List<BlobManifest> = manifests.values.toList()
    fun remove(hash: String) { synchronized(lock) { manifests.remove(hash); save() } }
    fun size(): Int = manifests.size
    fun totalSize(): Long = manifests.values.sumOf { it.sizeBytes }

    private fun save() { try { manifestFile.writeText(JsonHelper.toJson(manifests.values.toList())) } catch (e: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception", e) } }
    private fun load() { try { val json = manifestFile.readText(); val list: List<BlobManifest> = JsonHelper.fromJson(json); list.forEach { manifests[it.hash] = it } } catch (e: java.lang.Exception) { Log.w("ArchiveCloud", "ignored exception", e) } }
}

object JsonHelper {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    fun toJson(obj: Any): String = moshi.adapter(Any::class.java).toJson(obj)
    fun fromJson(json: String): List<BlobManifest> {
        val type = Types.newParameterizedType(List::class.java, BlobManifest::class.java)
        val adapter = moshi.adapter<List<BlobManifest>>(type)
        return adapter.fromJson(json) ?: emptyList()
    }
}

class BlobGC(
    private val storageDir: File,
    private val manifestStore: ManifestStore,
    private val ttlHours: Long = 72,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private var job: Job? = null
    private val _lastRun = MutableStateFlow(0L)
    val lastRun: StateFlow<Long> = _lastRun

    fun start(intervalMinutes: Long = 60) {
        job = scope.launch {
            while (true) {
                delay(intervalMinutes * 60 * 1000)
                runGC()
            }
        }
        onLog("[BlobGC] Started (interval=${intervalMinutes}min, ttl=${ttlHours}h)")
    }

    fun runGC() {
        val now = System.currentTimeMillis()
        val cutoff = now - (ttlHours * 3600 * 1000)
        var removed = 0

        val blobs = storageDir.listFiles() ?: return
        for (file in blobs) {
            if (file.name.startsWith("_")) continue
            val hash = file.name.removeSuffix(".blob")
            val manifest = manifestStore.get(hash)
            if (manifest != null && !manifest.pinned && manifest.createdAt < cutoff) {
                file.delete()
                manifestStore.remove(hash)
                removed++
            }
        }
        _lastRun.value = now
        onLog("[BlobGC] Removed $removed expired blobs")
    }

    fun stop() { job?.cancel() }
    fun dispose() { stop(); scope.cancel() }
}

class PinningService(
    private val manifestStore: ManifestStore,
    private val onLog: (String) -> Unit = {}
) {
    fun pin(hash: String): Boolean {
        val m = manifestStore.get(hash) ?: return false
        manifestStore.put(m.copy(pinned = true))
        onLog("[Pinning] Pinned: $hash")
        return true
    }

    fun unpin(hash: String): Boolean {
        val m = manifestStore.get(hash) ?: return false
        manifestStore.put(m.copy(pinned = false))
        onLog("[Pinning] Unpinned: $hash")
        return true
    }

    fun isPinned(hash: String): Boolean = manifestStore.get(hash)?.pinned ?: false
    fun listPinned(): List<BlobManifest> = manifestStore.getAll().filter { it.pinned }
    fun pinCount(): Int = listPinned().size
}
