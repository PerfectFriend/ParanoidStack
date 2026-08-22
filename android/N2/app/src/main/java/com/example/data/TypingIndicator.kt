/**
 * Индикатор набора текста (typing indicator).
 * Отслеживает, какие контакты в данный момент набирают сообщение.
 * Автоматически снимает статус "печатает" через 5 секунд бездействия.
 */
package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Индикатор печатания для контактов.
 * @param scope корутина-скоуп для таймеров
 */
class TypingIndicator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val tag = "TypingIndicator"
    private val typingTimers = ConcurrentHashMap<String, Job>()  // таймеры для каждого контакта
    private val _typingContacts = mutableSetOf<String>()

    /** Множество контактов, которые сейчас печатают */
    val typingContacts: Set<String> get() = _typingContacts.toSet()

    /**
     * Вызвать при начале набора текста контактом.
     * Запускает таймер на 5 сек; если контакт не перестал печатать — статус снимется.
     * @param contactId ID контакта
     * @param onNotify callback уведомления
     */
    fun onTypingStarted(contactId: String, onNotify: (String) -> Unit) {
        _typingContacts.add(contactId)
        onNotify(contactId)

        // Сбрасываем предыдущий таймер и ставим новый
        typingTimers[contactId]?.cancel()
        typingTimers[contactId] = scope.launch {
            delay(5000)
            _typingContacts.remove(contactId)
            onNotify(contactId)
        }
    }

    /** Вызвать при прекращении набора текста контактом */
    fun onTypingStopped(contactId: String, onNotify: (String) -> Unit) {
        _typingContacts.remove(contactId)
        typingTimers[contactId]?.cancel()
        typingTimers.remove(contactId)
        onNotify(contactId)
    }

    /** Сбросить все статусы печатания */
    fun clear() {
        typingTimers.values.forEach { it.cancel() }
        typingTimers.clear()
        _typingContacts.clear()
    }
}
