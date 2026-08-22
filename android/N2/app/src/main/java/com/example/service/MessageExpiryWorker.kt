/**
 * Worker для удаления сообщений с истёкшим сроком жизни.
 *
 * Поддерживает два режима:
 * 1) Удаление конкретного сообщения по ID (разовое задание).
 * 2) Массовое удаление всех сообщений, срок хранения которых истёк
 *    относительно текущего времени (периодическое задание).
 */
package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase

/**
 * Worker для очистки просроченных сообщений в базе данных.
 *
 * @param context Контекст приложения.
 * @param params  Параметры задачи WorkManager.
 */
class MessageExpiryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /**
     * Выполняет удаление сообщений с истёкшим сроком.
     *
     * Если во входных данных передан messageId, удаляется
     * только указанное сообщение. Иначе удаляются все
     * сообщения, срок которых меньше текущего времени.
     *
     * @return Result.success() после завершения.
     */
    /** Deletes expired messages: by specific ID if provided, or all expired messages otherwise */
    override suspend fun doWork(): Result {
        // Получаем ID сообщения из входных данных (если есть)
        val messageId = inputData.getLong("messageId", -1L)
        val dao = AppDatabase.getDatabase(applicationContext).secureMessageDao()

        if (messageId != -1L) {
            // Удаляем одно конкретное сообщение
            dao.deleteMessageById(messageId)
        } else {
            // Удаляем все сообщения, у которых срок истёк
            dao.deleteExpiredMessages(System.currentTimeMillis())
        }

        return Result.success()
    }
}
