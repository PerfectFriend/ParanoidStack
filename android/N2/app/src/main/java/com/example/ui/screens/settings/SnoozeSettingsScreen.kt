package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.service.QuietHoursManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSettingsScreen(
    manager: QuietHoursManager? = null,
    onBack: () -> Unit = {}
) {
    val config = remember { manager?.getConfig() ?: QuietHoursManager.QuietHoursConfig() }
    var enabled by remember { mutableStateOf(config.enabled) }
    var startHour by remember { mutableIntStateOf(config.startHour) }
    var startMinute by remember { mutableIntStateOf(config.startMinute) }
    var endHour by remember { mutableIntStateOf(config.endHour) }
    var endMinute by remember { mutableIntStateOf(config.endMinute) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Тихие часы") },
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Включить тихие часы", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    manager?.setConfig(config.copy(enabled = it))
                })
            }

            if (enabled) {
                Spacer(Modifier.height(24.dp))
                Text("Время начала", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timePicker(startHour, 0..23) { h ->
                        startHour = h
                        manager?.setConfig(config.copy(startHour = h))
                    }
                    Text(":", modifier = Modifier.padding(top = 8.dp))
                    timePicker(startMinute, 0..59) { m ->
                        startMinute = m
                        manager?.setConfig(config.copy(startMinute = m))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Время окончания", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timePicker(endHour, 0..23) { h ->
                        endHour = h
                        manager?.setConfig(config.copy(endHour = h))
                    }
                    Text(":", modifier = Modifier.padding(top = 8.dp))
                    timePicker(endMinute, 0..59) { m ->
                        endMinute = m
                        manager?.setConfig(config.copy(endMinute = m))
                    }
                }
            }
        }
    }
}

@Composable
private fun timePicker(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(String.format("%02d", value))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { v ->
                DropdownMenuItem(
                    text = { Text(String.format("%02d", v)) },
                    onClick = { onChange(v); expanded = false }
                )
            }
        }
    }
}
