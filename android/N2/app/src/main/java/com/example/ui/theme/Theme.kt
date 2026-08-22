/**
 * Пакет темы оформления приложения — набор цветовых схем.
 * Содержит 7 цветовых схем для различных тем и точку входа [MyApplicationTheme].
 *
 * ## Доступные темы
 * - **warm** — тёплая (дерево, золото, кожа), поддерживает тёмный/светлый режим.
 * - **bw** — чёрно-белая (минимализм).
 * - **rainbow** — радужная (психоделическая, неоновые цвета).
 * - **fire** — огненная (красные/оранжевые/жёлтые оттенки).
 * - **water** — водная (голубые/синие тона).
 * - **cosmic** — космическая неоновая (бирюзовый, розовый, индиго).
 * - **hacker** — хакерская (зелёная матричная).
 *
 * ## Использование
 * ```
 * MyApplicationTheme(themeId = "cosmic") { ... }
 * ```
 *
 * @see MyApplicationTheme основная точка входа.
 */
package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Тёмная тёплая цветовая схема (дерево, золото, кожа). */
private val DarkColorScheme = darkColorScheme(
    primary = WarmGold,
    secondary = AmberWood,
    tertiary = Mahogany,
    background = ObsidianBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = OnWarmSurface,
    onSurface = OnWarmSurface,
    errorContainer = Color(0xFF5E1E1E),
    onErrorContainer = Color(0xFFFFDADA),
    primaryContainer = WarmSurfaceVariant,
    onPrimaryContainer = OnWarmSurface
)

/** Светлая тёплая цветовая схема. */
private val LightColorScheme = lightColorScheme(
    primary = Mahogany,
    secondary = AmberWood,
    tertiary = WarmGold,
    background = SandLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3EEE8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2C1E14),
    onSurface = Color(0xFF2C1E14),
    errorContainer = Color(0xFFFFDADA),
    onErrorContainer = Color(0xFF5E1E1E),
    primaryContainer = Color(0xFFF0E5D1),
    onPrimaryContainer = Color(0xFF2C1E14)
)

/** Чёрно-белая тема (минимализм). */
private val BwColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFF888888),
    tertiary = Color(0xFF333333),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF2E2E2E),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color.White
)

/** Радужная (психоделическая) тема — яркие неоновые цвета на тёмно-фиолетовом. */
private val RainbowColorScheme = darkColorScheme(
    primary = Color(0xFFFF00FF),
    secondary = Color(0xFF00FFFF),
    tertiary = Color(0xFFFFCC00),
    background = Color(0xFF1A052D),
    surface = Color(0xFF2B0B47),
    surfaceVariant = Color(0xFF3B125C),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFECE2FF),
    onSurface = Color(0xFFDFD0F8),
    primaryContainer = Color(0xFF3D1460),
    onPrimaryContainer = Color(0xFF00FFFF)
)

/** Огненная тема — красные, оранжевые и жёлтые оттенки на тёмном фоне. */
private val FireColorScheme = darkColorScheme(
    primary = Color(0xFFFF5722),
    secondary = Color(0xFFFFC107),
    tertiary = Color(0xFFD84315),
    background = Color(0xFF1E0700),
    surface = Color(0xFF310D02),
    surfaceVariant = Color(0xFF471505),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFE3D9),
    onSurface = Color(0xFFFAD1C4),
    primaryContainer = Color(0xFF3C1205),
    onPrimaryContainer = Color(0xFFFF5722)
)

/** Водная тема — голубые и синие тона. */
private val WaterColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFF2979FF),
    tertiary = Color(0xFF00B0FF),
    background = Color(0xFF001122),
    surface = Color(0xFF001E3D),
    surfaceVariant = Color(0xFF002C5A),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFE0F7FA),
    onSurface = Color(0xFFB1EBF2),
    primaryContainer = Color(0xFF002244),
    onPrimaryContainer = Color(0xFF00E5FF)
)

/** Космическая неоновая тема — бирюзовый, розовый, индиго. */
private val CosmicColorScheme = darkColorScheme(
    primary = Color(0xFF00FFCC),
    secondary = Color(0xFFFF007F),
    tertiary = Color(0xFF8A2BE2),
    background = Color(0xFF07050F),
    surface = Color(0xFF131124),
    surfaceVariant = Color(0xFF211D36),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color(0xFFECE9FC),
    onSurface = Color(0xFFE2DFF5),
    primaryContainer = Color(0xFF2F294E),
    onPrimaryContainer = Color(0xFF00FFCC)
)

/** Хакерская (зелёная матричная) тема. */
private val HackerColorScheme = darkColorScheme(
    primary = Color(0xFF00FF33),
    secondary = Color(0xFF003300),
    tertiary = Color(0xFF009900),
    background = Color(0xFF000000),
    surface = Color(0xFF060D06),
    surfaceVariant = Color(0xFF0F1E0F),
    onPrimary = Color.Black,
    onSecondary = Color(0xFF00FF33),
    onBackground = Color(0xFF00FF33),
    onSurface = Color(0xFF80FF99),
    primaryContainer = Color(0xFF081508),
    onPrimaryContainer = Color(0xFF00FF33)
)

/**
 * Главная точка входа темы приложения.
 * Выбирает цветовую схему на основе [themeId] и применяет её через [MaterialTheme].
 *
 * @param themeId идентификатор темы: "warm", "bw", "rainbow", "fire", "water", "cosmic", "hacker".
 * @param darkTheme флаг тёмной темы (используется только для "warm").
 * @param content вложенный контент.
 */
@Composable
fun MyApplicationTheme(
    themeId: String = "warm",
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeId.lowercase()) {
        "bw" -> BwColorScheme
        "rainbow" -> RainbowColorScheme
        "fire" -> FireColorScheme
        "water" -> WaterColorScheme
        "cosmic" -> CosmicColorScheme
        "hacker" -> HackerColorScheme
        "warm" -> if (darkTheme) DarkColorScheme else LightColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
