package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Кнопка записи голосового сообщения.
 * При удержании — запись, при отпускании — отправка.
 * Отображает длительность записи в реальном времени.
 */
@Composable
fun VoiceMessageButton(
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    isRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    var recordDuration by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordDuration = 0
            while (true) {
                delay(1000)
                recordDuration++
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            Text(
                text = "\u23FA ${recordDuration}с",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancelRecording) {
                Text("Отмена", fontSize = 12.sp)
            }
        }

        FilledIconButton(
            onClick = {
                if (isRecording) onStopRecording()
                else onStartRecording()
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isRecording)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = if (isRecording) "\u25A0" else "\uD83C\uDF99",
                fontSize = 20.sp
            )
        }
    }
}
