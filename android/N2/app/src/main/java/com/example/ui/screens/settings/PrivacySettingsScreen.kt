/**
 * Экран настроек приватности: блокировка экрана, защита буфера обмена и Duress PIN.
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
fun PrivacySettingsScreen(
    // Параметры для трёх функций безопасности
    screenSecurityEnabled: Boolean = false,
    clipboardGuardEnabled: Boolean = false,
    duressPinEnabled: Boolean = false,
    onToggleScreenSecurity: (Boolean) -> Unit = {},
    onToggleClipboardGuard: (Boolean) -> Unit = {},
    onToggleDuressPin: (Boolean) -> Unit = {},
    onSetDuressPin: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приватность") },
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
            Text("Защита экрана", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            PrivacyToggle("Блокировка экрана", "Скрывать контент в списке последних приложений",
                screenSecurityEnabled, onToggleScreenSecurity)
            HorizontalDivider()
            PrivacyToggle("Защита буфера обмена", "Очищать буфер через 30 секунд",
                clipboardGuardEnabled, onToggleClipboardGuard)
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Duress PIN", style = MaterialTheme.typography.bodyLarge)
                    Text("Экстренный PIN для сброса", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = duressPinEnabled, onCheckedChange = onToggleDuressPin)
            }

            if (duressPinEnabled) {
                Button(
                    onClick = onSetDuressPin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Установить Duress PIN")
                }
            }
        }
    }
}

/** Строка переключателя приватности с заголовком и описанием. */
@Composable
private fun PrivacyToggle(
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
