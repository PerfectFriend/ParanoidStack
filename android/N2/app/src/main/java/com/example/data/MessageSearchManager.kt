/**
 * Менеджер полнотекстового поиска сообщений.
 * Использует FTS4 (Full-Text Search) через MessageFtsDao
 * для быстрого поиска по тексту сообщений.
 */
package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Менеджер поиска по сообщениям */
class MessageSearchManager(private val ftsDao: MessageFtsDao) {

    /** Результат поиска */
    data class SearchResult(
        val messageId: Long,  // ID сообщения
        val snippet: String   // фрагмент текста
    )

    /**
     * Выполнить полнотекстовый поиск.
     * @param query поисковый запрос
     * @return Flow списка ID найденных сообщений
     */
    fun search(query: String): Flow<List<Long>> {
        if (query.isBlank()) return flowOf(emptyList())
        // Escape SQL special characters and FTS4 operators
        val sanitized = query
            .replace("'", "''")
            .replace("\"", "\"\"")
            .replace("*", "")
            .replace("AND", "and")
            .replace("OR", "or")
            .replace("NOT", "not")
        return ftsDao.searchMessages("\"$sanitized\"")  // quoted phrase to avoid FTS operators
    }
}
