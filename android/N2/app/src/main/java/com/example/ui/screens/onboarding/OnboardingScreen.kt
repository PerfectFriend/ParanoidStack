/**
 * Экран онбординга (знакомства с приложением) с пошаговыми слайдами.
 */
package com.example.ui.screens.onboarding

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
 * Шаг онбординга с заголовком, описанием и эмодзи-иконкой.
 */
data class OnboardingStep(
    val title: String,
    val description: String,
    val emoji: String
)

private val steps = listOf(
    OnboardingStep(
        "Добро пожаловать в N2",
        "Децентрализованный мессенджер на протоколе SimpleX\nНикаких ID, никаких серверов",
        "\uD83D\uDC4B"
    ),
    OnboardingStep(
        "Полная приватность",
        "Сквозное шифрование, отсутствие метаданных\nВаши данные только на вашем устройстве",
        "\uD83D\uDD12"
    ),
    OnboardingStep(
        "Обход блокировок",
        "Tor, V2Ray, SOCKS5 цепочка\nОставайтесь на связи при любых условиях",
        "\uD83C\uDF10"
    ),
    OnboardingStep(
        "Готовы начать?",
        "Создайте профиль и пригласите друзей",
        "\uD83D\uDE80"
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit = {}
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val step = steps[currentStep]

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = step.emoji,
            fontSize = 64.sp
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = step.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = step.description,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            steps.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 12.dp else 8.dp)
                        .then(
                            if (index == currentStep) Modifier else Modifier
                        )
                ) {
                    if (index == currentStep) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    } else {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {}
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                if (currentStep < steps.size - 1) currentStep++
                else onComplete()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(if (currentStep < steps.size - 1) "Далее" else "Начать")
        }
    }
}
