package com.example.ui.screens.calls

import androidx.compose.foundation.background
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

enum class CallState { CALLING, RINGING, CONNECTED, ENDED }

@Composable
fun CallScreen(
    contactName: String = "",
    callState: CallState = CallState.CALLING,
    isIncoming: Boolean = false,
    callDurationSeconds: Int = 0,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onEnd: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false,
    onBack: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1a1a2e)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(96.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contactName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(contactName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))

        Text(
            text = when (callState) {
                CallState.CALLING -> "Вызов..."
                CallState.RINGING -> "Входящий вызов"
                CallState.CONNECTED -> "${callDurationSeconds / 60}:${String.format("%02d", callDurationSeconds % 60)}"
                CallState.ENDED -> "Вызов завершён"
            },
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            IconButton(onClick = onToggleMute) {
                Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = Color.DarkGray) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isMuted) "\uD83D\uDD07" else "\uD83D\uDD0A", fontSize = 24.sp)
                    }
                }
            }
            IconButton(onClick = onToggleSpeaker) {
                Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = Color.DarkGray) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isSpeakerOn) "\uD83D\uDD0A" else "\uD83D\uDD0A", fontSize = 24.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (isIncoming && callState == CallState.RINGING) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.size(64.dp)
                ) { Text("\u260E", fontSize = 28.sp) }
                Button(
                    onClick = onDecline,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.size(64.dp)
                ) { Text("\u2716", fontSize = 28.sp) }
            }
        } else if (callState == CallState.CONNECTED || callState == CallState.CALLING) {
            Button(
                onClick = onEnd,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                modifier = Modifier.size(64.dp)
            ) { Text("\u260E", fontSize = 28.sp) }
        } else if (callState == CallState.ENDED) {
            Button(onClick = onBack) {
                Text("Закрыть")
            }
        }
    }
}
