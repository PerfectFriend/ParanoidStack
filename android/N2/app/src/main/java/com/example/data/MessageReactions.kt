/**
 * Система реакций на сообщения (эмодзи).
 * Позволяет добавлять, удалять и получать реакции для каждого сообщения.
 * Реакции хранятся в памяти в ConcurrentHashMap.
 */
package com.example.data

import java.util.concurrent.ConcurrentHashMap

/** Реакция (эмодзи) на сообщение */
data class MessageReaction(
    val messageId: Long,  // ID сообщения
    val emoji: String,    // эмодзи (например, "👍")
    val senderId: String  // ID отправителя реакции
)

/** Менеджер реакций на сообщения */
class ReactionManager {
    private val reactions = ConcurrentHashMap<Long, MutableList<MessageReaction>>()

    /**
     * Добавить реакцию на сообщение.
     * @return true если реакция добавлена, false если уже существует
     */
    fun addReaction(reaction: MessageReaction): Boolean {
        val list = reactions.getOrPut(reaction.messageId) { mutableListOf() }
        val exists = list.any { it.senderId == reaction.senderId && it.emoji == reaction.emoji }
        if (exists) return false
        return list.add(reaction)
    }

    /**
     * Удалить реакцию с сообщения.
     * @return true если реакция удалена, false если не найдена
     */
    fun removeReaction(messageId: Long, senderId: String, emoji: String): Boolean {
        val list = reactions[messageId] ?: return false
        val removed = list.removeAll { it.senderId == senderId && it.emoji == emoji }
        if (list.isEmpty()) reactions.remove(messageId)  // удаляем пустой список
        return removed
    }

    /** Получить все реакции для сообщения */
    fun getReactionsForMessage(messageId: Long): List<MessageReaction> {
        return reactions[messageId]?.toList() ?: emptyList()
    }
}
