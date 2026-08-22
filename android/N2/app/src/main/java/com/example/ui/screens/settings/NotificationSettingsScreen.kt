package com.example.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class NotificationChannelItem(
    val id: String,
    val name: String,
    val description: String,
    var enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    channels: List<NotificationChannelItem> = emptyList(),
    pushServiceEnabled: Boolean = true,
    onToggleChannel: (String, Boolean) -> Unit = { _, _ -> },
    onTogglePushService: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var localPushEnabled by remember { mutableStateOf(pushServiceEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
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
            Text("Push Service", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Foreground push service", style = MaterialTheme.typography.bodyLarge)
                    Text("WebSocket connection for real-time notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = localPushEnabled, onCheckedChange = {
                    localPushEnabled = it
                    onTogglePushService(it)
                })
            }

            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Notification Channels", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (channels.isEmpty()) {
                Text("No channels available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                channels.forEach { channel ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.bodyLarge)
                            Text(channel.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = channel.enabled,
                            onCheckedChange = { onToggleChannel(channel.id, it) })
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
