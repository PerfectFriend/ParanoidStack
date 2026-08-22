/**
 * Экран информации о группе: список участников, добавление/удаление и выход из группы.
 */
package com.example.ui.screens.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Информация о группе: идентификатор, название и список участников.
 */
data class GroupInfo(
    val id: String,
    val name: String,
    val members: List<GroupMember>
)

data class GroupMember(
    val id: String,
    val displayName: String,
    val role: String = "member"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    group: GroupInfo,
    currentUserId: String,
    onAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onBack: () -> Unit
) {
    var showLeaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Участники (${group.members.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onAddMember,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Добавить участника")
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(group.members) { member ->
                    ListItem(
                        headlineContent = { Text(member.displayName) },
                        supportingContent = {
                            Text(if (member.id == currentUserId) "Это вы" else "")
                        },
                        trailingContent = {
                            if (member.id != currentUserId) {
                                IconButton(onClick = { onRemoveMember(member.id) }) {
                                    Text("\u2716")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Button(
                onClick = { showLeaveDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("Покинуть группу")
            }
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Покинуть группу?") },
            text = { Text("Вы больше не будете получать сообщения от этой группы.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    onLeaveGroup()
                }) { Text("Покинуть", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Отмена") }
            }
        )
    }
}
