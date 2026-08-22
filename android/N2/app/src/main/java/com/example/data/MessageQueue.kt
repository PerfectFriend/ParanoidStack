/**
 * Очередь исходящих сообщений с поддержкой повторных попыток отправки.
 * Сообщения буферизируются и отправляются асинхронно через SMP-агента.
 * При неудачной отправке выполняются повторные попытки (до 3 раз).
 */
package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Асинхронная очередь исходящих сообщений.
 *
 * @param context контекст приложения
 * @param agent SMP-агент для отправки
 * @param scope корутина-скоуп для фоновой отправки
 */
class MessageQueue(
    private val context: Context,
    private val agent: SMPAgent?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val tag = "MessageQueue"
    private val pendingOutgoing = ConcurrentLinkedQueue<OutgoingMessage>()
    private var syncJob: Job? = null

    /** Сообщение, ожидающее отправки */
    data class OutgoingMessage(
        val id: String,                                    // уникальный ID
        val contactId: String,                             // ID контакта-получателя
        val text: String,                                  // текст сообщения
        val timestamp: Long = System.currentTimeMillis(),  // время постановки в очередь
        val retryCount: Int = 0,                           // текущее количество попыток
        val maxRetries: Int = 3                            // максимальное количество попыток
    )

    /**
     * Добавить сообщение в очередь и запустить отправку.
     * @param contactId ID контакта
     * @param text текст сообщения
     * @return ID сообщения в очереди
     */
    fun enqueue(contactId: String, text: String): String {
        val msgId = "q_${System.nanoTime()}"
        pendingOutgoing.add(OutgoingMessage(id = msgId, contactId = contactId, text = text))
        startSync()
        return msgId
    }

    /** Запустить фоновую отправку очереди */
    fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (pendingOutgoing.isNotEmpty()) {
                processQueue()
                delay(2000)  // пауза 2 секунды между попытками
            }
        }
    }

    /** Остановить фоновую отправку */
    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Обработать очередь: отправить до 10 сообщений за раз.
     * В случае неудачи — повторная попытка с увеличением счётчика.
     */
    suspend fun processQueue() {
        val batch = mutableListOf<OutgoingMessage>()
        // Забираем до 10 сообщений из очереди
        while (batch.size < 10 && pendingOutgoing.isNotEmpty()) {
            pendingOutgoing.poll()?.let { batch.add(it) }
        }

        for (msg in batch) {
            val sent = agent?.sendMessage(msg.contactId, msg.text) ?: false
            if (!sent) {
                if (msg.retryCount < msg.maxRetries) {
                    // Возвращаем в очередь с увеличенным счётчиком
                    pendingOutgoing.add(msg.copy(retryCount = msg.retryCount + 1))
                    Log.w(tag, "Retry [${msg.retryCount + 1}/${msg.maxRetries}] msg=${msg.id}")
                } else {
                    Log.e(tag, "Failed to send msg=${msg.id} after ${msg.maxRetries} retries")
                }
            } else {
                Log.i(tag, "Sent msg=${msg.id} to ${msg.contactId}")
            }
        }
    }

    /** Количество сообщений, ожидающих отправки */
    val pendingCount: Int get() = pendingOutgoing.size

    /** Очистить очередь */
    fun clear() {
        pendingOutgoing.clear()
    }
}
