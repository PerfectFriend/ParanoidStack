/**
 * Пакет темы оформления приложения — цвета тёплой палитры.
 * Определяет базовые цвета для деревянной/золотистой темы "Warm",
 * используемой в [DarkColorScheme] и [LightColorScheme] из Theme.kt.
 *
 * ## Цвета
 * - [WarmGold] — золотисто-бронзовый акцент.
 * - [AmberWood] / [Mahogany] — коричневые и бордовые оттенки.
 * - [SandLight] — светлый песочный фон.
 * - [CharcoalDark] / [ObsidianBackground] — тёмные фоны.
 * - [WarmSurface] / [WarmSurfaceVariant] — поверхности.
 * - [OnWarmSurface] — цвет текста на тёплой поверхности.
 */
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/** Золотисто-бронзовый цвет (основной акцент тёплой темы). */
val WarmGold = Color(0xFFD3A373)
/** Тёплый коричневый оттенок (сиенна). */
val AmberWood = Color(0xFFA0522D)
/** Глубокий тёмно-бордовый цвет. */
val Mahogany = Color(0xFF5E2E14)
/** Светлый песочный оттенок для фона в светлой теме. */
val SandLight = Color(0xFFEFEDE8)
/** Тёмный древесно-угольный оттенок. */
val CharcoalDark = Color(0xFF201E1B)
/** Фоновый цвет обсидиан (очень тёмный). */
val ObsidianBackground = Color(0xFF141210)
/** Тёплая поверхность. */
val WarmSurface = Color(0xFF2A241F)
/** Вариант тёплой поверхности. */
val WarmSurfaceVariant = Color(0xFF3B332D)
/** Цвет текста на тёплой поверхности. */
val OnWarmSurface = Color(0xFFEBE0D0)
