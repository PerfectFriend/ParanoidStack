/**
 * Экран блокировки приложения с вводом PIN-кода и поддержкой биометрической аутентификации.
 */
package com.example.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLockScreen(
    isBiometricAvailable: Boolean = false,
    isSetupMode: Boolean = false,
    onUnlockWithPin: (String) -> Unit = {},
    onUnlockWithBiometric: () -> Unit = {},
    onResetApp: () -> Unit = {},
    failedAttempts: Int = 0
) {
    var pin by remember { mutableStateOf("") }                // Вводимый PIN-код
    var showResetOption by remember { mutableStateOf(false) }  // Показывать сброс после 5 ошибок
    val maxPinLength = 6                                      // Максимальная длина PIN

    // Обновляем флаг сброса при изменении числа неудачных попыток
    LaunchedEffect(failedAttempts) {
        showResetOption = failedAttempts >= 5
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = if (isSetupMode) "Set your PIN" else "Enter PIN",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FFCC),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSetupMode) "Choose a 4-6 digit code to secure your terminal"
                   else "Enter your ${maxPinLength}-digit PIN to unlock",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

        if (failedAttempts > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Неверных попыток: $failedAttempts",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= maxPinLength) pin = it   // Ограничиваем длину
                if (it.length == maxPinLength) onUnlockWithPin(it)  // Авторазблокировка при заполнении
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (pin.length == maxPinLength) onUnlockWithPin(pin) }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("******", textAlign = TextAlign.Center) }
        )

        Spacer(Modifier.height(16.dp))

        if (isBiometricAvailable) {
            OutlinedButton(
                onClick = onUnlockWithBiometric,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("\uD83D\uDC41\u200D\uD83D\uDDE8 Биометрия")
            }
        }

        if (showResetOption) {
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onResetApp) {
                Text("Сбросить приложение", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
