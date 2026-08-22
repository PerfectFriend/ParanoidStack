/**
 * Экран диагностики сети с тестами Tor, V2Ray и SMP-сервера.
 */
package com.example.ui.screens.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.NetworkTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkTestScreen(
    testResults: Map<String, NetworkTestResult> = emptyMap(),
    isRunning: Boolean = false,
    onRunAllTests: () -> Unit = {},
    onTestTor: () -> Unit = {},
    onTestV2Ray: () -> Unit = {},
    onTestSMP: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Диагностика сети") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(
                        onClick = onRunAllTests,
                        enabled = !isRunning
                    ) { Text(if (isRunning) "Тест..." else "Все тесты") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            TestCard("Tor",
                testResults["tor"],
                !isRunning,
                onTestTor)
            Spacer(Modifier.height(8.dp))
            TestCard("V2Ray",
                testResults["v2ray"],
                !isRunning,
                onTestV2Ray)
            Spacer(Modifier.height(8.dp))
            TestCard("SMP Сервер",
                testResults["smp"],
                !isRunning,
                onTestSMP)
        }
    }
}

/** Карточка теста сети с названием, кнопкой запуска и результатом. */
@Composable
private fun TestCard(
    name: String,
    result: NetworkTestResult?,
    enabled: Boolean,
    onTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Button(onClick = onTest, enabled = enabled) {
                    Text("Тест")
                }
            }

            if (result != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (result.success) "\u2705" else "\u274C",
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${result.latencyMs} мс",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = result.error ?: "OK",
                        fontSize = 12.sp,
                        color = if (result.success)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
