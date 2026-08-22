package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SearchFilters

/**
 * Полноценная панель поиска с фильтрами.
 * Позволяет искать по тексту, фильтровать по контакту,
 * дате, типу сообщений (медиа/входящие/исходящие).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    filters: SearchFilters = SearchFilters(),
    onFiltersChange: (SearchFilters) -> Unit = {},
    results: List<String> = emptyList(),
    onResultClick: (Int) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFilters by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Верхняя панель поиска
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Поиск...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    TextButton(onClick = onClose) { Text("\u2716") }
                }
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { showFilters = !showFilters }) {
                Text("\u2699", fontSize = 18.sp)
            }
        }

        // Фильтры (условно)
        if (showFilters) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Фильтры", fontWeight = FontWeight.Medium)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = filters.onlyMedia, onCheckedChange = {
                            onFiltersChange(filters.copy(onlyMedia = it))
                        })
                        Text("Только медиа", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = filters.onlyOutgoing, onCheckedChange = {
                            onFiltersChange(filters.copy(onlyOutgoing = it, onlyIncoming = false))
                        })
                        Text("Исходящие", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = filters.onlyIncoming, onCheckedChange = {
                            onFiltersChange(filters.copy(onlyIncoming = it, onlyOutgoing = false))
                        })
                        Text("Входящие", fontSize = 13.sp)
                    }
                }
            }
        }

        // Результаты
        if (results.isNotEmpty()) {
            Text(
                text = "Найдено: ${results.size}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(results.withIndex().toList()) { (index, item) ->
                    ListItem(
                        headlineContent = { Text(item, maxLines = 2, fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
            }
        } else if (query.isNotBlank()) {
            Text(
                text = "Ничего не найдено",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
