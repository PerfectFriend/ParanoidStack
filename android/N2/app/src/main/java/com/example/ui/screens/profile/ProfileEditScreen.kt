/**
 * Экран редактирования профиля: имя, статус и аватар пользователя.
 */
package com.example.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    currentName: String = "",
    currentStatus: String = "",
    onSave: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    var displayName by remember { mutableStateOf(currentName) }
    var status by remember { mutableStateOf(currentStatus) }
    val hasChanges = displayName != currentName || status != currentStatus

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактировать профиль") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Отмена") }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(displayName, status) },
                        enabled = hasChanges
                    ) { Text("Сохранить") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { }) {
                Text("Сменить аватар")
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Отображаемое имя") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = status,
                onValueChange = { status = it },
                label = { Text("Статус") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
