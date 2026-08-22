/**
 * Пакет темы оформления — анимационные эффекты.
 * Содержит [ShimmerEffect] — компонент для создания эффекта мерцания (shimmer)
 * при помощи анимированного линейного градиента.
 */
package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Анимационный эффект мерцания (shimmer) для индикации загрузки.
 * Создаёт движущийся градиент, имитирующий блик света.
 *
 * @param modifier модификатор компоновки.
 */
@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    // Бесконечная анимация сдвига градиента по горизонтали
    val transition = rememberInfiniteTransition()
    val shimmerTranslate by transition.animateFloat(
        initialValue = -200f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Градиент из трёх полос (тёмная-светлая-тёмная), движущийся справа налево
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Gray.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.25f),
            Color.Gray.copy(alpha = 0.15f)
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 300f, 0f)
    )

    Box(
        modifier = modifier
            .background(shimmerBrush)
    )
}
