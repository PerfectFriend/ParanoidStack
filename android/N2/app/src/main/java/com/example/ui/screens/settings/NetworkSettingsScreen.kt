/**
 * Экран настроек сети: управление прокси-цепочкой Tor, V2Ray и SOCKS5.
 */
package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    // Параметры состояния для каждого прокси-сервиса
    torEnabled: Boolean = false,
    v2rayEnabled: Boolean = false,
    socks5Enabled: Boolean = false,
    onToggleTor: (Boolean) -> Unit = {},
    onToggleV2Ray: (Boolean) -> Unit = {},
    onToggleSocks5: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки сети") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Прокси-цепочка", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            NetworkToggle("Tor", "Анонимная маршрутизация", torEnabled, onToggleTor)
            HorizontalDivider()
            NetworkToggle("V2Ray", "Обход блокировок", v2rayEnabled, onToggleV2Ray)
            HorizontalDivider()
            NetworkToggle("SOCKS5", "Прокси-цепочка", socks5Enabled, onToggleSocks5)
        }
    }
}

/** Строка переключателя сети с заголовком и описанием. */
@Composable
private fun NetworkToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
