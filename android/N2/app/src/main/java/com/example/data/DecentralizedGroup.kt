/**
 * Модели данных для децентрализованных групп SimpleX.
 * Группы не имеют центрального сервера — каждый участник хранит
 * полное состояние группы и распространяет сообщения через SMP.
 */
package com.example.data

/** Участник децентрализованной группы */
data class GroupMember(
    val memberId: String,         // уникальный ID участника
    val displayName: String,      // отображаемое имя
    val smpQueueUri: String,      // URI очереди SMP для связи
    val publicKeyB64: String,     // публичный ключ в Base64
    val role: MemberRole          // роль в группе
)

/** Роль участника в группе: администратор или обычный участник */
enum class MemberRole { ADMIN, MEMBER }

/** Состояние децентрализованной группы */
data class DecentralizedGroupState(
    val groupId: String,                    // ID группы
    val groupName: String,                  // название группы
    val members: List<GroupMember>,         // список участников
    val messageHistory: List<GroupMessage>  // история сообщений
)

/** Сообщение в децентрализованной группе */
data class GroupMessage(
    val messageId: String,   // ID сообщения
    val senderId: String,    // ID отправителя
    val text: String,        // текст сообщения
    val timestamp: Long      // временная метка
)
