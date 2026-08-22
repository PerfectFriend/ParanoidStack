/**
 * Пакет темы оформления — глобальное состояние темы и Matrix-цветовые схемы.
 *
 * ## Компоненты
 * - [AppTheme] — глобальный флаг тёмной темы и переключатель.
 * - [ThemeManager] — альтернативный менеджер темы.
 * - [MatrixDarkColorScheme] / [MatrixLightColorScheme] — цветовые схемы в стиле «Матрица».
 * - [MatrixTheme] / [N2Theme] — Composable-обёртки, применяющие тему через MaterialTheme.
 */
package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Глобальное состояние темы приложения.
 * Хранит флаг [isDarkMode] и предоставляет метод [toggleTheme] для переключения.
 */
object AppTheme {
    /** Флаг тёмной темы (по умолчанию включена). */
    var isDarkMode by mutableStateOf(true)
        private set

    /** Переключает между тёмной и светлой темой. */
    fun toggleTheme() {
        isDarkMode = !isDarkMode
    }
}

object ThemeManager {
    var isDarkMode: Boolean by mutableStateOf(true)

    fun toggle() {
        isDarkMode = !isDarkMode
    }

    val currentTheme: String
        get() = if (isDarkMode) "dark" else "light"
}

/** Тёмная цветовая схема в стиле «Матрица» (зелёные неоновые тона на тёмном фоне). */
private val MatrixDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF41),
    secondary = Color(0xFF008F11),
    tertiary = Color(0xFF00CC33),
    background = Color(0xFF0A0A1A),
    surface = Color(0xFF111122),
    surfaceVariant = Color(0xFF1A1A33),
    onPrimary = Color.Black,
    onSecondary = Color(0xFF00FF41),
    onBackground = Color(0xFF00FF41),
    onSurface = Color(0xFF80FF99),
    errorContainer = Color(0xFF5E1E1E),
    onErrorContainer = Color(0xFFFFDADA),
    primaryContainer = Color(0xFF003300),
    onPrimaryContainer = Color(0xFF00FF41)
)

/** Светлая цветовая схема в стиле «Матрица». */
private val MatrixLightColorScheme = lightColorScheme(
    primary = Color(0xFF008F11),
    secondary = Color(0xFF00CC33),
    tertiary = Color(0xFF006600),
    background = Color(0xFFF0FFF0),
    surface = Color.White,
    surfaceVariant = Color(0xFFE0F0E0),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF001100),
    onSurface = Color(0xFF002200),
    errorContainer = Color(0xFFFFDADA),
    onErrorContainer = Color(0xFF5E1E1E),
    primaryContainer = Color(0xFFCCFFCC),
    onPrimaryContainer = Color(0xFF001100)
)

/**
 * Composable-обёртка темы «Матрица».
 * Применяет [MaterialTheme] с выбранной цветовой схемой и типографикой.
 *
 * @param content вложенный контент.
 */
@Composable
fun MatrixTheme(content: @Composable () -> Unit) {
    val colorScheme = if (AppTheme.isDarkMode) MatrixDarkColorScheme else MatrixLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun N2Theme(content: @Composable () -> Unit) {
    val colorScheme = if (ThemeManager.isDarkMode) MatrixDarkColorScheme else MatrixLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
