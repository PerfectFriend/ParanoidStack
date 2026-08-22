package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Полноценная панель записи голоса с таймером,
 * кнопками отмены/отправки и анимацией уровня звука.
 */
@Composable
fun VoiceRecorderPanel(
    isRecording: Boolean = false,
    durationSeconds: Int = 0,
    amplitude: Float = 0f,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSendRecording: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isRecording,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u23FA", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))

                // Визуализация амплитуды
                val barCount = 20
                Row(
                    modifier = Modifier.weight(1f).height(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(barCount) { i ->
                        val barHeight = (amplitude * (0.3f + 0.7f * (i.toFloat() / barCount))).coerceIn(0.05f, 1f)
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height((barHeight * 24).dp)
                                .then(
                                    if (i % 2 == 0) Modifier else Modifier
                                )
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.error
                            ) {}
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                Text("${durationSeconds / 60}:${String.format("%02d", durationSeconds % 60)}",
                    fontSize = 14.sp)

                IconButton(onClick = onCancelRecording) { Text("\u2716", fontSize = 16.sp) }
                IconButton(onClick = onSendRecording) { Text("\u2714", fontSize = 16.sp) }
            }
        }
    }

    if (!isRecording) {
        VoiceMessageButton(
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onCancelRecording = onCancelRecording,
            isRecording = false
        )
    }
}
