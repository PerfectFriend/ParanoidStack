/**
 * Пакет темы оформления — типографика.
 * Определяет базовый стиль [Typography] для Material Design 3.
 * В текущей реализации задан только [bodyLarge]; остальные стили
 * наследуют стандартные значения MaterialTheme.
 */
package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Базовая типографика приложения.
 * Задаёт стиль [bodyLarge] по умолчанию; остальные стили можно переопределить
 * при необходимости.
 */
val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      )
  )
