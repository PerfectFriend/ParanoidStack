/**
 * Rate limiter for outbound messages (anti-spam).
 * Enforces both a global per-minute cap and a per-contact per-minute cap
 * to prevent message flooding. Uses thread-safe ConcurrentLinkedQueue for sliding-window counters.
 */
package com.example.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max

/**
 * Rate limiter for outbound messages (anti-spam).
 * Enforces a global per-minute limit and a per-contact per-minute limit.
 * Timestamps are stored in thread-safe ConcurrentLinkedQueue and pruned on each check.
 *
 * @param maxMessagesPerMinute Maximum global messages allowed in any 60-second window.
 * @param maxPerContactPerMinute Maximum messages per contact in any 60-second window.
 */
class RateLimiter(
    private val maxMessagesPerMinute: Int = 30,
    private val maxPerContactPerMinute: Int = 10
) {
    data class RateLimitConfig(
        val maxMessagesPerMinute: Int,
        val maxPerContactPerMinute: Int
    )

    private val globalTimestamps = ConcurrentLinkedQueue<Long>()
    private val contactTimestamps = ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>>()

    /** Prune timestamps older than cutoff from a ConcurrentLinkedQueue */
    private fun prune(queue: ConcurrentLinkedQueue<Long>, cutoff: Long) {
        while (true) {
            val oldest = queue.peek() ?: break
            if (oldest < cutoff) queue.poll() else break
        }
    }

    fun canSend(contactId: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000

        prune(globalTimestamps, oneMinuteAgo)
        if (globalTimestamps.size >= maxMessagesPerMinute) return false

        if (contactId != null) {
            val timestamps = contactTimestamps.getOrPut(contactId) { ConcurrentLinkedQueue() }
            prune(timestamps, oneMinuteAgo)
            if (timestamps.size >= maxPerContactPerMinute) return false
        }

        return true
    }

    fun recordSend(contactId: String? = null) {
        val now = System.currentTimeMillis()
        globalTimestamps.add(now)
        if (contactId != null) {
            val timestamps = contactTimestamps.getOrPut(contactId) { ConcurrentLinkedQueue() }
            timestamps.add(now)
        }
    }

    fun getRemainingGlobal(): Int {
        prune(globalTimestamps, System.currentTimeMillis() - 60_000)
        return max(0, maxMessagesPerMinute - globalTimestamps.size)
    }

    fun getRemainingForContact(contactId: String): Int {
        val timestamps = contactTimestamps[contactId] ?: return maxPerContactPerMinute
        prune(timestamps, System.currentTimeMillis() - 60_000)
        return max(0, maxPerContactPerMinute - timestamps.size)
    }

    val perMessageCostMs: Long get() = if (maxMessagesPerMinute > 0) 60_000L / maxMessagesPerMinute else 0L

    fun reset() {
        globalTimestamps.clear()
        contactTimestamps.clear()
    }
}