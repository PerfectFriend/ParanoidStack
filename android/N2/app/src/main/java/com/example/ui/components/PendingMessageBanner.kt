package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Баннер для отображения количества неотправленных сообщений
 * с возможностью повторной отправки всех сразу.
 */
@Composable
fun PendingMessageBanner(
    pendingCount: Int = 0,
    onRetryAll: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = pendingCount > 0,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u26A0", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Неотправленные сообщения",
                        fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    Text("$pendingCount сообщений ожидают отправки",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRetryAll) {
                    Text("Отправить", fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Text("\u2716", fontSize = 14.sp)
                }
            }
        }
    }
}
