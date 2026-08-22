package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel

@Composable
fun SimpleXRelayConfigPane(
    viewModel: GameViewModel,
    lang: String,
    focusedFieldId: String?,
    onFocusFieldChange: (String?) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (lang == "RU") "⚙️ УПРАВЛЕНИЕ ДЕЦЕНТРАЛИЗОВАННЫМИ РЕЛЕЯМИ SIMPLEX" else "⚙️ MANAGE DECENTRALIZED SIMPLEX RELAYS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "RU") "📡 Сервер SMP (Simple Messaged Protocol)" else "📡 SMP Server Relay",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lang == "RU") "Используется для доставки текстовых сообщений между пирами."
                    else "Used for low-latency asynchronous text delivery between peers.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
                OutlinedTextField(
                    value = viewModel.customSmpServer,
                    onValueChange = { viewModel.updateSmpServer(it) },
                    readOnly = viewModel.isNoGameModeEnabled,
                    singleLine = false,
                    maxLines = 6,
                    placeholder = { Text("smp://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_smp") Color(0xFF00FF41) else Color(0xFF00E676),
                        unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_smp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            if (viewModel.isNoGameModeEnabled) onFocusFieldChange("custom_smp")
                        }
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "RU") "📁 Сервер XFTP (Encrypted File Transfer)" else "📁 XFTP File Streaming Relay",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lang == "RU") "Обеспечивает передачу медиафайлов и голосовых сообщений."
                    else "Handles media file transfers and cryptocontainer voice recordings.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
                OutlinedTextField(
                    value = viewModel.customXftpServer,
                    onValueChange = { viewModel.updateXftpServer(it) },
                    readOnly = viewModel.isNoGameModeEnabled,
                    singleLine = false,
                    maxLines = 6,
                    placeholder = { Text("xftp://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_xftp") Color(0xFF00FF41) else Color(0xFF00E676),
                        unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_xftp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            if (viewModel.isNoGameModeEnabled) onFocusFieldChange("custom_xftp")
                        }
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (lang == "RU") "🎥 TURN/STUN Релей (Голосовая и видеосвязь)" else "🎥 TURN/STUN Relay (Voice & Video)",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lang == "RU") "Необходим для обхода NAT и будущей P2P голосовой и видеосвязи."
                    else "Required for NAT traversal to support future peer-to-peer voice/video calling.",
                    fontSize = 9.5.sp,
                    color = Color.LightGray
                )
                OutlinedTextField(
                    value = viewModel.customTurnServer,
                    onValueChange = { viewModel.updateTurnServer(it) },
                    readOnly = viewModel.isNoGameModeEnabled,
                    singleLine = false,
                    maxLines = 6,
                    placeholder = { Text("stun:...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_turn") Color(0xFF00FF41) else Color(0xFF00E676),
                        unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "custom_turn") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clickable {
                            if (viewModel.isNoGameModeEnabled) onFocusFieldChange("custom_turn")
                        }
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF06090D)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.2f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") "🖥️ КОНСОЛЬ РЕЛЕЕВ" else "🖥️ RELAY STATUS SYSTEM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        fontFamily = FontFamily.Monospace
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00E676).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "ОК" else "OK",
                            fontSize = 8.sp,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = if (lang == "RU") {
                        "> smp-resolver: Инициализирован узел ${viewModel.customSmpServer}\n" +
                        "> xftp-engine: Связь активна [${viewModel.customXftpServer}]\n" +
                        "> call-signaling: Туннель WebRTC TURN готов."
                    } else {
                        "> smp-resolver: Upstream link active [${viewModel.customSmpServer}]\n" +
                        "> xftp-engine: Connected & pooling chunk feeds [${viewModel.customXftpServer}]\n" +
                        "> call-signaling: WebRTC TURN stream pipe standing by."
                    },
                    fontSize = 8.5.sp,
                    color = Color(0xFF00D254),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 11.sp
                )
            }
        }
    }
}
