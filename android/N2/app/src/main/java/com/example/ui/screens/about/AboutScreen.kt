/**
 * Экран «О приложении» с информацией о версии, сборке, протоколе и шифровании.
 */
package com.example.ui.screens.about

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    // Версия, номер сборки, колбэки для лицензий и GitHub
    appVersion: String = "1.0.0",
    buildNumber: String = "1",
    onBack: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onOpenGithub: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О приложении") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "N2",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Децентрализованный мессенджер",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("Версия", appVersion)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AboutRow("Сборка", buildNumber)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AboutRow("Протокол", "SimpleX")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AboutRow("Шифрование", "NaCl + Double Ratchet")
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(onClick = onOpenLicense, modifier = Modifier.fillMaxWidth()) {
                Text("Лицензии")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenGithub, modifier = Modifier.fillMaxWidth()) {
                Text("Исходный код")
            }

            Spacer(Modifier.height(32.dp))
            Text(
                text = "N2 \u00A9 2026",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Строка с парой «метка — значение» на экране «О приложении». */
@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
