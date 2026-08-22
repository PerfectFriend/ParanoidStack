/**
 * Экран детальной информации о сообщении: отправитель, время, статус доставки, пересылка, файл.
 */
package com.example.ui.screens.messages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Статус доставки сообщения: отправляется, доставлено, прочитано, ошибка.
 */
enum class DeliveryStatus { SENDING, DELIVERED, READ, FAILED }

/**
 * Модель детальной информации о сообщении.
 */
data class MessageDetail(
    val text: String,
    val senderName: String,
    val timestamp: Long,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.DELIVERED,
    val isForwarded: Boolean = false,
    val fileInfo: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    message: MessageDetail? = null,
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация о сообщении") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(message?.text ?: "", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Отправитель", message?.senderName ?: "")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow("Время",
                        if (message != null) formatFullTimestamp(message.timestamp) else "")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow("Статус", getStatusText(message?.deliveryStatus))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DetailRow("Переслано", if (message?.isForwarded == true) "Да" else "Нет")
                    if (message?.fileInfo != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DetailRow("Файл", message.fileInfo)
                    }
                }
            }
        }
    }
}

/** Строка детали сообщения: метка и значение. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** Возвращает текст статуса доставки на русском. */
private fun getStatusText(status: DeliveryStatus?): String = when (status) {
    DeliveryStatus.SENDING -> "Отправляется..."
    DeliveryStatus.DELIVERED -> "Доставлено"
    DeliveryStatus.READ -> "Прочитано"
    DeliveryStatus.FAILED -> "Ошибка"
    null -> "Неизвестно"
}

/** Форматирует timestamp в строку вида dd.MM.yyyy HH:mm:ss. */
private fun formatFullTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.forLanguageTag("ru"))
    return sdf.format(java.util.Date(ts))
}
