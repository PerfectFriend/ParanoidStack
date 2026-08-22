/**
 * Репозиторий сообщений — прослойка между UI и Room-базой данных.
 * Предоставляет удобные методы для CRUD-операций с сообщениями
 * и трансляции между [ChatMessageInfo] и [SecureMessageEntity].
 */
package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.SecureRandom

/** Репозиторий для работы с сообщениями через SecureMessageDao */
class MessageRepository(private val context: Context) {

    private val dao get() = AppDatabase.getDatabase(context).secureMessageDao()

    /** Информация о сообщении в чате (более высокоуровневая модель) */
    data class ChatMessageInfo(
        val id: Long = 0,                // ID в БД
        val contactId: String,            // ID контакта
        val text: String,                 // текст
        val timestamp: Long,              // время
        val isOutgoing: Boolean,          // исходящее?
        val isRead: Boolean = false,      // прочитано?
        val isDeleted: Boolean = false,   // удалено?
        val expiresAt: Long? = null,      // срок жизни
        val isSending: Boolean = false,   // отправляется?
        val isFailed: Boolean = false,    // ошибка отправки?
        val filePath: String? = null,     // путь к файлу
        val mimeType: String? = null      // MIME-тип
    )

    /** Получить поток сообщений для контакта */
    fun getMessagesForContact(contactId: String): Flow<List<SecureMessageEntity>> {
        return dao.getMessagesForContact(contactId)
    }

    /** Сохранить новое сообщение */
    suspend fun saveMessage(info: ChatMessageInfo) {
        val entity = SecureMessageEntity(
            contactId = info.contactId,
            messageText = info.text.encodeToByteArray(),
            timestamp = info.timestamp,
            isOutgoing = info.isOutgoing,
            isRead = info.isRead,
            expiresAt = info.expiresAt
        )
        dao.insertMessage(entity)
    }

    /** Отметить как прочитанное */
    suspend fun markAsRead(messageId: Long) = dao.markAsRead(messageId)

    /** Отметить как ошибочное */
    suspend fun markAsFailed(messageId: Long) = dao.markAsFailed(messageId)

    /** Удалить сообщение по ID */
    suspend fun deleteMessage(messageId: Long) = dao.deleteMessageById(messageId)

    /** Редактировать текст сообщения */
    suspend fun editMessage(messageId: Long, newText: String) {
        dao.markAsEdited(messageId)
        dao.updateMessageText(messageId, newText.encodeToByteArray())
        val db = AppDatabase.getDatabase(context)
        val editsDao = db.messageEditDao()
        val currentCount = editsDao.getEditHistory(messageId).size
        editsDao.insert(MessageEditEntity(
            originalMessageId = messageId,
            newText = newText,
            editNumber = currentCount + 1
        ))
    }

    /** Получить историю редактирования сообщения */
    suspend fun getEditHistory(messageId: Long): List<MessageEditEntity> {
        return AppDatabase.getDatabase(context).messageEditDao().getEditHistory(messageId)
    }

    /** Удалить все сообщения для контакта */
    suspend fun deleteAllForContact(contactId: String) = dao.deleteAllForContact(contactId)

    /** Получить количество непрочитанных сообщений */
    suspend fun getUnreadCount(contactId: String): Int = dao.getUnreadCount(contactId).first()

    /** Удалить сообщения с истёкшим сроком */
    suspend fun deleteExpired() = dao.deleteExpiredMessages(System.currentTimeMillis())

    /** Получить список всех контактов */
    suspend fun getAllContacts(): List<String> = dao.getAllContacts()

}
