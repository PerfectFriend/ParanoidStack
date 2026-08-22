/**
 * Экран создания группы: выбор участников и ввод названия.
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
import androidx.compose.ui.graphics.Color

/**
 * Модель выбора контакта: идентификатор, отображаемое имя и флаг выбора.
 */
data class ContactSelection(
    val id: String,
    val displayName: String,
    var isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    contacts: List<ContactSelection>,
    onCreateGroup: (String, List<String>) -> Unit,
    onBack: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf(contacts.map { it.id }) }
    val isFormValid = groupName.isNotBlank() && selectedContacts.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать группу") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Отмена") }
                },
                actions = {
                    TextButton(
                        onClick = { onCreateGroup(groupName, selectedContacts) },
                        enabled = isFormValid
                    ) { Text("Создать") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Название группы") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Участники (${selectedContacts.size}):", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            LazyColumn {
                items(contacts) { contact ->
                    val isChecked = contact.id in selectedContacts
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedContacts = if (checked) {
                                    selectedContacts + contact.id
                                } else {
                                    selectedContacts - contact.id
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(contact.displayName)
                    }
                }
            }
        }
    }
}
