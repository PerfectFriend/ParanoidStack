package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

/**
 * Композер отложенного сообщения.
 * Позволяет ввести текст, выбрать задержку отправки и отправить сообщение по таймеру.
 *
 * @param currentText текущий текст сообщения
 * @param onTextChange вызывается при изменении текста
 * @param delayMinutes задержка отправки в минутах (0 = сейчас)
 * @param onDelayChange вызывается при выборе новой задержки
 * @param onSend вызывается при нажатии «Отправить» / «Отложить»
 * @param onCancel вызывается при отмене
 */
@Composable
fun ScheduledMessageComposer(
    currentText: String,
    onTextChange: (String) -> Unit = {},
    delayMinutes: Int = 0,
    onDelayChange: (Int) -> Unit = {},
    onSend: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    // Предустановленные опции задержки
    val delayOptions = listOf(
        0 to "Сейчас",
        5 to "5 мин",
        15 to "15 мин",
        30 to "30 мин",
        60 to "1 час",
        360 to "6 часов",
        1440 to "24 часа"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        // Поле ввода текста с минимальной высотой 80dp
        OutlinedTextField(
            value = currentText,
            onValueChange = onTextChange,
            placeholder = { Text("Сообщение...") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            maxLines = 5
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка выбора задержки с выпадающим меню
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    val label = delayOptions.find { it.first == delayMinutes }?.second ?: "Таймер"
                    Text("\u23F0 $label")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    delayOptions.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onDelayChange(minutes)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // Кнопки: отмена и отправка/отложить
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel) { Text("Отмена") }
                Button(onClick = onSend) {
                    Text(if (delayMinutes > 0) "\u23F0 Отложить" else "Отправить")
                }
            }
        }

        // Если установлена задержка — показываем расчётное время отправки
        if (delayMinutes > 0) {
            val estimatedTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes.toLong())
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("ru"))
            Text(
                text = "Будет отправлено в ${sdf.format(java.util.Date(estimatedTime))}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
