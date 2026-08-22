package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Диалог предложения оценить приложение.
 * Показывается после N-го запуска или N-й игры.
 */
@Composable
fun AppRatingPrompt(
    onRate: () -> Unit = {},
    onLater: () -> Unit = {},
    onNever: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text("\u2B50", fontSize = 48.sp)
        },
        title = {
            Text("Нравится N2?", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Если вам нравится приложение,\nпожалуйста, оцените его в магазине!",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = onRate, modifier = Modifier.fillMaxWidth()) {
                Text("Оценить")
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onLater) { Text("Позже") }
                TextButton(onClick = onNever) { Text("Больше не показывать") }
            }
        }
    )
}
