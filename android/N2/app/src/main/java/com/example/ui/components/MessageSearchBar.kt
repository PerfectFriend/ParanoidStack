package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Панель поиска сообщений с полем ввода, кнопками очистки и закрытия.
 *
 * @param onSearch вызывается при каждом изменении текста запроса
 * @param onClose вызывается при закрытии панели
 * @param modifier модификатор компонента
 */
@Composable
fun MessageSearchBar(
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Иконка поиска слева
            Icon(Icons.Default.Search, contentDescription = null)
            
            // Поле ввода запроса с плейсхолдером на русском
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                placeholder = { Text("Поиск сообщений…") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            
            // Кнопка очистки запроса (показывается, если есть текст)
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    query = ""
                    onSearch("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Очистить")
                }
            }
            
            // Кнопка полного закрытия панели поиска
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть поиск")
            }
        }
    }
}
