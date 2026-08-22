/**
 * Пакет безопасности — защита буфера обмена.
 * Обеспечивает автоматическую очистку скопированных данных через заданный интервал.
 */
package com.example.security

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Охранник буфера обмена.
 * Хранит скопированные данные в защищённой памяти с тайм-аутом и автоматически
 * очищает их по истечении срока давности.
 */
object ClipboardGuard {
    /** Хранилище записей: ключ -> (значение, время истечения). */
    private val secureClipboard = mutableMapOf<String, Pair<String, Long>>()
    /** Время жизни записи в буфере (60 секунд). */
    private const val EXPIRY_MS = 60_000L
    /** Job для фоновой очистки. */
    private var autoClearJob: Job? = null
    private var scope: CoroutineScope? = null

    /**
     * Копирует значение в защищённый буфер с временем истечения.
     * @param key ключ записи.
     * @param value значение для хранения.
     */
    /** Stores a value with a TTL of [EXPIRY_MS] milliseconds */
    fun copyToSecureClipboard(key: String, value: String) {
        synchronized(secureClipboard) {
            secureClipboard[key] = Pair(value, System.currentTimeMillis() + EXPIRY_MS)
        }
    }

    /**
     * Извлекает значение из защищённого буфера (однократное чтение с удалением).
     * @param key ключ записи.
     * @return значение или null, если запись не найдена или истекла.
     */
    /** Retrieves and removes a value; returns null if expired or not found (single-read) */
    fun pasteFromSecureClipboard(key: String): String? {
        synchronized(secureClipboard) {
            val entry = secureClipboard[key] ?: return null
            if (System.currentTimeMillis() > entry.second) {
                secureClipboard.remove(key)
                return null
            }
            secureClipboard.remove(key)
            return entry.first
        }
    }

    /** Очищает весь защищённый буфер. */
    fun clearClipboard() {
        synchronized(secureClipboard) {
            secureClipboard.clear()
        }
    }

    /**
     * Запускает фоновый воркер для автоматической очистки просроченных записей.
     * Проверяет каждые 30 секунд.
     * @param context контекст приложения.
     */
    /** Launches a background coroutine that purges expired entries every 30 seconds */
    fun startAutoClearWorker(context: Context) {
        stopAutoClearWorker()
        scope = CoroutineScope(Dispatchers.Default)
        autoClearJob = scope?.launch {
            while (true) {
                delay(30_000L)
                synchronized(secureClipboard) {
                    val now = System.currentTimeMillis()
                    // Remove all entries whose expiry time has passed
                    secureClipboard.entries.removeAll { it.value.second <= now }
                }
            }
        }
    }

    /** Останавливает фоновый воркер очистки. */
    fun stopAutoClearWorker() {
        autoClearJob?.cancel()
        autoClearJob = null
        scope = null
    }
}
