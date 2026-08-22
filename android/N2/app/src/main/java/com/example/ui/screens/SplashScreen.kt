package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    var alpha by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        alpha = 1f
        delay(2000)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D0B1A),
                        Color(0xFF07050F),
                        Color(0xFF020108)
                    ),
                    radius = 1.5f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val orbitR = minOf(cx, cy) * 0.35f
            for (i in 0 until 12) {
                val angle = (i.toFloat() / 12) * 2f * PI.toFloat() + progress * 2f * PI.toFloat()
                val px = cx + orbitR * cos(angle)
                val py = cy + orbitR * sin(angle)
                val dotSize = (4 + i % 3 * 2).toFloat()
                drawCircle(
                    color = Color(0xFF00FFCC).copy(alpha = 0.3f + 0.2f * (i % 3).toFloat() / 2f),
                    radius = dotSize * pulse,
                    center = Offset(px, py)
                )
            }
            for (i in 0 until 8) {
                val angle = (i.toFloat() / 8) * 2f * PI.toFloat() - progress * 1.5f * PI.toFloat()
                val r = orbitR * 0.6f
                val px = cx + r * cos(angle)
                val py = cy + r * sin(angle)
                drawCircle(
                    color = Color(0xFFFF007F).copy(alpha = 0.2f),
                    radius = 3f * pulse,
                    center = Offset(px, py)
                )
            }
            val ringAlpha = 0.12f + 0.08f * pulse
            drawCircle(
                color = Color(0xFF00FFCC).copy(alpha = ringAlpha),
                radius = orbitR + 20f,
                center = Offset(cx, cy),
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = Color(0xFFFF007F).copy(alpha = ringAlpha * 0.5f),
                radius = orbitR * 0.6f + 15f,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "N2",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp,
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00FFCC),
                            Color(0xFFFF007F),
                            Color(0xFF8A2BE2)
                        )
                    )
                ),
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "decentralized · encrypted · sovereign",
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.5f * pulse)
            )
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF00FFCC).copy(alpha = pulse * 0.6f),
                                Color(0xFFFF007F).copy(alpha = pulse * 0.3f),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}
