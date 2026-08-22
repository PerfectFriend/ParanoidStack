/**
 * Экран экспорта логов: logcat, логи приложения, отправка и очистка.
 */
package com.example.ui.screens.debug

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.service.LogExporter
import kotlinx.coroutines.launch

@Composable
fun LogExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exporter = remember { LogExporter(context) }
    
    var logUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Экспорт логов", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            status = "Экспорт логов..."
                            logUri = exporter.exportLogcatToFile()
                            status = if (logUri != null) "Лог экспортирован" else "Ошибка экспорта"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экспорт logcat")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            status = "Экспорт логов приложения..."
                            val path = exporter.exportAppLogs()
                            status = if (path != null) "Логи сохранены: $path" else "Ошибка"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экспорт логов приложения")
                }
                
                Spacer(Modifier.height(8.dp))
                
                if (logUri != null) {
                    Button(
                        onClick = { logUri?.let { exporter.shareLog(it) } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить лог")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                OutlinedButton(
                    onClick = {
                        exporter.clearLogs()
                        logUri = null
                        status = "Логи очищены"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Очистить логи")
                }
            }
        }
        
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад")
        }
    }
}
