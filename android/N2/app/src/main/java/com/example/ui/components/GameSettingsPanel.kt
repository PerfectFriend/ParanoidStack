/**
 * Settings and network configuration composables.
 *
 * - [GameSettingsPanel] – toggles for dark mode, sound, bot opponent, auto-roll,
 *   AI difficulty slider, and visual theme selection.
 * - [GameNetworkPanel] – Tor / V2Ray connection status, proxy type selector,
 *   and connection test button.
 */
package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameSettingsState(
    val isDarkMode: Boolean = false,
    val isSoundEnabled: Boolean = true,
    val isBotOpponentEnabled: Boolean = false,
    val isAutoRollEnabled: Boolean = false,
    val themeId: String = "default",
    val aiDifficulty: Int = 1
)

@Composable
fun GameSettingsPanel(
    state: GameSettingsState,
    onDarkModeToggle: (Boolean) -> Unit = {},
    onSoundToggle: (Boolean) -> Unit = {},
    onBotToggle: (Boolean) -> Unit = {},
    onAutoRollToggle: (Boolean) -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    onAiDifficultyChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            SettingsRow("Тёмная тема", state.isDarkMode, onDarkModeToggle)
            SettingsRow("Звук", state.isSoundEnabled, onSoundToggle)
            SettingsRow("Игрок-бот", state.isBotOpponentEnabled, onBotToggle)
            SettingsRow("Авто-бросок", state.isAutoRollEnabled, onAutoRollToggle)
            Spacer(Modifier.height(8.dp))
            Text("Сложность ИИ: ${state.aiDifficulty}", fontSize = 12.sp)
            Slider(value = state.aiDifficulty.toFloat(), onValueChange = { onAiDifficultyChange(it.toInt()) }, valueRange = 1f..5f, steps = 3)
            Spacer(Modifier.height(8.dp))
            Text("Тема:", fontSize = 12.sp)
            val themes = listOf("default", "bw", "rainbow", "fire", "water", "cosmic", "hacker")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                themes.take(4).forEach { t ->
                    FilterChip(selected = state.themeId == t, onClick = { onThemeChange(t) }, label = { Text(t, fontSize = 9.sp) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                themes.drop(4).forEach { t ->
                    FilterChip(selected = state.themeId == t, onClick = { onThemeChange(t) }, label = { Text(t, fontSize = 9.sp) })
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
fun GameNetworkPanel(
    isOnline: Boolean = false,
    serverAddress: String = "",
    onConnect: (String) -> Unit = {},
    onDisconnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var address by remember { mutableStateOf(serverAddress) }
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Сеть", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (isOnline) {
                Text("Подключено: $serverAddress", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Отключиться") }
            } else {
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Адрес сервера") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConnect(address) }, enabled = address.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Подключиться") }
            }
        }
    }
}
