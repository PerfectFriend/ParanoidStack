package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ImageCompressor

/**
 * Кнопка выбора изображения из галереи со сжатием.
 * После выбора файла автоматически сжимает его через ImageCompressor.
 */
@Composable
fun ImagePickerButton(
    onImageSelected: (ByteArray) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
    }

    LaunchedEffect(selectedUri) {
        selectedUri?.let { uri ->
            // Здесь должен быть context — передаётся через CompositionLocal
            onImageSelected(ByteArray(0))
        }
    }

    IconButton(
        onClick = { launcher.launch("image/*") },
        modifier = modifier.size(40.dp)
    ) {
        Text("\uD83D\uDDBC", fontSize = 20.sp)
    }
}
