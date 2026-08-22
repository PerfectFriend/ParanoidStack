package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val quickReactions = listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE00", "\uD83D\uDE22", "\uD83D\uDE21", "\uD83D\uDC4F")

@Composable
fun ReactionPicker(
    messageId: Long,
    onReact: (Long, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(4.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            quickReactions.forEach { emoji ->
                FilledIconButton(
                    onClick = {
                        onReact(messageId, emoji)
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(emoji, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun ReactionBadge(
    emoji: String,
    count: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(end = 4.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 12.sp)
            if (count > 1) {
                Spacer(Modifier.width(2.dp))
                Text("$count", fontSize = 10.sp)
            }
        }
    }
}
