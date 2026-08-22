/**
 * Планировщик задач для управления истечением срока сообщений.
 *
 * Предоставляет методы для запуска периодической проверки
 * просроченных сообщений (каждые 15 минут) и для планирования
 * удаления конкретного сообщения через заданный промежуток времени.
 * Использует WorkManager для гарантированного выполнения задач
 * даже после перезапуска приложения.
 */
package com.example.service

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Планировщик задач очистки сообщений.
 */
object MessageScheduler {
    /** Уникальное имя периодической задачи */
    private const val PERIODIC_WORK_NAME = "message_expiry_periodic"
    /** Префикс для тегов одноразовых задач */
    private const val ONE_TIME_WORK_PREFIX = "message_expiry_onetime_"

    /**
     * Запускает периодическую проверку просроченных сообщений.
     *
     * Задача выполняется каждые 15 минут (с гибким интервалом 5 минут)
     * без требований к сети. Если задача уже существует, новая не создаётся.
     *
     * @param context Контекст приложения.
     */
    /** Enqueues a periodic expiry check every 15 minutes (existing task is preserved) */
    fun scheduleExpiryCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<MessageExpiryWorker>(
            15, TimeUnit.MINUTES,  // Интервал выполнения
            5, TimeUnit.MINUTES    // Гибкий интервал (flex-период)
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Сеть не требуется
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Не заменять существующую задачу
            request
        )
    }

    /**
     * Отменяет периодическую проверку просроченных сообщений.
     *
     * @param context Контекст приложения.
     */
    /** Cancels the periodic expiry check if one is scheduled */
    fun cancelExpiryCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /**
     * Планирует удаление конкретного сообщения через указанную задержку.
     *
     * Используется для установки срока жизни отдельного сообщения.
     * После истечения задержки сообщение с указанным ID будет удалено.
     *
     * @param context  Контекст приложения.
     * @param messageId ID сообщения для удаления.
     * @param delayMs  Задержка в миллисекундах перед удалением.
     */
    /** Schedules a one-time deletion for the given message after the specified delay */
    fun scheduleMessageExpiry(context: Context, messageId: Long, delayMs: Long) {
        val inputData = workDataOf("messageId" to messageId)
        val request = OneTimeWorkRequestBuilder<MessageExpiryWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("$ONE_TIME_WORK_PREFIX$messageId")
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
