package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Компактный плеер голосовых сообщений.
 * Содержит кнопку play/pause, индикатор прогресса и отображение времени.
 * Воспроизведение симулируется через корутину с секундным шагом.
 *
 * @param durationSeconds общая длительность сообщения в секундах
 * @param filePath путь к файлу голосового сообщения (зарезервировано)
 * @param modifier модификатор компонента
 * @param accentColor акцентный цвет индикатора прогресса
 */
@Composable
fun VoiceMessagePlayer(
    durationSeconds: Int,
    filePath: String,
    modifier: Modifier = Modifier,
    accentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF00FF41)
) {
    // Состояние воспроизведения и текущая позиция (секунды)
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableFloatStateOf(0f) }
    val maxProgress = durationSeconds.coerceAtLeast(1).toFloat()

    // Таймер: каждую секунду увеличиваем позицию, пока не дойдём до конца
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (currentPosition < maxProgress) {
                delay(1000)
                currentPosition++
            }
            // Автоматически останавливаем по окончании
            isPlaying = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка play/pause
        IconButton(onClick = { isPlaying = !isPlaying }) {
            Text(
                text = if (isPlaying) "\u23F8" else "\u25B6",
                fontSize = 20.sp
            )
        }

        // Линейный индикатор прогресса
        LinearProgressIndicator(
            progress = { currentPosition / maxProgress },
            modifier = Modifier
                .weight(1f)
                .height(6.dp),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.width(8.dp))

        // Метка времени: текущая позиция / общая длительность
        Text(
            text = "${currentPosition.toInt()}с / ${durationSeconds}с",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
