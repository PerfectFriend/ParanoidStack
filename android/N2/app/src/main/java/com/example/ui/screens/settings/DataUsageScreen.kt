/**
 * Экран статистики использования данных в настройках приложения.
 * Отображает объём полученных/отправленных данных и текущую скорость соединения.
 */
package com.example.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(
    totalReceived: Long = 0,
    totalSent: Long = 0,
    currentSpeedDown: Double = 0.0,
    currentSpeedUp: Double = 0.0,
    onResetStats: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    // Карточки с общей статистикой и текущей скоростью
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Использование данных") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = onResetStats) { Text("Сброс") }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Всего", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    DataRow("Получено", formatBytes(totalReceived))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DataRow("Отправлено", formatBytes(totalSent))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DataRow("Всего", formatBytes(totalReceived + totalSent),
                        isTotal = true)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Текущая скорость", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    DataRow("Загрузка", "${formatSpeed(currentSpeedDown)}/с")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DataRow("Отдача", "${formatSpeed(currentSpeedUp)}/с")
                }
            }
        }
    }
}

/** Строка с данными: метка и значение (для итога — жирный шрифт). */
@Composable
private fun DataRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text(
            value,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

/** Форматирует байты в человекочитаемый вид: B, KB, MB, GB. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

/** Форматирует скорость из байт/с в B/с, KB/с, MB/с. */
private fun formatSpeed(bytesPerSec: Double): String = when {
    bytesPerSec < 1024 -> "%.0f B".format(bytesPerSec)
    bytesPerSec < 1024 * 1024 -> "%.1f KB".format(bytesPerSec / 1024)
    else -> "%.1f MB".format(bytesPerSec / (1024 * 1024))
}
