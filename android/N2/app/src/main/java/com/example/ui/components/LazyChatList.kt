package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ShimmerEffect

/**
 * Модель сообщения чата.
 *
 * @param id уникальный идентификатор сообщения
 * @param text текст сообщения
 * @param isOutgoing признак исходящего сообщения
 * @param timestamp временная метка отправки/получения
 * @param isDeleted признак удаления сообщения
 * @param isSending признак отправки (пока не доставлено)
 * @param isFailed признак неудачной отправки
 * @param disappearingTimeLeft оставшееся время до самоуничтожения (сек), -1 если не установлено
 */
data class ChatMessage(
    val id: String,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long,
    val isDeleted: Boolean = false,
    val isSending: Boolean = false,
    val isFailed: Boolean = false,
    val disappearingTimeLeft: Int = -1
)

/**
 * Виртуализированный список сообщений чата (LazyColumn).
 * Отображает пустой placeholder, если сообщений нет.
 *
 * @param messages список сообщений для отображения
 * @param onMessageClick вызывается при нажатии на сообщение
 * @param modifier модификатор компонента
 */
@Composable
fun LazyChatList(
    messages: List<ChatMessage>,
    onMessageClick: (ChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Если сообщений нет — показываем заглушку
    if (messages.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No messages. Start a secure session.",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
        return
    }

    // Список с анимацией элементов при добавлении/удалении
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            Box(Modifier.animateItem()) {
                ChatMessageItem(
                    message = message,
                    onClick = { onMessageClick(message) }
                )
            }
        }
    }
}

/**
 * Визуальное представление одного сообщения в списке.
 * Цвет фона и скругления зависят от того, исходящее ли сообщение.
 * Удалённые сообщения отображаются особым образом.
 */
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    onClick: () -> Unit
) {
    val isOutgoing = message.isOutgoing
    // Выбор цвета фона: красноватый для удалённых, зелёный для исходящих, тёмно-серый для входящих
    val bgColor = if (message.isDeleted) {
        Color(0xFF2C1E1E)
    } else if (isOutgoing) {
        Color(0xFF0D533A)
    } else {
        Color(0xFF222831)
    }

    // Строка с выравниванием в зависимости от отправителя
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        // Карточка сообщения: цвет, обводка при удалении, скруглённые углы
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
            // Максимальная ширина пузырька; клик доступен только для не удалённых сообщений
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    if (!message.isDeleted) {
                        Modifier.clickable { onClick() }
                    } else Modifier
                )
        ) {
            // Внутреннее содержимое пузырька
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Удалённое сообщение: иконка "закрыть" + текст
                if (message.isDeleted) {
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
                // Обычное (не удалённое) сообщение — просто текст
                } else {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }

                // Нижняя строка с таймером самоуничтожения, временем и статусом
                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Индикатор самоуничтожения, если таймер активен
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

                    // Галочка отправки для исходящих сообщений
                    if (!message.isDeleted && isOutgoing) {
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

/**
 * Заглушка-плейсхолдер для сообщения с эффектом шиммера (мерцания).
 * Используется при загрузке истории сообщений.
 *
 * @param modifier модификатор компонента
 */
@Composable
fun MessagePlaceholder(modifier: Modifier = Modifier) {
    ShimmerEffect(
        modifier = modifier
            .height(60.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    )
}
