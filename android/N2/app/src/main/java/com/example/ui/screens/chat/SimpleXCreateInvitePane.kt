package com.example.ui.screens.chat

import com.example.ui.GameViewModel
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleXCreateInvitePane(
    viewModel: GameViewModel,
    lang: String,
    context: Context,
    onMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (lang == "RU") "\uD83D\uDD17 ГЕНЕРАТОР ПРИГЛАШЕНИЙ" else "\uD83D\uDD17 INVITATION GENERATOR",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
        
        var selectedInviteType by remember { mutableStateOf("ONE_TIME") }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (lang == "RU") "Выберите тип соединения:" else "Select connection type:", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                
                val options = listOf(
                    Triple("ONE_TIME", if (lang == "RU") "Одноразовая ссылка" else "One-time secure link", if (lang == "RU") "Идеально для анонимного подключения. Удаляется после активации." else "Ideal for anonymous 1-on-1 connection. Deleted after activation."),
                    Triple("LONG_TERM", if (lang == "RU") "Долгосрочный адрес контакта" else "Long-term contact address", if (lang == "RU") "Позволяет нескольким игрокам слать запросы на контакт." else "Allows multiple users to send contact requests."),
                    Triple("GROUP", if (lang == "RU") "Групповой секретный канал" else "Group secret channel", if (lang == "RU") "Для создания защищенных чатов на несколько человек." else "For creating secure chats with multiple people."),
                    Triple("CHANNEL", if (lang == "RU") "Информационный новостной канал" else "Info News Channel", if (lang == "RU") "Канал вещания, где писать новости может только автор." else "Broadcast feed channel where only the owner can post updates.")
                )
                
                options.forEach { (type, title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedInviteType == type) Color(0xFF00E676).copy(alpha = 0.08f) else Color.Transparent)
                            .border(1.dp, if (selectedInviteType == type) Color(0xFF00E676).copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedInviteType = type }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(
                            selected = selectedInviteType == type,
                            onClick = { selectedInviteType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E676))
                        )
                        Column {
                            Text(title, color = if (selectedInviteType == type) Color(0xFF00E676) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(desc, color = Color.LightGray, fontSize = 10.sp, lineHeight = 12.sp)
                        }
                    }
                }
                
                Button(
                    onClick = {
                        viewModel.generateSimpleXInvitation(selectedInviteType)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (lang == "RU") "СГЕНЕРИРОВАТЬ ПРИГЛАШЕНИЕ ⚙️" else "GENERATE INVITATION ⚙️", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if (viewModel.generatedInvitationLink.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.5.dp, Color(0xFF00E676).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = when(viewModel.generatedInvitationType) {
                            "ONE_TIME" -> if (lang == "RU") "\uD83D\uDD12 ОДНОРАЗОВЫЙ КЛЮЧ ГОТОВ" else "\uD83D\uDD12 ONE-TIME KEY SECURED"
                            "LONG_TERM" -> if (lang == "RU") "\uD83D\uDCE1 ДОЛГОСРОЧНЫЙ АДРЕС СОЗДАН" else "\uD83D\uDCE1 LONG-TERM ADDRESS CREATED"
                            else -> if (lang == "RU") "\uD83D\uDCE2 ССЫЛКА НА ГРУППУ ГОТОВА" else "\uD83D\uDCE2 GROUP INVITATION LINK READY"
                        },
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val matrix = viewModel.generatedInvitationQrMatrix
                    if (matrix != null) {
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Canvas(
                                modifier = Modifier.size(130.dp)
                            ) {
                                val sizeInUnits = matrix.size
                                val blockSizeX = size.width / sizeInUnits
                                val blockSizeY = size.height / sizeInUnits
                                
                                val exclusionStart = (sizeInUnits * 0.35f).toInt()
                                val exclusionEnd = (sizeInUnits * 0.65f).toInt()

                                for (r in matrix.indices) {
                                    for (c in matrix[r].indices) {
                                        val inExclusionZone = r in exclusionStart..exclusionEnd && c in exclusionStart..exclusionEnd
                                        if (matrix[r][c] && !inExclusionZone) {
                                            drawRect(
                                                color = Color.Black,
                                                topLeft = Offset(c * blockSizeX, r * blockSizeY),
                                                size = Size(blockSizeX + 0.5f, blockSizeY + 0.5f)
                                            )
                                        }
                                    }
                                }

                                val logoPercent = 0.34f
                                val logoSize = size.width * logoPercent
                                val logoLeft = (size.width - logoSize) / 2f
                                val logoTop = (size.height - logoSize) / 2f
                                
                                drawRoundRect(
                                    color = Color(0xFF10151B),
                                    topLeft = Offset(logoLeft, logoTop),
                                    size = Size(logoSize, logoSize),
                                    cornerRadius = CornerRadius(logoSize * 0.25f)
                                )
                                
                                drawRoundRect(
                                    color = Color(0xFF00E676),
                                    topLeft = Offset(logoLeft, logoTop),
                                    size = Size(logoSize, logoSize),
                                    cornerRadius = CornerRadius(logoSize * 0.25f),
                                    style = Stroke(width = 1.5f * density)
                                 )
                                  
                                val pStartX = logoLeft + logoSize * 0.26f
                                val pEndX = logoLeft + logoSize * 0.44f
                                val pTopY = logoTop + logoSize * 0.30f
                                val pMiddleY = logoTop + logoSize * 0.52f
                                val pBottomY = logoTop + logoSize * 0.72f
                                
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pStartX, pTopY),
                                    end = Offset(pStartX, pBottomY),
                                    strokeWidth = 2f * density,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pStartX, pTopY),
                                    end = Offset(pEndX, pTopY),
                                    strokeWidth = 2f * density,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pEndX, pTopY),
                                    end = Offset(pEndX, pMiddleY),
                                    strokeWidth = 2f * density,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(pEndX, pMiddleY),
                                    end = Offset(pStartX, pMiddleY),
                                    strokeWidth = 2f * density,
                                    cap = StrokeCap.Round
                                )
                                
                                val xStartX = logoLeft + logoSize * 0.52f
                                val xEndX = logoLeft + logoSize * 0.76f
                                val xTopY = logoTop + logoSize * 0.32f
                                val xBottomY = logoTop + logoSize * 0.70f
                                
                                drawLine(
                                    color = Color(0xFF00E676),
                                    start = Offset(xStartX, xTopY),
                                    end = Offset(xEndX, xBottomY),
                                    strokeWidth = 2.5f * density,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = Color(0xFF00E676),
                                    start = Offset(xEndX, xTopY),
                                    end = Offset(xStartX, xBottomY),
                                    strokeWidth = 2.5f * density,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Invitation URI:", fontSize = 10.sp, color = Color.Gray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = viewModel.generatedInvitationLink,
                                fontSize = 10.sp,
                                color = Color(0xFF00FF99),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SimpleX Invitation URI", viewModel.generatedInvitationLink)
                                    clipboard.setPrimaryClip(clip)
                                    onMessage(if (lang == "RU") "Ссылка успешно скопирована!" else "Link successfully copied!")
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = if (lang == "RU") "Передайте её контакту безопасным способом. По соображениям приватности, метаданные соединения не регистрируются." else "Share it with your contact securely. For privacy reasons, connection metadata is not registered.",
                        color = Color.LightGray,
                        fontSize = 9.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}
