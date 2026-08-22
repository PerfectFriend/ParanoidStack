/**
 * Панель диагностики и отладки: системная информация, crash-логи и тесты соединения.
 */
package com.example.ui.screens.debug

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DebugPanel(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var systemInfo by remember { mutableStateOf("") }
    var showFullLog by remember { mutableStateOf(false) }

    // Загружаем информацию о системе при первом рендере
    LaunchedEffect(Unit) {
        systemInfo = buildSystemInfo(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Диагностика", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Системная информация", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(systemInfo, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Последний crash лог", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                val crashLog = com.example.MyApplication.lastCrashLog
                if (crashLog != null) {
                    Text(
                        text = if (showFullLog) crashLog else crashLog.take(500) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { showFullLog = !showFullLog }) {
                        Text(if (showFullLog) "Свернуть" else "Показать полностью")
                    }
                } else {
                    Text("Нет сохранённых crash-логов", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Тесты соединения", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                var testResult by remember { mutableStateOf("") }

                // Проверка доступности Tor и V2Ray через сокетное соединение
                Button(onClick = {
                    testResult = "Тестирование Tor (127.0.0.1:9050)...\n"
                    try {
                        val s = java.net.Socket()
                        s.connect(java.net.InetSocketAddress("127.0.0.1", 9050), 2000)
                        s.close()
                        testResult += "✓ Tor доступен\n"
                    } catch (e: Exception) {
                        testResult += "✗ Tor недоступен: ${e.message}\n"
                    }

                    testResult += "Тестирование V2Ray (127.0.0.1:10808)...\n"
                    try {
                        val s = java.net.Socket()
                        s.connect(java.net.InetSocketAddress("127.0.0.1", 10808), 2000)
                        s.close()
                        testResult += "✓ V2Ray доступен\n"
                    } catch (e: Exception) {
                        testResult += "✗ V2Ray недоступен: ${e.message}\n"
                    }
                }) {
                    Text("Проверить соединение")
                }

                Spacer(Modifier.height(8.dp))

                if (testResult.isNotEmpty()) {
                    Text(testResult, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}

/** Собирает строку с системной информацией: устройство, Android, память, профили. */
private fun buildSystemInfo(context: Context): String = buildString {
    appendLine("Устройство: ${Build.MODEL}")
    appendLine("Производитель: ${Build.MANUFACTURER}")
    appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
    appendLine("Android версия: ${Build.VERSION.RELEASE}")
    appendLine("Приложение: ${context.applicationContext.packageName}")
    appendLine("Профилей: ${com.example.data.ProfileManager.getProfiles().size}")
    appendLine("Активный профиль: ${com.example.data.ProfileManager.getActiveProfile()?.name ?: "нет"}")

    val memInfo = android.app.ActivityManager.MemoryInfo()
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    am.getMemoryInfo(memInfo)
    appendLine("Доступно памяти: ${memInfo.availMem / 1_000_000} MB")
    appendLine("Всего памяти: ${memInfo.totalMem / 1_000_000} MB")
}
