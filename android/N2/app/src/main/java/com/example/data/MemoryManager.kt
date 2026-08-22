/**
 * Управление памятью приложения и кэшем.
 * Предоставляет утилиты для отслеживания использования памяти
 * и очистки кэша при нехватке ресурсов.
 */
package com.example.data

import android.content.Context
import java.io.File

/** Объект-синглтон для мониторинга и очистки памяти */
object MemoryManager {
    private const val LOW_MEMORY_THRESHOLD_MB = 50L

    /**
     * Получить объём используемой памяти в МБ.
     * @return используемая память (МБ)
     */
    fun getUsedMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    /**
     * Получить объём доступной памяти в МБ.
     * @return доступная память (МБ)
     */
    fun getAvailableMemoryMB(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
    }

    /** Проверить, не достигнут ли низкий порог памяти (< 50 МБ) */
    fun isLowMemory(): Boolean {
        return getAvailableMemoryMB() < LOW_MEMORY_THRESHOLD_MB
    }

    /** Очистить кэш-директорию приложения */
    fun trimCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Получить размер кэш-директории в байтах */
    fun getCacheSize(context: Context): Long {
        return getDirSize(context.cacheDir)
    }

    /** Рекурсивно подсчитать размер директории */
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
