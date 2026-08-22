package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ThreadMessage(
    val id: Long,
    val text: String,
    val senderName: String,
    val timestamp: Long,
    val isOwn: Boolean
)

@Composable
fun MessageThread(
    parentMessage: ThreadMessage,
    replies: List<ThreadMessage>,
    onReplyClick: (Long) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ответы (${replies.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = onClose) { Text("✖") }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(parentMessage.senderName, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Text(parentMessage.text, fontSize = 14.sp, maxLines = 2)
            }
        }

        replies.forEach { reply ->
            ListItem(
                headlineContent = { Text(reply.text, fontSize = 14.sp, maxLines = 3) },
                supportingContent = {
                    Text("${reply.senderName} • ${formatTime(reply.timestamp)}", fontSize = 11.sp)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(onClick = { onReplyClick(parentMessage.id) }) {
            Text("💬 Ответить", fontSize = 13.sp)
        }
    }
}

private fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.forLanguageTag("ru"))
    return sdf.format(java.util.Date(ts))
}
