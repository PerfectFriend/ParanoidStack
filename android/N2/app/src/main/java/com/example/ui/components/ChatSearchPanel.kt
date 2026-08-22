package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Результат поиска по сообщениям чата.
 *
 * @param messageId уникальный идентификатор сообщения
 * @param text текст найденного сообщения
 * @param senderName имя отправителя
 * @param timestamp временная метка сообщения
 * @param contactId идентификатор контакта-отправителя
 */
data class SearchResult(
    val messageId: Long,
    val text: String,
    val senderName: String,
    val timestamp: Long,
    val contactId: String
)

/**
 * Панель поиска сообщений в чате.
 * Содержит текстовое поле для ввода запроса, список результатов
 * и сообщение об отсутствии совпадений.
 *
 * @param results список найденных сообщений
 * @param query текущий поисковый запрос
 * @param onQueryChange вызывается при изменении текста запроса
 * @param onResultClick вызывается при нажатии на результат
 * @param onClose вызывается при закрытии панели
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSearchPanel(
    results: List<SearchResult>,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onResultClick: (SearchResult) -> Unit = {},
    onClose: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Поле ввода запроса с кнопкой закрытия
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск в чате...") },
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = onClose) { Text("\u2716") }
            },
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        )

        // Если есть результаты — выводим список
        if (results.isNotEmpty()) {
            Text(
                text = "Найдено: ${results.size}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Список результатов с ограничением по высоте
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(results) { result ->
                    ListItem(
                        headlineContent = {
                            Text(result.text, maxLines = 2, fontSize = 14.sp)
                        },
                        supportingContent = {
                            Text("${result.senderName} \u2022 ${formatTimestamp(result.timestamp)}",
                                fontSize = 11.sp)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
            }
        // Если запрос непустой, но результатов нет
        } else if (query.isNotBlank()) {
            Text(
                text = "Ничего не найдено",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Преобразует timestamp в человекочитаемый относительный формат ("только что", "5 мин. назад" и т.д.).
 */
private fun formatTimestamp(ts: Long): String {
    val seconds = (System.currentTimeMillis() - ts) / 1000
    return when {
        seconds < 60 -> "только что"
        seconds < 3600 -> "${seconds / 60} мин. назад"
        seconds < 86400 -> "${seconds / 3600} ч. назад"
        else -> "${seconds / 86400} дн. назад"
    }
}
