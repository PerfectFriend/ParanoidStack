package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Кнопка прикрепления файла.
 * Открывает системный файловый менеджер для выбора любого файла.
 *
 * @param onFileSelected вызывается с URI выбранного файла
 * @param modifier модификатор компонента
 */
@Composable
fun FileAttachmentButton(
    onFileSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Запускаем системный picker для любых типов файлов (*/*)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFileSelected(it) }
    }
    
    // Кнопка-иконка скрепки, по нажатию открывающая файловый менеджер
    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
        Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
    }
}
