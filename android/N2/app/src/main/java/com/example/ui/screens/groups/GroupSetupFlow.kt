package com.example.ui.screens.groups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class GroupSetupStep { NAME, MEMBERS, CONFIRM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSetupFlow(
    contacts: List<ContactSelection> = emptyList(),
    onComplete: (String, String, List<String>) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {}
) {
    var currentStep by remember { mutableStateOf(GroupSetupStep.NAME) }
    var groupName by remember { mutableStateOf("") }
    var groupDescription by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (currentStep) {
                        GroupSetupStep.NAME -> "Название группы"
                        GroupSetupStep.MEMBERS -> "Выбор участников"
                        GroupSetupStep.CONFIRM -> "Подтверждение"
                    })
                },
                navigationIcon = {
                    TextButton(onClick = {
                        when (currentStep) {
                            GroupSetupStep.NAME -> onBack()
                            GroupSetupStep.MEMBERS -> currentStep = GroupSetupStep.NAME
                            GroupSetupStep.CONFIRM -> currentStep = GroupSetupStep.MEMBERS
                        }
                    }) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            // Индикатор шагов
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                GroupSetupStep.values().forEachIndexed { index, step ->
                    Surface(
                        modifier = Modifier.size(8.dp).padding(horizontal = 4.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (step.ordinal <= currentStep.ordinal)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ) {}
                }
            }

            Spacer(Modifier.height(24.dp))

            when (currentStep) {
                GroupSetupStep.NAME -> {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groupDescription,
                        onValueChange = { groupDescription = it },
                        label = { Text("Описание (необязательно)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        maxLines = 3
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { currentStep = GroupSetupStep.MEMBERS },
                        enabled = groupName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Далее →") }
                }

                GroupSetupStep.MEMBERS -> {
                    Text("Выбрано: ${selectedMembers.size}", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    contacts.forEach { contact ->
                        val isSelected = contact.id in selectedMembers
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedMembers = if (checked)
                                        selectedMembers + contact.id
                                    else selectedMembers - contact.id
                                }
                            )
                            Text(contact.displayName)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { currentStep = GroupSetupStep.CONFIRM },
                        enabled = selectedMembers.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Далее →") }
                }

                GroupSetupStep.CONFIRM -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(groupName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (groupDescription.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(groupDescription, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("${selectedMembers.size} участников", fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { onComplete(groupName, groupDescription, selectedMembers) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Создать группу") }
                }
            }
        }
    }
}
