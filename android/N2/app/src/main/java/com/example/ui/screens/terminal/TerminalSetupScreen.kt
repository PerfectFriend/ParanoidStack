package com.example.ui.screens.terminal

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TerminalStage
import com.example.ui.TerminalStepResult

@Composable
fun TerminalSetupScreen(
    currentStageIndex: Int,
    stepResults: Map<TerminalStage, TerminalStepResult>,
    isRunning: Boolean,
    terminalReady: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit = {},
    onLaunchMessenger: () -> Unit = {},
    onReset: () -> Unit = {}
) {
    val stages = TerminalStage.entries

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // Заголовок
            Text(
                text = if (terminalReady) "TERMINAL READY"
                       else if (isRunning) "INITIALIZING NODE…"
                       else if (errorMessage != null) "CONNECTION ERROR"
                       else "NODE STARTUP",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (terminalReady) Color(0xFF00FFCC)
                        else if (errorMessage != null) Color(0xFFFF007F)
                        else Color(0xFF88AAFF),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (terminalReady) "All systems operational"
                       else if (errorMessage != null) errorMessage
                       else if (isRunning) "Testing network layers…"
                       else "Awaiting authentication",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(24.dp))

            // Список этапов
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(stages) { index, stage ->
                    val result = stepResults[stage]
                    val isCurrent = index == currentStageIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .alpha(if (isCurrent || result !is TerminalStepResult.Skipped) 1f else 0.4f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Иконка статуса
                        Text(
                            text = when (result) {
                                is TerminalStepResult.Pass -> "✅"
                                is TerminalStepResult.Fail -> "⚠️"
                                is TerminalStepResult.Running -> "⏳"
                                is TerminalStepResult.Skipped -> "⏺"
                                null -> "⏺"
                            },
                            fontSize = 14.sp,
                            modifier = Modifier.width(28.dp)
                        )

                        // Название этапа
                        Text(
                            text = stage.label,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when (result) {
                                is TerminalStepResult.Pass -> Color(0xFF00FFCC)
                                is TerminalStepResult.Fail -> Color(0xFFFF8800)
                                is TerminalStepResult.Running -> Color(0xFF88AAFF)
                                else -> Color.White.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        // Детали
                        if (result is TerminalStepResult.Pass && result.detail.isNotEmpty()) {
                            Text(
                                text = result.detail,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.35f),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                        if (result is TerminalStepResult.Fail) {
                            Text(
                                text = result.detail,
                                fontSize = 9.sp,
                                color = Color(0xFFFF8800).copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Прогресс-бар
            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp),
                    color = Color(0xFF00FFCC),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Кнопки
            if (terminalReady) {
                Button(
                    onClick = onLaunchMessenger,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FFCC),
                        contentColor = Color(0xFF020108)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "✦ LAUNCH MESSENGER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else if (errorMessage != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8800).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("RETRY DIAGNOSTICS", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onReset) {
                    Text("RESET TERMINAL", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                }
            } else if (!isRunning && currentStageIndex == 0) {
                Text(
                    text = "Authenticate to begin",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(16.dp))

            // Версия
            Text(
                text = "NotNode v1.1.0 · N2 protocol",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.15f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
