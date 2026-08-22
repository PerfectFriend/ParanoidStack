/**
 * Экран предпросмотра файла: отображение изображения или иконки файла, информация о нём.
 */
package com.example.ui.screens.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    fileName: String = "",
    fileSizeBytes: Long = 0,
    mimeType: String = "",
    bitmap: ImageBitmap? = null,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = onShare) { Text("Поделиться") }
                    TextButton(onClick = onSave) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bitmap != null && mimeType.startsWith("image/")) {
                Image(
                    bitmap = bitmap,
                    contentDescription = fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(16.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getFileEmoji(mimeType),
                        fontSize = 64.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FileInfoRow("Имя", fileName)
                    FileInfoRow("Тип", if (mimeType.isNotBlank()) mimeType else "неизвестно")
                    FileInfoRow("Размер", formatFileSize(fileSizeBytes))
                }
            }
        }
    }
}

/** Строка информации о файле: метка и значение. */
@Composable
private fun FileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

/** Возвращает эмодзи-иконку в зависимости от MIME-типа. */
private fun getFileEmoji(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "\uD83D\uDDBC"
    mimeType.startsWith("video/") -> "\uD83C\uDFA5"
    mimeType.startsWith("audio/") -> "\uD83C\uDFB5"
    mimeType.startsWith("text/") -> "\uD83D\uDCC4"
    else -> "\uD83D\uDCC1"
}

/** Форматирует размер файла в человекочитаемый вид. */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
