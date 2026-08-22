/**
 * Монитор пропускной способности сети.
 * Собирает статистику отправленных/полученных байт,
 * вычисляет среднюю скорость за последние 10 секунд.
 */
package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Мониторинг сетевого трафика.
 * Ведёт учёт отправленных и полученных байт,
 * собирает семплы каждую секунду и вычисляет статистику.
 *
 * @param scope корутина-скоуп для фонового сбора семплов
 */
class BandwidthMonitor(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val tag = "BandwidthMonitor"
    private val bytesSent = AtomicLong(0)      // всего отправлено байт
    private val bytesReceived = AtomicLong(0)  // всего получено байт
    private val samples = ConcurrentLinkedQueue<Sample>()  // очередь семплов (макс 60)
    private var monitorJob: Job? = null

    /** Семпл трафика за одну секунду */
    data class Sample(
        val timestamp: Long,      // время семпла
        val sentDelta: Long,      // отправлено байт за последнюю секунду
        val receivedDelta: Long   // получено байт за последнюю секунду
    )

    /** Статистика пропускной способности */
    data class BandwidthStats(
        val totalSentMB: Double,          // всего отправлено, МБ
        val totalReceivedMB: Double,      // всего получено, МБ
        val uploadSpeedKbps: Double,      // скорость отправки, кбит/с
        val downloadSpeedKbps: Double,    // скорость приёма, кбит/с
        val sessionDurationSec: Long      // длительность сессии, сек
    )

    /** Записать отправленные байты (int) */
    fun recordSent(bytes: Int) {
        bytesSent.addAndGet(bytes.toLong())
    }

    /** Записать полученные байты (int) */
    fun recordReceived(bytes: Int) {
        bytesReceived.addAndGet(bytes.toLong())
    }

    /** Записать отправленные байты (long) */
    fun recordSentBytes(bytes: Long) {
        bytesSent.addAndGet(bytes)
    }

    /** Записать полученные байты (long) */
    fun recordReceivedBytes(bytes: Long) {
        bytesReceived.addAndGet(bytes)
    }

    /**
     * Запустить фоновый сбор семплов.
     * Каждую секунду вычисляет дельту и сохраняет в очередь.
     */
    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            var lastSent = bytesSent.get()
            var lastReceived = bytesReceived.get()

            while (isActive) {
                delay(1000)
                val currentSent = bytesSent.get()
                val currentReceived = bytesReceived.get()

                samples.add(Sample(
                    timestamp = System.currentTimeMillis(),
                    sentDelta = currentSent - lastSent,
                    receivedDelta = currentReceived - lastReceived
                ))

                // Храним не более 60 семплов (1 минута)
                if (samples.size > 60) samples.poll()

                lastSent = currentSent
                lastReceived = currentReceived
            }
        }
    }

    /** Остановить сбор семплов */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Получить текущую статистику.
     * Усреднение по последним 10 семплам (10 секунд).
     */
    fun getStats(): BandwidthStats {
        val totalSent = bytesSent.get()
        val totalReceived = bytesReceived.get()

        val recentSamples = samples.toList().takeLast(10)
        val avgUpload = if (recentSamples.isNotEmpty())
            recentSamples.map { it.sentDelta }.average() else 0.0
        val avgDownload = if (recentSamples.isNotEmpty())
            recentSamples.map { it.receivedDelta }.average() else 0.0

        return BandwidthStats(
            totalSentMB = totalSent / 1_000_000.0,
            totalReceivedMB = totalReceived / 1_000_000.0,
            uploadSpeedKbps = avgUpload / 1000.0 * 8,     // переводим байты в килобиты
            downloadSpeedKbps = avgDownload / 1000.0 * 8,
            sessionDurationSec = samples.size.toLong()     // приблизительная длительность
        )
    }

    /** Сбросить всю статистику */
    fun reset() {
        bytesSent.set(0)
        bytesReceived.set(0)
        samples.clear()
    }
}
