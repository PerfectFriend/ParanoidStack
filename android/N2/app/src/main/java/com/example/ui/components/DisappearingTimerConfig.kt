package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Вариант таймера самоуничтожения сообщения.
 *
 * @param label текстовое представление (например, "5 мин")
 * @param seconds значение в секундах
 */
data class TimerOption(
    val label: String,
    val seconds: Int
)

/**
 * Предопределённые опции таймера: от "Выкл" (0 сек) до 24 часов.
 */
private val timerOptions = listOf(
    TimerOption("Выкл", 0),
    TimerOption("5 сек", 5),
    TimerOption("30 сек", 30),
    TimerOption("1 мин", 60),
    TimerOption("5 мин", 300),
    TimerOption("30 мин", 1800),
    TimerOption("1 час", 3600),
    TimerOption("24 часа", 86400)
)

/**
 * Панель настройки таймера самоуничтожения сообщений (исчезающие сообщения).
 * Пользователь выбирает один из предустановленных интервалов и применяет его.
 *
 * @param currentTimerSeconds текущее значение таймера в секундах
 * @param onTimerChange вызывается при выборе нового значения
 * @param onApply вызывается при нажатии «Применить»
 * @param onBack вызывается при нажатии «Отмена»
 */
@Composable
fun DisappearingTimerConfig(
    currentTimerSeconds: Int = 0,
    onTimerChange: (Int) -> Unit = {},
    onApply: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Заголовок секции
        Text(
            text = "Исчезающие сообщения",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        // Пояснение: текущий статус таймера
        Text(
            text = if (currentTimerSeconds > 0)
                "Сообщения будут удалены через ${formatTimer(currentTimerSeconds)}"
            else "Сообщения не будут удаляться автоматически",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        // Сетка опций: по 2 чипа в ряд
        timerOptions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { option ->
                    val isSelected = currentTimerSeconds == option.seconds
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTimerChange(option.seconds) },
                        label = { Text(option.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Выравнивание, если в ряду остался один элемент
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Кнопки управления: Отмена / Применить
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) { Text("Отмена") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApply) {
                Text("Применить")
            }
        }
    }
}

/**
 * Форматирует количество секунд в читаемый текст: "30 сек", "5 мин", "2 ч" и т.д.
 */
private fun formatTimer(seconds: Int): String = when {
    seconds < 60 -> "$seconds сек"
    seconds < 3600 -> "${seconds / 60} мин"
    seconds < 86400 -> "${seconds / 3600} ч"
    else -> "${seconds / 86400} дн"
}
