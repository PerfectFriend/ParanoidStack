package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ErrorRecoveryManager private constructor() {
    companion object {
        private const val TAG = "NexusChat/Recovery"
        private const val MAX_RETRIES = 5
        private const val BASE_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 60000L
        @Volatile private var instance: ErrorRecoveryManager? = null
        fun getInstance(): ErrorRecoveryManager =
            instance ?: synchronized(this) {
                instance ?: ErrorRecoveryManager().also { instance = it }
            }
    }

    enum class ServiceType { TOR, V2RAY, XRAY, SMP, SNOWFLAKE, CHAIN_PROXY, WIREGUARD, DNS }

    data class ServiceStatus(
        val type: ServiceType,
        var healthy: Boolean = true,
        var retryCount: Int = 0,
        var lastError: String = "",
        var lastRecovery: Long = 0,
        var consecutiveFailures: Int = 0
    )

    data class RecoveryResult(
        val success: Boolean,
        val serviceType: ServiceType,
        val attempts: Int,
        val delayMs: Long
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val services = ConcurrentHashMap<ServiceType, ServiceStatus>()
    private val recoveryJobs = ConcurrentHashMap<ServiceType, Job>()
    private var onRecoveryAction: ((ServiceType) -> Unit)? = null

    fun setRecoveryHandler(handler: (ServiceType) -> Unit) {
        onRecoveryAction = handler
    }

    fun registerService(type: ServiceType) {
        services.putIfAbsent(type, ServiceStatus(type = type))
        Log.d(TAG, "Service registered: $type")
    }

    @Synchronized
    fun reportFailure(type: ServiceType, error: String) {
        val status = services[type] ?: return
        status.healthy = false
        status.retryCount++
        status.consecutiveFailures++
        status.lastError = error
        Log.w(TAG, "${type.name} failure #${status.retryCount}: $error")
        if (status.retryCount <= MAX_RETRIES) {
            scheduleRecovery(type)
        } else {
            Log.e(TAG, "${type.name} exceeded max retries ($MAX_RETRIES)")
        }
    }

    @Synchronized
    fun reportSuccess(type: ServiceType) {
        val status = services[type] ?: return
        status.healthy = true
        status.consecutiveFailures = 0
        Log.d(TAG, "${type.name} healthy")
    }

    suspend fun attemptRecovery(type: ServiceType): RecoveryResult = withContext(Dispatchers.IO) {
        val status = services[type] ?: return@withContext RecoveryResult(false, type, 0, 0)
        var attempts = 0
        var delay = BASE_DELAY_MS

        while (attempts < MAX_RETRIES && !status.healthy) {
            attempts++
            val jitter = (rng.nextLong() and Long.MAX_VALUE) % delay
            delay(delay + jitter)
            try {
                Log.i(TAG, "Recovery attempt $attempts for ${type.name}")
                onRecoveryAction?.invoke(type)
                delay(2000)
                status.healthy = true
                status.retryCount = 0
                status.lastRecovery = System.currentTimeMillis()
                Log.i(TAG, "${type.name} recovered after $attempts attempts")
                return@withContext RecoveryResult(true, type, attempts, delay)
            } catch (e: Exception) {
                Log.w(TAG, "Recovery attempt $attempts failed: ${e.message}")
                delay = (delay * 2).coerceAtMost(MAX_DELAY_MS)
            }
        }
        RecoveryResult(false, type, attempts, delay)
    }

    private fun scheduleRecovery(type: ServiceType) {
        recoveryJobs[type]?.cancel()
        recoveryJobs[type] = scope.launch {
            try {
                val result = attemptRecovery(type)
                if (!result.success) {
                    Log.e(TAG, "${type.name} unrecoverable after ${result.attempts} attempts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recovery scheduler error for $type: ${e.message}")
            }
        }
    }

    fun getStatus(type: ServiceType): ServiceStatus? = services[type]

    fun getAllStatuses(): Map<ServiceType, ServiceStatus> = services.toMap()

    fun isHealthy(type: ServiceType): Boolean = services[type]?.healthy ?: false

    fun resetRetries(type: ServiceType) {
        services[type]?.let {
            it.retryCount = 0
            it.consecutiveFailures = 0
        }
    }

    fun destroy() {
        recoveryJobs.values.forEach { it.cancel() }
        recoveryJobs.clear()
        scope.cancel()
        instance = null
    }
}
