package com.example.data

import android.util.Log

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LazyListPrefetchOptimizer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val prefetchCache = ConcurrentHashMap<String, Any>()
    private val _cacheSize = MutableStateFlow(0)
    val cacheSize: StateFlow<Int> = _cacheSize

    fun prefetch(key: String, data: Any) { prefetchCache[key] = data; _cacheSize.value = prefetchCache.size }
    fun get(key: String): Any? = prefetchCache[key]
    fun evict(key: String) { prefetchCache.remove(key); _cacheSize.value = prefetchCache.size }
    fun clear() { prefetchCache.clear(); _cacheSize.value = 0 }
}

class ImageCacheManager(
    private val context: Context,
    private val maxMemoryBytes: Long = Runtime.getRuntime().maxMemory() / 8,
    private val maxDiskBytes: Long = 50 * 1024 * 1024 // 50MB
) {
    private val memCache = LinkedHashMap<String, Bitmap>(0, 0.75f, true)
    private var memSize: Long = 0
    private val cacheDir = File(context.cacheDir, "image_cache")
    private val lock = Any()
    private val _memCacheSize = MutableStateFlow(0L)
    val memCacheSize: StateFlow<Long> = _memCacheSize
    private val _diskCacheSize = MutableStateFlow(0L)
    val diskCacheSize: StateFlow<Long> = _diskCacheSize

    init { cacheDir.mkdirs(); calculateDiskSize() }

    fun put(key: String, bitmap: Bitmap) { synchronized(lock) { memSize += bitmap.allocationByteCount; memCache[key] = bitmap; trimMemory() }; _memCacheSize.value = memSize }
    fun get(key: String): Bitmap? = synchronized(lock) { memCache[key] }
    fun putDisk(key: String, data: ByteArray) { try { File(cacheDir, key.sha256()).writeBytes(data); calculateDiskSize(); trimDisk() } catch (e: java.lang.Exception) { Log.w("PerformanceOptimizer", "ignored exception", e) } }
    fun getDisk(key: String): ByteArray? = try { File(cacheDir, key.sha256()).readBytes() } catch (e: Exception) { null }
    fun clear() { synchronized(lock) { memCache.clear(); memSize = 0 }; cacheDir.deleteRecursively(); cacheDir.mkdirs(); _memCacheSize.value = 0; _diskCacheSize.value = 0 }

    private fun trimMemory() { while (memSize > maxMemoryBytes && memCache.isNotEmpty()) { val entry = memCache.entries.first(); memSize -= entry.value.allocationByteCount; memCache.remove(entry.key) } }
    private fun trimDisk() { if (diskCacheSize() > maxDiskBytes) { cacheDir.listFiles()?.sortedBy { it.lastModified() }?.firstOrNull()?.delete(); calculateDiskSize() } }
    private fun calculateDiskSize() { _diskCacheSize.value = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    private fun diskCacheSize(): Long = _diskCacheSize.value
    private fun String.sha256(): String = java.security.MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
}

class MemoryProfiler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private val _heapUsage = MutableStateFlow(0L)
    val heapUsage: StateFlow<Long> = _heapUsage
    private val _nativeHeap = MutableStateFlow(0L)
    val nativeHeap: StateFlow<Long> = _nativeHeap
    private val _heapMax = MutableStateFlow(0L)
    val heapMax: StateFlow<Long> = _heapMax
    private val _usagePercent = MutableStateFlow(0f)
    val usagePercent: StateFlow<Float> = _usagePercent
    private var job: Job? = null

    fun start(intervalMs: Long = 5000) {
        job = scope.launch {
            while (true) {
                val rt = Runtime.getRuntime()
                val used = rt.totalMemory() - rt.freeMemory()
                val max = rt.maxMemory()
                _heapUsage.value = used
                _heapMax.value = max
                _usagePercent.value = if (max > 0) used.toFloat() / max.toFloat() * 100f else 0f
                _nativeHeap.value = used
                onLog("[MemProfiler] Heap: ${used / 1024 / 1024}MB / ${max / 1024 / 1024}MB (${"%.1f".format(_usagePercent.value)}%)")
                delay(intervalMs)
            }
        }
    }

    fun stop() { job?.cancel() }
    fun snapshot(): Triple<Long, Long, Float> = Triple(_heapUsage.value, _heapMax.value, _usagePercent.value)
}

class BatteryAwareScheduler(private val context: Context) {
    enum class BatteryLevel { CRITICAL, LOW, NORMAL, HIGH }

    fun getBatteryLevel(): BatteryLevel {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        return when { pct <= 10 -> BatteryLevel.CRITICAL; pct <= 25 -> BatteryLevel.LOW; pct >= 80 -> BatteryLevel.HIGH; else -> BatteryLevel.NORMAL }
    }

    fun shouldDefer(): Boolean = getBatteryLevel() == BatteryLevel.CRITICAL
    fun shouldReduceWork(): Boolean = getBatteryLevel() <= BatteryLevel.LOW
}

class PerformanceOptimizer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    val prefetchOptimizer = LazyListPrefetchOptimizer(scope)
    val imageCache = ImageCacheManager(context)
    val memoryProfiler = MemoryProfiler(scope, onLog)
    val batteryScheduler = BatteryAwareScheduler(context)
    val rateLimiter = RateLimiter()

    fun startMonitoring() { memoryProfiler.start() }
    fun stopMonitoring() { memoryProfiler.stop() }

    fun getReport(): String = buildString {
        appendLine("=== Performance Report ===")
        val (heap, max, pct) = memoryProfiler.snapshot()
        appendLine("Heap: ${heap / 1024 / 1024}MB / ${max / 1024 / 1024}MB ($pct%)")
        appendLine("Image Cache: mem=${imageCache.memCacheSize.value / 1024}KB disk=${imageCache.diskCacheSize.value / 1024 / 1024}MB")
        appendLine("Battery: ${batteryScheduler.getBatteryLevel()}")
        appendLine("Rate Limiter: ${rateLimiter.perMessageCostMs}ms/msg")
    }

    fun dispose() { stopMonitoring(); scope.cancel() }
}
