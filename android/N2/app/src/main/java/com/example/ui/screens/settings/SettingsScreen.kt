package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SettingsSection {
    NETWORK, PRIVACY, NOTIFICATIONS, PROTOCOL, DATA_USAGE, SNOOZE, DEBUG, ABOUT, NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSection: SettingsSection = SettingsSection.NONE,
    onNavigateToNetwork: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProtocol: () -> Unit = {},
    onNavigateToDataUsage: () -> Unit = {},
    onNavigateToSnooze: () -> Unit = {},
    onNavigateToDebugPanel: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SettingsItem("Network", "Tor, V2Ray, SOCKS5, proxy chain", onClick = onNavigateToNetwork)
            HorizontalDivider()
            SettingsItem("Protocol", "Node orchestration, mesh, cloud", onClick = onNavigateToProtocol)
            HorizontalDivider()
            SettingsItem("Privacy", "Screen, clipboard, PIN, duress", onClick = onNavigateToPrivacy)
            HorizontalDivider()
            SettingsItem("Notifications", "Channels, sounds, quiet hours", onClick = onNavigateToNotifications)
            HorizontalDivider()
            SettingsItem("Data Usage", "Bandwidth, cache, auto-download", onClick = onNavigateToDataUsage)
            HorizontalDivider()
            SettingsItem("Snooze", "Do not disturb schedule", onClick = onNavigateToSnooze)
            HorizontalDivider()
            SettingsItem("Debug", "Logs, diagnostics, network test", onClick = onNavigateToDebugPanel)
            HorizontalDivider()
            SettingsItem("About", "Version, licenses, privacy policy", onClick = onNavigateToAbout)
        }
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.fillMaxWidth(),
        trailingContent = { Text("\u203A") }
    )
}
