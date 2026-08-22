/**
 * Экран обмена контактами: отображение собственного QR-кода и сканирование чужого.
 */
package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContactExchangeScreen(
    myInviteLink: String,
    onQrScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    var mode by remember { mutableStateOf("menu") } // "menu" | "show_qr" | "scan_qr"
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Обмен контактами", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        
        // Переключение между меню, показом QR и сканированием
        when (mode) {
            "menu" -> {
                Button(onClick = { mode = "show_qr" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Показать мой QR-код")
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { mode = "scan_qr" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сканировать QR-код")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Назад")
                }
            }
            "show_qr" -> {
                Text("Ваш код приглашения:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                // QR code would be rendered here by QrGenerator
                Text(myInviteLink, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { mode = "menu" }) {
                    Text("Назад")
                }
            }
            "scan_qr" -> {
                Text("Наведите камеру на QR-код собеседника")
                Spacer(Modifier.height(8.dp))
                // QrCodeScannerView would render here
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { mode = "menu" }) {
                    Text("Отмена")
                }
            }
        }
    }
}
