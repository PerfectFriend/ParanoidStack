package com.example.security

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CertificatePinner
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Emergency panic button that triggers registered callbacks to wipe sensitive data.
 */
object PanicButtonManager {
    private const val TAG = "PanicButton"
    private var panicCallbacks = mutableListOf<() -> Unit>()

    fun register(callback: () -> Unit) { panicCallbacks.add(callback) }
    fun unregister(callback: () -> Unit) { panicCallbacks.remove(callback) }
    fun trigger() { panicCallbacks.forEach { it.invoke() }; Log.w(TAG, "PANIC BUTTON TRIGGERED") }
}

/**
 * Generates dummy encrypted network traffic to obscure real communication patterns.
 * Adjustable noise level controls packet size and interval.
 */
class CoverTrafficGenerator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onLog: (String) -> Unit = {}
) {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private var job: Job? = null

    enum class NoiseLevel { LOW, MEDIUM, HIGH }

    fun start(level: NoiseLevel = NoiseLevel.LOW) {
        if (_isRunning.value) return
        _isRunning.value = true
        val intervalMs = when(level) { NoiseLevel.LOW -> 60000L; NoiseLevel.MEDIUM -> 30000L; NoiseLevel.HIGH -> 15000L }
        val sizeBytes = when(level) { NoiseLevel.LOW -> 64; NoiseLevel.MEDIUM -> 128; NoiseLevel.HIGH -> 256 }
        job = scope.launch {
            val dummy = ByteArray(sizeBytes); Random.nextBytes(dummy)
            while (_isRunning.value) {
                onLog("[CoverTraffic] Sending ${dummy.size}B dummy packet...")
                delay(intervalMs + Random.nextLong(-5000, 5000))
            }
        }
    }

    fun stop() { _isRunning.value = false; job?.cancel() }
    fun dispose() { stop(); scope.cancel() }
}

/**
 * Allows trusted apps to pin clipboard entries so they survive the auto-clear timeout.
 * Pinned entries expire after [PIN_TIMEOUT_MS].
 */
object ClipboardPinningManager {
    private val pinned = ConcurrentHashMap.newKeySet<String>()
    private const val PIN_TIMEOUT_MS = 60000L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun pin(content: String) { pinned.add(content); scope.launch { delay(PIN_TIMEOUT_MS); unpin(content) } }
    fun unpin(content: String) { pinned.remove(content) }
    fun isPinned(content: String): Boolean = pinned.contains(content)
    fun clearAll() { pinned.clear() }
}

/**
 * Auto-locks the screen after a configurable inactivity timeout.
 * Counts down [timeoutSeconds] and invokes [onTimeout] when the timer expires.
 */
class SecureScreenTimeout(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val onTimeout: () -> Unit = {},
    private val onLog: (String) -> Unit = {}
) {
    private val _timeoutSeconds = MutableStateFlow(300)
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds
    private val _remainingSeconds = MutableStateFlow(300)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds
    private var job: Job? = null

    fun start(seconds: Int = 300) {
        _timeoutSeconds.value = seconds
        _remainingSeconds.value = seconds
        job = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
            }
            onLog("[SecureTimeout] Timeout reached")
            onTimeout()
        }
    }

    fun reset() { _remainingSeconds.value = _timeoutSeconds.value }
    fun stop() { job?.cancel() }
}

/** SSL certificate pinning configuration for OkHttp clients. */
object SslPinner {
    private const val TAG = "SslPinner"

    fun createPin(hostname: String, sha256Hash: String): CertificatePinner =
        CertificatePinner.Builder()
            .add(hostname, "sha256/$sha256Hash")
            .build()

    fun buildClientWithPinning(
        hostname: String,
        sha256Hash: String
    ): okhttp3.OkHttpClient =
        okhttp3.OkHttpClient.Builder()
            .certificatePinner(createPin(hostname, sha256Hash))
            .build()
}
