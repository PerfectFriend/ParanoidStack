/**
 * Room-сущность для хранения зашифрованных сообщений в локальной БД.
 * Соответствует таблице `secure_messages`.
 * Содержит метаданные сообщения (контакт, статус, срок жизни) и
 * зашифрованный текст сообщения.
 */
package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Сущность защищённого сообщения в Room-базе данных.
 *
 * @property id автоинкрементный ID
 * @property contactId ID контакта (собеседника)
 * @property messageText зашифрованный или открытый текст (ByteArray)
 * @property timestamp временная метка
 * @property isOutgoing исходящее сообщение (true) или входящее (false)
 * @property isRead прочитано ли сообщение
 * @property expiresAt срок жизни (null = неограниченно)
 * @property isSending в процессе отправки
 * @property isFailed ошибка отправки
 * @property filePath путь к прикреплённому файлу
 * @property mimeType MIME-тип прикреплённого файла
 */
@Entity(
    tableName = "secure_messages",
    indices = [
        Index(value = ["contactId", "timestamp"]),
        Index(value = ["isRead"]),
        Index(value = ["isDeleted"])
    ]
)
data class SecureMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: String,
    val messageText: ByteArray,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val isRead: Boolean = false,
    val expiresAt: Long? = null,
    val isSending: Boolean = false,
    val isFailed: Boolean = false,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val filePath: String? = null,
    val mimeType: String? = null
) {
    val text: String get() = messageText.decodeToString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureMessageEntity) return false
        return id == other.id &&
            contactId == other.contactId &&
            messageText.contentEquals(other.messageText) &&
            timestamp == other.timestamp &&
            isOutgoing == other.isOutgoing &&
            isRead == other.isRead &&
            expiresAt == other.expiresAt &&
            isSending == other.isSending &&
            isFailed == other.isFailed &&
            isEdited == other.isEdited &&
            isDeleted == other.isDeleted &&
            isPinned == other.isPinned &&
            filePath == other.filePath &&
            mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + contactId.hashCode()
        result = 31 * result + messageText.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isOutgoing.hashCode()
        result = 31 * result + isRead.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        result = 31 * result + isSending.hashCode()
        result = 31 * result + isFailed.hashCode()
        result = 31 * result + isEdited.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + (filePath?.hashCode() ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}
