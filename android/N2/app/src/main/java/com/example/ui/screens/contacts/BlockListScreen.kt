package com.example.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BlockedContact(
    val id: String,
    val displayName: String,
    val blockedAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockListScreen(
    blockedContacts: List<BlockedContact> = emptyList(),
    onUnblock: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заблокированные контакты") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (blockedContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDE10", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Нет заблокированных контактов",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(
                    text = "${blockedContacts.size} заблокирован(о)",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn {
                    items(blockedContacts) { contact ->
                        ListItem(
                            headlineContent = {
                                Text(contact.displayName, fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text("Заблокирован: ${java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.forLanguageTag("ru")).format(java.util.Date(contact.blockedAt))}",
                                    fontSize = 12.sp)
                            },
                            trailingContent = {
                                OutlinedButton(onClick = { onUnblock(contact.id) }) {
                                    Text("Разблок.", fontSize = 12.sp)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
