package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Пузырёк сообщения чата.
 * Отображает текст сообщения, статус (отправляется, не удалось отправить, удалено),
 * таймер самоуничтожения и отметку об отправке.
 *
 * @param message данные сообщения
 * @param onResend колбэк повторной отправки при ошибке
 * @param onDelete колбэк удаления сообщения
 */
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onResend: () -> Unit,
    onDelete: () -> Unit,
    isTyping: Boolean = false
) {
    // Определяем, исходящее ли сообщение, и выбираем цвет фона
    val isOutgoing = message.isOutgoing
    val bgColor = if (message.isDeleted) {
        Color(0xFF2C1E1E)
    } else if (isOutgoing) {
        Color(0xFF0D533A)
    } else {
        Color(0xFF222831)
    }

    // Строка-контейнер, выравнивающая пузырёк вправо (исходящие) или влево (входящие)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        // Карточка сообщения с закруглениями: у исходящих скруглён низ слева, у входящих — справа
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            border = if (message.isDeleted) {
                androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4D4D))
            } else null,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isOutgoing) 12.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 12.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize()
        ) {
            // Внутреннее содержимое пузырька: текст, статус, мета-информация
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Индикатор набора текста — 3 анимированные точки
                if (isTyping) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "...",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                } else if (message.isDeleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Deleted",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "[MESSAGE DELETED]",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                // Если сообщение не удалось отправить — показываем иконку ошибки и кнопку повтора
                } else if (message.isFailed) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Failed",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = message.text,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Text(
                            text = "Tap to retry",
                            color = Color(0xFFFF5252),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onResend() }
                        )
                    }
                // Если сообщение ещё отправляется — полупрозрачный текст + индикатор
                } else if (message.isSending) {
                    Column {
                        Text(
                            text = message.text,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = "Sending...",
                            color = Color(0xFFFFA726),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }

                // Нижняя строка: таймер самоуничтожения, метка времени, иконка отправки
                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Отображаем оставшееся время до самоуничтожения
                    if (message.disappearingTimeLeft > 0) {
                        Text(
                            text = "⏳ ${message.disappearingTimeLeft}s",
                            color = Color(0xFFFFA726),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val timestampText = formatTimestamp(message.timestamp)
                    Text(
                        text = timestampText,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 8.sp
                    )

                    // Галочка об успешной отправке (только для исходящих, не удалённых)
                    if (!message.isDeleted && !message.isSending && !message.isFailed && isOutgoing) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Sent",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Форматирует timestamp в строку "HH:mm".
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
