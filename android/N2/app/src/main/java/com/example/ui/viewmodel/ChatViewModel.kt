/**
 * Пакет ViewModel — модель представления для чата.
 *
 * ## Содержимое
 * - [ChatMessage] — модель сообщения чата (id, текст, направление, флаг удаления).
 * - [ChatUiState] — состояние UI чата (список сообщений, индикатор набора, ошибка).
 * - [ChatViewModel] — ViewModel, управляющая отправкой, получением и очисткой сообщений.
 */
package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Модель сообщения чата.
 * @property id уникальный идентификатор.
 * @property text текст сообщения.
 * @property isOutgoing true, если отправлено текущим пользователем.
 * @property timestamp время отправки.
 * @property isDeleted флаг удаления.
 */
data class ChatMessage(
    val id: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

/**
 * Состояние UI чата.
 * @property messages список сообщений.
 * @property isTyping индикатор набора текста собеседником.
 * @property error сообщение об ошибке.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel для экрана чата.
 * Управляет списком сообщений: отправка, получение, очистка.
 */
class ChatViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val messageCounter = AtomicInteger(0)

    /** Отправляет исходящее сообщение. */
    fun sendMessage(text: String) {
        val msg = ChatMessage(
            id = "msg_${messageCounter.getAndIncrement()}",
            text = text,
            isOutgoing = true
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + msg
        )
    }

    /** Добавляет входящее сообщение. */
    fun receiveMessage(text: String) {
        val msg = ChatMessage(
            id = "msg_${messageCounter.getAndIncrement()}",
            text = text,
            isOutgoing = false
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + msg
        )
    }

    /** Очищает всю историю сообщений. */
    fun clearMessages() {
        _uiState.value = ChatUiState()
    }
}
