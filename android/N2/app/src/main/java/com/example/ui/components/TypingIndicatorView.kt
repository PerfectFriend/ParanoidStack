package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Индикатор набора текста собеседником.
 * Показывает имя контакта и анимированные точки (...), когда он печатает.
 * Появляется и скрывается с анимацией slide/fade.
 *
 * @param contactName имя контакта, который набирает текст
 * @param isTyping флаг: true — показывать индикатор, false — скрыть
 * @param modifier модификатор компонента
 */
@Composable
fun TypingIndicatorView(
    contactName: String,
    isTyping: Boolean,
    modifier: Modifier = Modifier
) {
    // Анимированное появление/исчезновение текста
    AnimatedVisibility(
        visible = isTyping,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Text(
            text = "$contactName печатает…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    // Анимация мигающих точек (...) с циклом 500 мс
    if (isTyping) {
        var dots by remember { mutableStateOf("") }
        LaunchedEffect(isTyping) {
            while (true) {
                dots = when (dots) {
                    "" -> "."
                    "." -> ".."
                    ".." -> "..."
                    else -> ""
                }
                delay(500)
            }
        }
    }
}
