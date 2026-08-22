/**
 * Network status and proxy management composable.
 *
 * Displays the connection status of Tor / V2Ray / SMP services and allows
 * the user to switch proxy type (Tor, V2Ray, SOCKS5, or chain).
 * Includes a connection test button.
 */
package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Сетевая панель, извлечённая из GameScreen.kt
 * Отображает статус Tor/V2Ray/SMP соединений.
 * Позволяет переключать прокси и проверять соединение.
 */
data class GameNetworkState(
    val torConnected: Boolean = false,
    val v2rayConnected: Boolean = false,
    val smpConnected: Boolean = false,
    val proxyType: String = "Tor"
)

@Composable
fun GameNetworkPanel(
    state: GameNetworkState,
    onToggleTor: () -> Unit = {},
    onToggleV2Ray: () -> Unit = {},
    onTestConnection: () -> Unit = {},
    onProxyChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Сеть", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text("✖") }
            }

            Spacer(Modifier.height(12.dp))

            NetRow("Tor", state.torConnected, onToggleTor)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            NetRow("V2Ray", state.v2rayConnected, onToggleV2Ray)

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Прокси", modifier = Modifier.weight(1f))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(state.proxyType)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Tor", "V2Ray", "SOCKS5", "Цепочка").forEach { proxy ->
                            DropdownMenuItem(
                                text = { Text(proxy) },
                                onClick = { onProxyChange(proxy); expanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Проверить соединение")
            }
        }
    }
}

@Composable
private fun NetRow(label: String, connected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            text = if (connected) "\u2705" else "\u274C",
            fontSize = 14.sp
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = connected, onCheckedChange = { onToggle() })
    }
}
