package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/**
 * MATRIX-стиль boot screen — 3 секунды.
 * Админ в тёмной серверной перед консолью, зелёные огни, цифровой дождь.
 */
@Composable
fun BootScreen(onBootFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    val rainOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rain"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )
    val scanline by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scan"
    )

    val chars = remember { "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEF".toList() }
    val rainDrops = remember {
        (0 until 60).map { RainDrop(Random.nextInt(0, 400), Random.nextInt(-200, 0), Random.nextFloat() * 0.8f + 0.2f) }
    }
    val adminPixels = remember { generateAdminSilhouette() }

    // console-мигание
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3000)
        onBootFinished()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000800)), // очень тёмный зелёный
        contentAlignment = Alignment.Center
    ) {
        // ─── Цифровой дождь ──────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cols = size.width / 12f
            val cellH = 14f
            // MATRIX rain
            for (drop in rainDrops) {
                val x = drop.x.toFloat() / 400f * size.width
                val yBase = (drop.y + rainOffset * (size.height + 400f)) % (size.height + 200f) - 100f
                val len = 8 + (drop.speed * 6).toInt()
                for (i in 0 until len) {
                    val y = yBase - i * cellH
                    if (y < -cellH || y > size.height + cellH) continue
                    val alpha = (1f - i.toFloat() / len) * drop.speed * 0.6f
                    val isHead = i == 0
                    drawCircle(
                        color = Color(0xFF00FF41).copy(alpha = if (isHead) 1f else alpha * 0.5f),
                        radius = if (isHead) 3f else 1.5f,
                        center = Offset(x + Random.nextFloat() * 4f, y)
                    )
                }
            }
        }

        // ─── Админ за консолью + серверная ──────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
        ) {
            val cx = size.width / 2
            val cy = size.height / 2 + 40f
            val scale = minOf(size.width, size.height) / 600f

            // Фоновое свечение серверной стойки
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF001800).copy(alpha = 0.3f),
                        Color(0xFF000800).copy(alpha = 0.0f)
                    )
                ),
                size = size
            )

            // Стеллаж серверов (слева)
            val rackX = cx - 140f * scale
            val rackY = cy - 100f * scale
            drawRect(
                color = Color(0xFF002200).copy(alpha = 0.6f),
                topLeft = Offset(rackX, rackY),
                size = androidx.compose.ui.geometry.Size(50f * scale, 200f * scale)
            )
            // Лампочки серверов (мигающие зелёные огоньки)
            for (row in 0 until 8) {
                for (col in 0 until 3) {
                    val ledOn = ((row * 7 + col * 13 + (rainOffset * 100).toInt()) % 3) != 0
                    if (ledOn) {
                        drawCircle(
                            color = Color(0xFF00FF41).copy(alpha = 0.3f + 0.3f * glowPulse),
                            radius = 2f * scale,
                            center = Offset(
                                rackX + 8f * scale + col * 14f * scale,
                                rackY + 12f * scale + row * 22f * scale
                            )
                        )
                    }
                }
            }
            // Вторая стойка (справа)
            val rack2X = cx + 90f * scale
            drawRect(
                color = Color(0xFF002200).copy(alpha = 0.4f),
                topLeft = Offset(rack2X, rackY),
                size = androidx.compose.ui.geometry.Size(45f * scale, 200f * scale)
            )
            for (row in 0 until 6) {
                val ledOn = ((row * 17 + (rainOffset * 100).toInt()) % 2) == 0
                if (ledOn) {
                    drawCircle(
                        color = Color(0xFF00FF41).copy(alpha = 0.2f + 0.3f * glowPulse),
                        radius = 2f * scale,
                        center = Offset(
                            rack2X + 22f * scale,
                            rackY + 15f * scale + row * 30f * scale
                        )
                    )
                }
            }

            // Кабели между стойками
            for (i in 0 until 3) {
                val t = rainOffset * 2f + i * 0.3f
                val cableY = rackY + 50f * scale + i * 40f * scale + sin(t * PI.toFloat()) * 8f
                drawLine(
                    color = Color(0xFF003300).copy(alpha = 0.3f),
                    start = Offset(rackX + 50f * scale, cableY),
                    end = Offset(rack2X, cableY + 10f * scale * sin(t * 1.3f)),
                    strokeWidth = 2f * scale
                )
            }

            // Силуэт админа перед консолью
            drawAdmin(adminPixels, cx, cy + 20f * scale, scale, glowPulse)

            // Монитор/консоль
            val monW = 120f * scale
            val monH = 80f * scale
            val monX = cx - monW / 2f
            val monY = cy - 100f * scale

            // Корпус монитора
            drawRoundRect(
                color = Color(0xFF001A00),
                topLeft = Offset(monX - 8f * scale, monY - 4f * scale),
                size = androidx.compose.ui.geometry.Size(monW + 16f * scale, monH + 12f * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale)
            )
            // Экран (тёмный)
            drawRoundRect(
                color = Color(0xFF000A00),
                topLeft = Offset(monX, monY),
                size = androidx.compose.ui.geometry.Size(monW, monH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
            )
            // Строчки кода на мониторе
            val codeLines = listOf(
                "[kernel] init crypto.. OK",
                "[tor]    daemon v0.4.4.6",
                "[v2ray]  core v1.8.24",
                "[smp]    handshake.. OK",
                "[net]    onion ready",
                "_"
            )
            for ((i, line) in codeLines.withIndex()) {
                val codeAlpha = if (i == codeLines.size - 1) {
                    if (cursorVisible) 0.8f else 0f
                } else 0.25f + 0.3f * (i.toFloat() / codeLines.size) * glowPulse
                val lineY = monY + 10f * scale + i * 10f * scale
                for ((ci, ch) in line.withIndex()) {
                    val charX = monX + 6f * scale + ci * 6f * scale
                    val flicker = if (ch == 'O' || ch == 'K') {
                        0.5f + 0.5f * sin(rainOffset * 100f + i.toFloat() * 7f)
                    } else 1f
                    drawCircle(
                        color = Color(0xFF00FF41).copy(alpha = codeAlpha * flicker * 0.6f),
                        radius = 1.2f,
                        center = Offset(charX, lineY)
                    )
                }
            }
            // CRT scanline effect
            val scanY = scanline * size.height
            drawRect(
                color = Color(0xFF00FF41).copy(alpha = 0.03f),
                topLeft = Offset(0f, scanY),
                size = androidx.compose.ui.geometry.Size(size.width, 2f)
            )
            // Vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0xFF000800).copy(alpha = 0.6f)),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.7f
                ),
                size = size
            )
        }

        // ─── ТЕКСТ "BOOT" ────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 60.dp)
                .align(Alignment.BottomCenter)
        ) {
            // CRT-стиль "BOOT"
            Text(
                text = "BOOT",
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 16.sp,
                color = Color(0xFF00FF41).copy(alpha = 0.7f + 0.3f * glowPulse),
                modifier = Modifier.background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00FF41).copy(alpha = 0.05f),
                            Color(0xFF00FF41).copy(alpha = 0.02f * glowPulse)
                        )
                    )
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "v1.1.0 · N2 protocol",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp,
                color = Color(0xFF00FF41).copy(alpha = 0.25f)
            )
        }

        // ─── Загрузочный индикатор ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 80.dp, vertical = 24.dp)
                .align(Alignment.BottomCenter)
        ) {
            // Прогресс-бар
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                val w = size.width * (rainOffset * 3f % 1f)
                drawLine(
                    color = Color(0xFF00FF41).copy(alpha = 0.4f * glowPulse),
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f),
                    strokeWidth = 2f
                )
            }
        }
    }
}

private data class RainDrop(val x: Int, val y: Int, val speed: Float)

/**
 * Генерирует пиксельный силуэт человека сидящего за консолью.
 * Возвращает список точек (нормализованные 0..1, 0 = верх/лево).
 */
private fun generateAdminSilhouette(): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()
    // Голова
    for (a in 0 until 360 step 15) {
        val r = 0.06f + Random.nextFloat() * 0.015f
        points.add(
            0.5f + r * cos(a * PI.toFloat() / 180f) to
            0.12f + r * sin(a * PI.toFloat() / 180f)
        )
    }
    // Тело (плечи-талия)
    for (row in 0 until 10) {
        val frac = row / 10f
        val width = 0.25f - frac * 0.05f
        points.add(0.5f - width + Random.nextFloat() * 0.02f to 0.20f + frac * 0.25f)
        points.add(0.5f + width - Random.nextFloat() * 0.02f to 0.20f + frac * 0.25f)
    }
    // Левая рука (к клавиатуре)
    for (t in 0 until 6) {
        val frac = t / 5f
        points.add(0.28f - frac * 0.08f + Random.nextFloat() * 0.02f to 0.30f + frac * 0.08f)
    }
    // Правая рука (мышь)
    for (t in 0 until 6) {
        val frac = t / 5f
        points.add(0.72f + frac * 0.06f + Random.nextFloat() * 0.02f to 0.30f + frac * 0.07f)
    }
    // Глаза (отблеск от экрана)
    points.add(0.48f to 0.14f)
    points.add(0.52f to 0.14f)
    return points
}

/**
 * Рисует силуэт админа из пиксельных точек.
 */
private fun DrawScope.drawAdmin(
    pixels: List<Pair<Float, Float>>,
    cx: Float, cy: Float, scale: Float, glow: Float
) {
    for ((px, py) in pixels) {
        val x = cx - 60f * scale + px * 120f * scale
        val y = cy - 60f * scale + py * 120f * scale
        // Зелёное свечение вокруг фигуры
        drawCircle(
            color = Color(0xFF00FF41).copy(alpha = 0.15f * glow),
            radius = 4f * scale,
            center = Offset(x, y)
        )
        drawCircle(
            color = Color(0xFF003300).copy(alpha = 0.8f),
            radius = 2.2f * scale,
            center = Offset(x, y)
        )
        // Контур фигуры
        drawCircle(
            color = Color(0xFF00FF41).copy(alpha = 0.3f + 0.2f * glow),
            radius = 1.8f * scale,
            center = Offset(x, y)
        )
    }
    // Зелёный отблеск от монитора на лицо
    for (i in 0 until 5) {
        val x = cx + (-8f + i * 4f) * scale
        val y = cy - 32f * scale
        drawCircle(
            color = Color(0xFF00FF41).copy(alpha = 0.05f * glow),
            radius = 6f * scale,
            center = Offset(x, y)
        )
    }
}
