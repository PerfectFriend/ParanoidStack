package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SnoozeOption(
    val label: String,
    val durationMinutes: Int
)

private val snoozeOptions = listOf(
    SnoozeOption("15 минут", 15),
    SnoozeOption("30 минут", 30),
    SnoozeOption("1 час", 60),
    SnoozeOption("2 часа", 120),
    SnoozeOption("4 часа", 240),
    SnoozeOption("8 часов", 480),
    SnoozeOption("До завтра", 1440)
)

@Composable
fun NotificationSnoozePicker(
    onSnooze: (Int) -> Unit = {},
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Отложить уведомления",
                fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("Вы не будете получать уведомления\nв течение выбранного периода",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            snoozeOptions.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onSnooze(option.durationMinutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(option.label, modifier = Modifier.weight(1f))
                        Text("\u23F3", fontSize = 16.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Отмена")
            }
        }
    }
}
