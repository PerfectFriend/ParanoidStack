package com.example.ui.screens.chat

import com.example.ui.components.BackgammonBoardContainer
import com.example.ui.components.DiceCube
import com.example.ui.components.CheckerPiece
import com.example.ui.components.QrCodeScannerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.MatchHistory
import com.example.data.Bip39Helper
import com.example.model.CheckerStack
import com.example.model.GameStatus
import com.example.model.Player
import com.example.model.GameLogEntry
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

import com.example.ui.components.MatrixStyleKeyboard
import com.example.ui.components.MatrixKey
import com.example.ui.screens.Language
import com.example.ui.screens.CryptocontainerSetupWizard
import com.example.ui.GameViewModel
import com.example.ui.screens.chat.SimpleXRelayConfigPane
import com.example.ui.screens.chat.SimpleXContactsPane
import com.example.ui.screens.chat.SimpleXCreateInvitePane

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SimpleXFullScreenChat(
    viewModel: GameViewModel,
    initialTabSegment: Int = 0,
    onDismiss: () -> Unit
) {
    val lang = viewModel.selectedChatLanguage
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vpnManager = viewModel.vpnManager

    // Control portrait mode inside the chat, then return to previous orientation when dismissed
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (viewModel.isNoGameModeEnabled) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (!viewModel.showWelcomeScreen) {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // Auto dismiss chat screen if online game starts from challenge
    LaunchedEffect(viewModel.challengeGameStartedTrigger) {
        if (viewModel.challengeGameStartedTrigger) {
            onDismiss()
            viewModel.challengeGameStartedTrigger = false
        }
    }

    var activeChatRoom by remember { mutableStateOf<String?>(null) } // null = Chat list, "BOT_CHAT", "DEV_CHAT", "LOBBY_CHAT"
    var activeTabSegment by remember { mutableStateOf(initialTabSegment) }
    var chatSubSegment by remember { mutableStateOf(0) } // 0 = Chats, 1 = Contacts, 2 = Create Invite, 3 = Join Chat
    val localKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var showReactionTrayForMsgId by remember { mutableStateOf<String?>(null) }
    var focusedFieldId by remember { mutableStateOf<String?>(null) }
    
    // Camera / scanning states
    var showCameraView by remember { mutableStateOf(false) }
    var isCameraScanning by remember { mutableStateOf(false) }
    var cameraScanResult by remember { mutableStateOf("") }
    var scanningServerType by remember { mutableStateOf("SMP") } // SMP or XFTP

    var vpnFileAlert by remember { mutableStateOf("") }
    var showVpnTextImportDialog by remember { mutableStateOf(false) }

    // Backup & Restore settings state vars
    var showBackupPinVerifyDialog by remember { mutableStateOf(false) }
    var backupActionType by remember { mutableStateOf("EXPORT") } // EXPORT or IMPORT
    var backupPinError by remember { mutableStateOf("") }
    var showExportResultDialog by remember { mutableStateOf(false) }
    var showImportInputDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    var showSaveCryptoKeyDialog by remember { mutableStateOf(false) }
    var exportedSeedPhraseText by remember { mutableStateOf("") }
    var exportedContainerText by remember { mutableStateOf("") }
    
    val vpnConfigImportLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val res = viewModel.vpnManager.importConfigFromFile(it)
            vpnFileAlert = res
        }
    }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            vpnManager.startVpn()
        }
    }

    LaunchedEffect(viewModel.showVpnPermissionRequest) {
        viewModel.showVpnPermissionRequest?.let { intent ->
            vpnPrepareLauncher.launch(intent)
            viewModel.showVpnPermissionRequest = null
        }
    }

    LaunchedEffect(focusedFieldId) {
        if (focusedFieldId != null && viewModel.isNoGameModeEnabled) {
            localKeyboard?.hide()
        }
    }

    if (!viewModel.isCryptocontainerMounted) {
        CryptocontainerSetupWizard(viewModel = viewModel, onDismiss = onDismiss)
        return
    }

    // Master Layout Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF0F1216))
            .clickable(enabled = false) {} // block clickthrough
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (activeChatRoom == null) {
                    // --- LARGER DRAWN SERVICE STATUS INDICATORS ---
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .padding(end = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val services = listOf(
                            Pair("V2RAY", viewModel.vpnManager.vpnState == "Connected"),
                            Pair("VPN", viewModel.vpnManager.vpnState == "Connected"),
                            Pair("TOR", viewModel.isTorEnabled && viewModel.torStatus == "ACTIVE"),
                            Pair("SMP", viewModel.v2RayTorSyncStatus == "SYNCED"),
                            Pair("XFTP", viewModel.v2RayTorSyncStatus == "SYNCED")
                        )

                        services.forEach { (label, isActive) ->
                            val colorSchemeColor = if (isActive) Color(0xFF00FF41) else Color.Gray
                            val bgAlpha = if (isActive) 0.12f else 0.05f
                            
                            Box(
                                modifier = Modifier
                                    .background(colorSchemeColor.copy(alpha = bgAlpha), RoundedCornerShape(6.dp))
                                    .border(1.dp, colorSchemeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isActive) Color(0xFF00FF41) else Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                } else {
                    // Traditional active chat room header (back button, room title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { activeChatRoom = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (lang == "RU") "Назад" else "Back",
                                tint = Color.White
                            )
                        }

                        Column {
                            val matchedRoom = viewModel.simplexRooms.firstOrNull { it.id == activeChatRoom }
                            val roomTitle = matchedRoom?.title ?: when (activeChatRoom) {
                                "BOT_CHAT" -> if (lang == "RU") "🤖 Зарик Бот (AI)" else "🤖 Zaric Bot (AI)"
                                "DEV_CHAT" -> "📢 Crazy Backgammon P2P Devs"
                                else -> if (lang == "RU") "📡 Сетевой Матчмейкинг" else "📡 Network Matchmaking"
                            }
                            Text(
                                text = roomTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (lang == "RU") "Шифрованное P2P соединение" else "Encrypted P2P connection",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Actions Button - Red Exit & System Clear Button
                Button(
                    onClick = {
                        viewModel.performHardExit(context as? android.app.Activity)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (lang == "RU") "ВЫХОД" else "EXIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // --- TAB NAV SEGMENTS (Only visible when not inside an active chat window) ---
            if (activeChatRoom == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12161D))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        if (lang == "RU") "💬 Чаты" else "💬 Chats",
                        if (lang == "RU") "🔧 Сеть" else "🔧 Network"
                    ).forEachIndexed { index, title ->
                        val isSel = activeTabSegment == index
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isSel) 1.dp else 0.dp,
                                    color = if (isSel) Color(0xFF00E676).copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { activeTabSegment = index }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSel) Color(0xFF00E676) else Color.LightGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // --- MAIN VIEWPORT BODY ---
            val chatRoom = activeChatRoom
            if (chatRoom != null) {
                // --- ACTIVE CHAT WINDOW VIEW ---
                val currentRoom = chatRoom
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0F12))
                        .padding(10.dp)
                ) {
                    
                    // Chat Control Option Bar with Burn Settings and Room Types
                    val roomObj = viewModel.simplexRooms.firstOrNull { it.id == currentRoom }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B22), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (lang == "RU") "Двойной Рэтчет E2EE" else "Double Ratchet E2EE",
                                    fontSize = 11.sp, 
                                    color = Color.LightGray, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Room badge & Self destruct toggle button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val roomTypeLabel = when(roomObj?.roomType) {
                                    "INFO_CHANNEL" -> if (lang == "RU") "Канал 📢" else "Channel 📢"
                                    "GROUP_CHAT" -> if (lang == "RU") "Группа 👥" else "Group 👥"
                                    else -> if (lang == "RU") "Личный 👤" else "Direct 👤"
                                }
                                val roomTypeColor = when(roomObj?.roomType) {
                                    "INFO_CHANNEL" -> Color(0xFF29B6F6)
                                    "GROUP_CHAT" -> Color(0xFFAB47BC)
                                    else -> Color(0xFF00E676)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(roomTypeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(roomTypeLabel, color = roomTypeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }

                                // Interactive shredder auto-destruct delay
                                val currentTimer = roomObj?.selfDestructTimerSec ?: 0
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (currentTimer > 0) Color(0xFFFF3333).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f))
                                        .border(0.5.dp, if (currentTimer > 0) Color(0xFFFF3333).copy(alpha = 0.4f) else Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .clickable {
                                            if (roomObj != null) {
                                                roomObj.selfDestructTimerSec = when(currentTimer) {
                                                    0 -> 10
                                                    10 -> 30
                                                    30 -> 60
                                                    60 -> 300
                                                    else -> 0
                                                }
                                                // Log action in a beautiful way
                                                val addedSecs = roomObj.selfDestructTimerSec
                                                val alertText = if (addedSecs > 0) {
                                                    if (lang == "RU") "⏱️ Автоуничтожение сообщений установлено на $addedSecs сек." 
                                                    else "⏱️ Auto-shred timer set to $addedSecs seconds."
                                                } else {
                                                    if (lang == "RU") "⏱️ Автоуничтожение сообщений отключено." 
                                                    else "⏱️ Auto-shred timer disabled."
                                                }
                                                roomObj.messages.add(
                                                    GameViewModel.ChatMessage(
                                                        "sys",
                                                        "System",
                                                        alertText,
                                                        System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("🔥", fontSize = 9.sp)
                                        Text(
                                            text = if (currentTimer > 0) "${currentTimer}s" else "BURN: OFF", 
                                            color = if (currentTimer > 0) Color(0xFFFF5252) else Color.Gray, 
                                            fontSize = 9.sp, 
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                        
                        Button(
                            onClick = {
                                viewModel.sendGameChallengeInvite(currentRoom)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB300),
                                contentColor = Color.Black
                              ),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "ИГРАТЬ ⚔️" else "PLAY ⚔️", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    // Messages List Pane
                    val messagesList = roomObj?.messages ?: remember { mutableStateListOf() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        if (messagesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (lang == "RU") "Нет сообщений. Начните защищенную сессию." else "No messages. Start a secure session.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(messagesList) { m ->
                                    val isMe = m.sender == viewModel.simplexUserHandle
                                    val isSys = m.id == "sys" || m.sender == "System" || m.sender == "SimpleX System"
                                    val isChallenge = m.text.contains("⚔️ ВЫЗОВ НА ИГРУ")

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isSys) Arrangement.Center else if (isMe) Arrangement.End else Arrangement.Start
                                    ) {
                                        if (isSys) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFF1E252D), RoundedCornerShape(8.dp))
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(m.text, color = Color(0xFF00E676), fontSize = 12.sp, textAlign = TextAlign.Center)
                                            }
                                        } else if (isChallenge) {
                                            // Golden Interactive Challenge Card
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF221C0E)),
                                                border = BorderStroke(1.5.dp, Color(0xFFFFB300)),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.widthIn(max = 290.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFFFB300)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("⚔️", fontSize = 11.sp, color = Color.Black)
                                                        }
                                                        Text(if (lang == "RU") "ОНЛАЙН ДУЭЛЬ" else "ONLINE DUEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB300))
                                                    }
                                                    
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    
                                                    Text(
                                                        text = m.text,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        lineHeight = 16.sp
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                viewModel.startChallengeGame(m.sender)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                                            contentPadding = PaddingValues(0.dp),
                                                            modifier = Modifier.weight(1.2f).height(32.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ПРИНЯТЬ" else "ACCEPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                        }
                                                        
                                                        OutlinedButton(
                                                            onClick = {
                                                                messagesList.remove(m)
                                                            },
                                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                                            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                                                            contentPadding = PaddingValues(0.dp),
                                                            modifier = Modifier.weight(0.8f).height(32.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ОТКЛОНИТЬ" else "DECLINE", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                             // Legible speech bubble
                                             Column(
                                                 horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                                                 modifier = Modifier.widthIn(max = 280.dp)
                                             ) {
                                                 Card(
                                                     colors = CardDefaults.cardColors(
                                                         containerColor = if (m.isSelfDestructed) Color(0xFF2C1E1E) else if (isMe) Color(0xFF0D533A) else Color(0xFF222831)
                                                     ),
                                                     border = if (m.isSelfDestructed) BorderStroke(1.dp, Color(0xFFFF4D4D)) else null,
                                                     shape = RoundedCornerShape(
                                                         topStart = 12.dp,
                                                         topEnd = 12.dp,
                                                         bottomStart = if (isMe) 12.dp else 2.dp,
                                                         bottomEnd = if (isMe) 2.dp else 12.dp
                                                     ),
                                                     modifier = Modifier
                                                         .clickable {
                                                             showReactionTrayForMsgId = if (showReactionTrayForMsgId == m.id) null else m.id
                                                         }
                                                 ) {
                                                     Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                         if (!isMe) {
                                                             Text(m.sender, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                         }
                                                         
                                                         if (m.isSelfDestructed) {
                                                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                 Text("🔥", fontSize = 14.sp)
                                                                 Text(
                                                                     text = if (lang == "RU") "[СООБЩЕНИЕ УНИЧТОЖЕНО]" else "[MESSAGE SHREDDED]",
                                                                     color = Color(0xFFFF5252),
                                                                     fontSize = 11.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                                 )
                                                             }
                                                         } else {
                                                             // Display custom attachments if active
                                                             if (m.attachmentType == "IMAGE") {
                                                                 Card(
                                                                     colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                                                     border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f)),
                                                                     shape = RoundedCornerShape(8.dp),
                                                                     modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                                                 ) {
                                                                     Column(
                                                                         modifier = Modifier.padding(8.dp),
                                                                         horizontalAlignment = Alignment.CenterHorizontally
                                                                     ) {
                                                                         Icon(
                                                                             imageVector = Icons.Default.Place, 
                                                                             contentDescription = null, 
                                                                             tint = Color(0xFF00E676), 
                                                                             modifier = Modifier.size(34.dp)
                                                                         )
                                                                         Spacer(modifier = Modifier.height(4.dp))
                                                                         Text(
                                                                             text = if (lang == "RU") "ЗАШИФРОВАННЫЕ ДАННЫЕ ИЗОБРАЖЕНИЯ" else "ENCRYPTED IMAGE DATA",
                                                                             color = Color.White,
                                                                             fontSize = 10.sp, 
                                                                             fontWeight = FontWeight.Bold
                                                                         )
                                                                         Text(
                                                                             text = "SHA-256 E2E Metadata Striped", 
                                                                             color = Color.Gray, 
                                                                             fontSize = 8.sp
                                                                         )
                                                                     }
                                                                 }
                                                             } else if (m.attachmentType == "FILE") {
                                                                 Row(
                                                                     verticalAlignment = Alignment.CenterVertically, 
                                                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                     modifier = Modifier
                                                                         .fillMaxWidth()
                                                                         .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                                         .padding(8.dp)
                                                                 ) {
                                                                     Icon(Icons.Default.Build, tint = Color(0xFFAB47BC), contentDescription = null, modifier = Modifier.size(18.dp))
                                                                     Column {
                                                                         Text(m.attachmentName, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                                         Text(m.attachmentSize, color = Color.Gray, fontSize = 9.sp)
                                                                     }
                                                                 }
                                                             } else if (m.attachmentType == "AUDIO") {
                                                                 Row(
                                                                     verticalAlignment = Alignment.CenterVertically, 
                                                                     horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                     modifier = Modifier
                                                                         .fillMaxWidth()
                                                                         .background(Color(0xFF14241B), RoundedCornerShape(6.dp))
                                                                         .padding(8.dp)
                                                                 ) {
                                                                     Icon(Icons.Default.PlayArrow, tint = Color(0xFF00E676), contentDescription = null, modifier = Modifier.size(20.dp))
                                                                     Column(modifier = Modifier.weight(1f)) {
                                                                         Text(if (lang == "RU") "Защищенное аудио" else "Secure Audio Note", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                         Text("Duration: 14s (E2EE PCM)", color = Color.Gray, fontSize = 9.sp)
                                                                     }
                                                                 }
                                                             }

                                                             Text(
                                                                 text = m.text,
                                                                 color = Color.White,
                                                                 fontSize = 14.sp,
                                                                 lineHeight = 18.sp
                                                             )
                                                         }
                                                         
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Row(
                                                             modifier = Modifier.align(Alignment.End),
                                                             verticalAlignment = Alignment.CenterVertically,
                                                             horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                         ) {
                                                             if (!m.isSelfDestructed && m.selfDestructTimeLeft > 0) {
                                                                 Text(
                                                                     text = "🔥 ${m.selfDestructTimeLeft}s",
                                                                     color = Color(0xFFFFA726),
                                                                     fontSize = 9.sp,
                                                                     fontWeight = FontWeight.Bold
                                                                 )
                                                             }
                                                             Text("E2EE ", fontSize = 8.sp, color = Color.White.copy(alpha = 0.4f))
                                                             Icon(
                                                                 imageVector = if (m.isSelfDestructed) Icons.Default.Close else Icons.Default.Check, 
                                                                 contentDescription = null, 
                                                                 tint = if (m.isSelfDestructed) Color.Red else Color(0xFF00E676), 
                                                                 modifier = Modifier.size(10.dp)
                                                             )
                                                         }
                                                     }
                                                 }

                                                 // Dynamic Emoji Reactions Badges
                                                 if (m.reactions.isNotEmpty()) {
                                                     Spacer(modifier = Modifier.height(2.dp))
                                                     Row(
                                                         horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                         modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start)
                                                     ) {
                                                         m.reactions.forEach { reactionEmoji ->
                                                             Box(
                                                                 modifier = Modifier
                                                                     .background(Color(0xFF1E252D), RoundedCornerShape(10.dp))
                                                                     .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                                     .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                     .clickable {
                                                                         viewModel.addSimpleXReaction(currentRoom, m.id, reactionEmoji)
                                                                     }
                                                             ) {
                                                                 Text(reactionEmoji, fontSize = 10.sp)
                                                             }
                                                         }
                                                     }
                                                 }

                                                 // Interactive Emoji Tray
                                                 if (showReactionTrayForMsgId == m.id && !m.isSelfDestructed) {
                                                     Spacer(modifier = Modifier.height(4.dp))
                                                     Row(
                                                         horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                         modifier = Modifier
                                                             .background(Color(0xFF161B22), RoundedCornerShape(20.dp))
                                                             .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                                             .padding(horizontal = 10.dp, vertical = 4.dp)
                                                             .align(if (isMe) Alignment.End else Alignment.Start)
                                                     ) {
                                                         listOf("👍", "🔥", "🔐", "🧅", "💬", "👎").forEach { emoji ->
                                                             Box(
                                                                 modifier = Modifier
                                                                     .padding(2.dp)
                                                                     .clickable {
                                                                         viewModel.addSimpleXReaction(currentRoom, m.id, emoji)
                                                                         showReactionTrayForMsgId = null
                                                                     }
                                                             ) {
                                                                 Text(emoji, fontSize = 14.sp)
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                        }
                                    }
                                }
                            }
                        }

                    // Suggesters for Bot Helper
                    if (currentRoom == "BOT_CHAT") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.sendSimpleXMessage("/joke") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(if (lang == "RU") "🤡 Анекдот бота" else "🤡 Bot Joke", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.sendSimpleXMessage("/roll")
                                    viewModel.playRollSound()
                                    val b1 = (1..6).random()
                                    val b2 = (1..6).random()
                                    viewModel.simplexMessages.add(
                                        GameViewModel.ChatMessage(
                                            java.util.UUID.randomUUID().toString(),
                                            if (lang == "RU") "🤖 Кости-Зарик" else "🤖 Zaric Dice Bot",
                                            if (lang == "RU") "Выпало [$b1 : $b2]! 🎲 Делайте ваши ставки!" else "Rolled [$b1 : $b2]! 🎲 Place your bets!",
                                            System.currentTimeMillis()
                                        )
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676)),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Text(if (lang == "RU") "🎲 Бросить кости" else "🎲 Roll Dice", fontSize = 11.sp)
                            }
                        }
                    }

                    // Input Row
                    var textInputMsg by remember { mutableStateOf("") }
                    var showAttachmentTray by remember { mutableStateOf(false) }
                    val showMatrixKeyboard = true
                    var cursorOn by remember { mutableStateOf(true) }
                    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(530)
                            cursorOn = !cursorOn
                        }
                    }

                    // Enforce absolute offline software keyboard suppression
                    LaunchedEffect(textInputMsg, activeChatRoom) {
                        keyboardController?.hide()
                    }

                     // Attachment Picker Panel
                    if (showAttachmentTray) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. IMAGE
                            Button(
                                onClick = {
                                    viewModel.sendSimpleXMessage(
                                        currentRoom,
                                        if (lang == "RU") "Шифрованное фото отправлено" else "Encrypted image transmitted",
                                        attachmentType = "IMAGE",
                                        attachmentUrl = "sec_img_ram.raw"
                                    )
                                    showAttachmentTray = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color(0xFF00E676)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("📷", fontSize = 12.sp)
                                    Text(if (lang == "RU") "ФОТО" else "PHOTO", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 2. FILE
                            Button(
                                onClick = {
                                    viewModel.sendSimpleXMessage(
                                        currentRoom,
                                        "paranoid_backup.sql",
                                        attachmentType = "FILE",
                                        attachmentUrl = "secure_backup.bin",
                                        attachmentName = "paranoid_backup.sql",
                                        attachmentSize = "45 KB"
                                    )
                                    showAttachmentTray = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color(0xFFAB47BC)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("📁", fontSize = 12.sp)
                                    Text(if (lang == "RU") "ФАЙЛ" else "FILE", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 3. AUDIO VOICE NOTE
                            Button(
                                onClick = {
                                    viewModel.sendSimpleXMessage(
                                        currentRoom,
                                        if (lang == "RU") "Голосовая заметка (E2EE PCM)" else "E2EE Voice Note PCM",
                                        attachmentType = "AUDIO",
                                        attachmentUrl = "voc_ram_3.raw"
                                    )
                                    showAttachmentTray = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color(0xFF29B6F6)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🎤", fontSize = 12.sp)
                                    Text(if (lang == "RU") "ГОЛОС" else "VOICE", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fontFam = FontFamily.Monospace
                        val textStyleColor = Color(0xFF00FF41)
                        val borderColor = Color(0xFF00FF41).copy(alpha = 0.5f)
                        val suffixText = if (cursorOn) "█" else ""

                        // Paperclip Button
                        IconButton(
                            onClick = { showAttachmentTray = !showAttachmentTray },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (showAttachmentTray) Color(0xFF00FF41).copy(alpha = 0.15f) else Color(0xFF06090D))
                                .border(1.dp, if (showAttachmentTray) Color(0xFF00FF41) else Color(0xFF00FF41).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            Text("📎", fontSize = 18.sp, color = Color(0xFF00FF41))
                        }

                        // Secure custom non-focusable display container instead of BasicTextField to avoid trigger of system IME/Gboard
                        val inputScrollState = androidx.compose.foundation.rememberScrollState()
                        LaunchedEffect(textInputMsg) {
                            inputScrollState.animateScrollTo(inputScrollState.maxValue)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .wrapContentHeight()
                                .heightIn(min = 48.dp, max = 96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF06090D))
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "> ",
                                    color = Color(0xFF00FF41),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 64.dp)
                                        .verticalScroll(inputScrollState)
                                        .align(Alignment.CenterVertically),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (textInputMsg.isEmpty()) {
                                        Text(
                                            text = if (currentRoom == "BOT_CHAT") {
                                                if (lang == "RU") "Спросите Зарика (/joke, /roll)..." 
                                                else "Ask Zaric (/joke, /roll)..."
                                            } else {
                                                if (lang == "RU") "Шифрованное E2EE-сообщение..." 
                                                else "Encrypted E2EE message..."
                                            }, 
                                            fontSize = 14.sp, 
                                            color = Color(0xFF00FF41).copy(alpha = 0.4f),
                                            fontFamily = fontFam
                                        )
                                    } else {
                                        Text(
                                            text = textInputMsg + suffixText,
                                            color = textStyleColor,
                                            fontFamily = fontFam,
                                            fontSize = 16.sp,
                                            softWrap = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Offline Secure Keyboard Key",
                                    tint = Color(0xFF00FF41),
                                    modifier = Modifier.size(18.dp).align(Alignment.CenterVertically)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (textInputMsg.isNotBlank()) {
                                    viewModel.sendSimpleXMessage(currentRoom, textInputMsg)
                                    textInputMsg = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF41)),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(width = 54.dp, height = 44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                    }

                    com.example.ui.components.MatrixStyleKeyboard(
                        onKey = { key ->
                            when (key) {
                                is com.example.ui.components.MatrixKey.CharKey -> {
                                    textInputMsg += key.text
                                }
                                com.example.ui.components.MatrixKey.Space -> {
                                    textInputMsg += " "
                                }
                                com.example.ui.components.MatrixKey.Backspace -> {
                                    if (textInputMsg.isNotEmpty()) {
                                        textInputMsg = textInputMsg.dropLast(1)
                                    }
                                }
                                com.example.ui.components.MatrixKey.Enter -> {
                                    if (textInputMsg.isNotBlank()) {
                                        viewModel.sendSimpleXMessage(currentRoom, textInputMsg)
                                        textInputMsg = ""
                                    }
                                }
                                com.example.ui.components.MatrixKey.Hide -> {
                                    keyboardController?.hide() // Does not hide custom keyboard, only ensures soft keyboard stays hidden
                                }
                                else -> {}
                            }
                        },
                        lang = lang
                    )
                }
            } else {
                // --- CHAT LIST TAB OR NETWORKS CONFIG PANE ---
                if (activeTabSegment == 0) {
                    // Sub tab segments row: horizontally scrollable, icon + short label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161E26))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    val subTabs = listOf(
                        Triple(if (lang == "RU") "💬" else "💬", if (lang == "RU") "Чаты" else "Chats", 0),
                        Triple(if (lang == "RU") "👥" else "👥", if (lang == "RU") "Контакты" else "Contacts", 1),
                        Triple(if (lang == "RU") "➕" else "➕", if (lang == "RU") "QR" else "QR", 2),
                        Triple(if (lang == "RU") "📥" else "📥", if (lang == "RU") "Войти" else "Join", 3),
                        Triple(if (lang == "RU") "⚙️" else "⚙️", if (lang == "RU") "Реле" else "Relays", 4)
                    )
                    subTabs.forEach { (icon, label, idx) ->
                        val isSel = chatSubSegment == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    width = if (isSel) 1.dp else 0.dp,
                                    color = if (isSel) Color(0xFF00E676) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { chatSubSegment = idx }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = icon,
                                fontSize = 22.sp,
                                maxLines = 1
                            )
                            Text(
                                text = label,
                                color = if (isSel) Color(0xFF00E676) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (chatSubSegment == 0) {
                    // --- SUB-TAB 0: LEGIBLE SECURE P2P CHATS ---
                    var selectedRoomFilter by remember { mutableStateOf("ALL") }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "💬 БЕЗОПАСНЫЕ КАНАЛЫ (Direct E2EE Mesh)" else "💬 SECURE CHANNELS (Direct E2EE Mesh)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Filter Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "ALL" to (if (lang == "RU") "Все" else "All"),
                                "DIRECT_CHAT" to (if (lang == "RU") "👤 Личные" else "👤 Direct"),
                                "GROUP_CHAT" to (if (lang == "RU") "👥 Группы" else "👥 Groups"),
                                "INFO_CHANNEL" to (if (lang == "RU") "📢 Каналы" else "📢 Channels")
                            ).forEach { (filterVal, filterTitle) ->
                                val isSel = selectedRoomFilter == filterVal
                                Box(
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.16f) else Color(0xFF1E252D))
                                        .border(1.dp, if (isSel) Color(0xFF00E676) else Color.Transparent, RoundedCornerShape(14.dp))
                                        .clickable { selectedRoomFilter = filterVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filterTitle,
                                        color = if (isSel) Color(0xFF00E676) else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val filteredRooms = viewModel.simplexRooms.filter { room ->
                            selectedRoomFilter == "ALL" || room.roomType == selectedRoomFilter
                        }

                        if (filteredRooms.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 30.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (lang == "RU") "Нет каналов выбранного типа." else "No channels of selected type.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Rich, beautiful dynamic chat list cards
                        filteredRooms.forEach { room ->
                            val id = room.id
                            val title = room.title
                            val lastMsg = room.lastMessage
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeChatRoom = id },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (id == "BOT_CHAT") Color(0xFF00E676) 
                                                else if (room.roomType == "INFO_CHANNEL") Color(0xFF29B6F6) 
                                                else if (room.roomType == "GROUP_CHAT") Color(0xFFAB47BC)
                                                else if (room.isOneTime) Color(0xFFFFB300)
                                                else Color(0xFF9E9E9E)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(title.filter { it.isLetter() }.take(2).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                        
                                        // Online status glowing point
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00FF66))
                                                .border(2.dp, Color(0xFF13181C), CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
 
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = when(room.roomType) {
                                                        "INFO_CHANNEL" -> if (lang == "RU") "КАНАЛ 📢" else "CHANNEL 📢"
                                                        "GROUP_CHAT" -> if (lang == "RU") "ГРУППА 👥" else "GROUP 👥"
                                                        else -> if (room.isOneTime) "1-TIME 🔒" else "DIRECT 👤"
                                                    },
                                                    color = when(room.roomType) {
                                                        "INFO_CHANNEL" -> Color(0xFF29B6F6)
                                                        "GROUP_CHAT" -> Color(0xFFAB47BC)
                                                        else -> if (room.isOneTime) Color(0xFFFFB300) else Color(0xFF00E676)
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(lastMsg, color = Color.LightGray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                } else if (chatSubSegment == 1) {
                    SimpleXContactsPane(
                        viewModel = viewModel,
                        lang = lang,
                        onOpenChat = { activeChatRoom = it }
                    )
                } else if (chatSubSegment == 2) {
                    SimpleXCreateInvitePane(
                        viewModel = viewModel,
                        lang = lang,
                        context = context,
                        onMessage = { cameraScanResult = it }
                    )
                } else if (chatSubSegment == 3) {
                        // --- SUB-TAB 3: ESTABLISH CONNECTION (JOIN) ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "📥 УСТАНОВЛЕНИЕ СОЕДИНЕНИЯ ПО QR И ССЫЛКАМ" else "📥 ESTABLISH CONNECTION VIA QR & LINKS",
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
                                    Text(if (lang == "RU") "Вставьте пригласительную ссылку:" else "Paste invitation link:", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    
                                    OutlinedTextField(
                                        value = viewModel.invitationInputText,
                                        onValueChange = { viewModel.invitationInputText = it },
                                        readOnly = viewModel.isNoGameModeEnabled,
                                        singleLine = false,
                                        maxLines = 6,
                                        placeholder = { Text("simplex://contact#id=... или https://...", fontSize = 12.sp, color = Color.LightGray.copy(alpha = 0.6f)) },
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray,
                                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "invitation") Color(0xFF00FF41) else Color(0xFF00E676),
                                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "invitation") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clickable {
                                                if (viewModel.isNoGameModeEnabled) focusedFieldId = "invitation"
                                            }
                                    )
                                    
                                    Button(
                                        onClick = {
                                            viewModel.connectToSimpleXInvitation(viewModel.invitationInputText) { msg ->
                                                cameraScanResult = msg
                                                viewModel.invitationInputText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "🔗 ПОДКЛЮЧИТЬСЯ ПО ССЫЛКЕ" else "🔗 CONNECT VIA LINK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            // QR CODE INSTANT LIVE SCANNER
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(if (lang == "RU") "📸 Быстрое сканирование QR-кода" else "📸 Instant QR Code Scanning", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(if (lang == "RU") "Используйте симулируемую камеру" else "Use simulated camera scanner", fontSize = 10.sp, color = Color.LightGray)
                                        }
                                        
                                        Button(
                                            onClick = {
                                                showCameraView = true
                                                isCameraScanning = true
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262C)),
                                            enabled = !showCameraView,
                                            modifier = Modifier.height(30.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Text(if (lang == "RU") "ОТКРЫТЬ КАМЕРУ" else "OPEN CAMERA", fontSize = 9.sp, color = Color.White)
                                        }
                                    }
                                    
                                    if (showCameraView) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black)
                                                .border(1.5.dp, Color(0xFF00E676), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCameraScanning) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    QrCodeScannerView(
                                                        onQrScanned = { activeUrl ->
                                                            viewModel.connectToSimpleXInvitation(activeUrl) { msg ->
                                                                cameraScanResult = msg
                                                            }
                                                            isCameraScanning = false
                                                            showCameraView = false
                                                        },
                                                        modifier = Modifier.fillMaxSize()
                                                    )

                                                    // Visual line overlay for scanning animation
                                                    val inf = rememberInfiniteTransition()
                                                    val lineY by inf.animateFloat(
                                                        initialValue = 0.1f,
                                                        targetValue = 0.9f,
                                                        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse)
                                                    )
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        drawLine(Color.Red, Offset(0f, size.height * lineY), Offset(size.width, size.height * lineY), 2.5.dp.toPx())
                                                    }

                                                    // Small test button floating on top for fallback / testing in environments without camera feeds
                                                    IconButton(
                                                        onClick = {
                                                            val seed = "smp-one-time-scanned-key-" + java.util.UUID.randomUUID().toString().take(6)
                                                            val simulatedLink = "simplex://contact#id=$seed&use=1"
                                                            viewModel.connectToSimpleXInvitation(simulatedLink) { msg ->
                                                                cameraScanResult = msg
                                                            }
                                                            isCameraScanning = false
                                                            showCameraView = false
                                                        },
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .padding(bottom = 6.dp)
                                                            .height(28.dp)
                                                            .background(Color(0xFF0D533A), RoundedCornerShape(12.dp))
                                                    ) {
                                                        Text(if (lang == "RU") "СИМУЛИРОВАТЬ ТЕСТ 🎯" else "SIMULATE TEST 🎯", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    Text(if (lang == "RU") "Предустановленные тестовые приглашения:" else "Preset test invitations:", fontSize = 10.sp, color = Color.Gray)
                                    val presets = listOf(
                                        Triple(
                                            if (lang == "RU") "Турнирный арбитр (Tournament Arbiter)" else "Tournament Arbiter (Referee)",
                                            "simplex://contact#id=smp-referee-handshake-99&use=multi",
                                            if (lang == "RU") "Рефери по нардам в защищенном канале" else "Backgammon referee in secure channel"
                                        ),
                                        Triple(
                                            if (lang == "RU") "Мастер Зариков (Nardy Champion)" else "Zaric Dice Master (Championship)",
                                            "simplex://contact#id=smp-champion-contact-01&use=1",
                                            if (lang == "RU") "Редкий вызов легендарного игрока" else "Rare challenge link with a legend"
                                        ),
                                        Triple(
                                            if (lang == "RU") "Закрытый лобби-чат (Backgammon Pro Lobby)" else "Pro Players Backgammon Lobby",
                                            "simplex://group#id=smp-pro-lobby-secret&title=ProLobby",
                                            if (lang == "RU") "Секретный групповой чат профессионалов" else "Private secure lobby with dynamic players"
                                        )
                                    )
                                    
                                    presets.forEach { (title, inviteUrl, desc) ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2228)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.connectToSimpleXInvitation(inviteUrl) { msg ->
                                                        cameraScanResult = msg
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("🔗", fontSize = 12.sp)
                                                }
                                                Column {
                                                    Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(desc, color = Color.LightGray, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Result Toast/Note
                            if (cameraScanResult.isNotEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF09291D)),
                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                        Text(cameraScanResult, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (chatSubSegment == 4) {
                        SimpleXRelayConfigPane(viewModel = viewModel, lang = lang, focusedFieldId = focusedFieldId, onFocusFieldChange = { focusedFieldId = it })
                    }
                } else {
                    // --- TAB 1: NETWORKS CONFIGURATION (REAL VPN, TOR & DYNAMIC ONIONS) ---
                    var activeNetworkSectionTab by remember { mutableStateOf(0) }
                    val networkTabs = listOf(
                        Triple(if (lang == "RU") "👤 Профиль" else "👤 Profile", "PROFILE", 0),
                        Triple(if (lang == "RU") "🛡️ Мост" else "🛡️ Bridge", "BRIDGE", 1),
                        Triple(if (lang == "RU") "📡 Onion" else "📡 Onion", "ONION", 2),
                        Triple(if (lang == "RU") "🦊 VPN" else "🦊 VPN", "VPN", 3),
                        Triple(if (lang == "RU") "🧅 Tor" else "🧅 Tor", "TOR", 4),
                        Triple(if (lang == "RU") "💾 Резерв" else "💾 Backup", "BACKUP", 5)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Horizontal Tab bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            networkTabs.forEach { (label, _, index) ->
                                val isSelected = activeNetworkSectionTab == index
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF13181C))
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { activeNetworkSectionTab = index }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFF00E676) else Color.LightGray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Scrollable content area
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (activeNetworkSectionTab == 0) {
                                // User Profile Handle Config
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (lang == "RU") "👤 Профиль сетевого клиента" else "👤 Network Client Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                
                                OutlinedTextField(
                                    value = viewModel.simplexUserHandle,
                                    onValueChange = { viewModel.updateSimplexHandle(it) },
                                    label = { Text(if (lang == "RU") "Имя контакта (Handle)" else "Contact Name (Handle)", fontSize = 11.sp, color = Color.LightGray) },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E676),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(if (lang == "RU") "Код приглашения Е2ЕЕ:" else "E2EE Invitation Code:", fontSize = 10.sp, color = Color.Gray)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("SimpleX URI", viewModel.simplexContactCode)
                                            clipboard.setPrimaryClip(clip)
                                            cameraScanResult = if (lang == "RU") "Код скопирован в буфер!" else "Code copied to clipboard!"
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = viewModel.simplexContactCode,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF00FF99),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = Language.get("connect_on_startup", lang),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = Language.get("connect_on_startup_desc", lang),
                                            color = Color.Gray,
                                            fontSize = 8.5.sp,
                                            lineHeight = 10.5.sp
                                        )
                                    }
                                    Switch(
                                        checked = viewModel.isConnectOnStartupEnabled,
                                        onCheckedChange = { viewModel.updateConnectOnStartup(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E676),
                                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f),
                                            uncheckedThumbColor = Color.LightGray,
                                            uncheckedTrackColor = Color.DarkGray
                                        )
                                    )
                                }

                                 Spacer(modifier = Modifier.height(6.dp))
                                 Row(
                                     modifier = Modifier.fillMaxWidth(),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Column(modifier = Modifier.weight(1f)) {
                                         Text(
                                             text = if (lang == "RU") "Режим 'Без игры'" else "'No Game' Mode",
                                             color = Color.White,
                                             fontSize = 11.sp,
                                             fontWeight = FontWeight.Bold
                                         )
                                         Text(
                                             text = if (lang == "RU") "При запуске игра заменяется экраном ввода ПИН-кода." else "Bypasses the game and runs E2EE chat pin screen directly.",
                                             color = Color.Gray,
                                             fontSize = 8.5.sp,
                                             lineHeight = 10.5.sp
                                         )
                                     }
                                     Switch(
                                         checked = viewModel.isNoGameModeEnabled,
                                         onCheckedChange = { viewModel.updateNoGameMode(it) },
                                         colors = SwitchDefaults.colors(
                                             checkedThumbColor = Color(0xFF00E676),
                                             checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f),
                                             uncheckedThumbColor = Color.LightGray,
                                             uncheckedTrackColor = Color.DarkGray
                                         )
                                     )
                                 }

                                 Spacer(modifier = Modifier.height(10.dp))
                                 Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
                                 Spacer(modifier = Modifier.height(8.dp))
                                 Column(modifier = Modifier.fillMaxWidth()) {
                                     Text(
                                         text = if (lang == "RU") "Локализация интерфейса чата" else if (lang == "ES") "Idioma del chat" else "Chat Interface Language",
                                         color = Color.White,
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold
                                     )
                                     Spacer(modifier = Modifier.height(6.dp))
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         listOf("EN" to "English", "ES" to "Español", "RU" to "Русский").forEach { (code, label) ->
                                             val isSelected = viewModel.selectedChatLanguage == code
                                             Box(
                                                 modifier = Modifier
                                                     .weight(1f)
                                                     .height(30.dp)
                                                     .clip(RoundedCornerShape(6.dp))
                                                     .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFF1B2228))
                                                     .border(
                                                         width = 1.dp,
                                                         color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                                                         shape = RoundedCornerShape(6.dp)
                                                     )
                                                     .clickable { viewModel.updateChatLanguage(code) },
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Text(
                                                     text = label,
                                                     color = if (isSelected) Color(0xFF00E676) else Color.LightGray,
                                                     fontSize = 10.sp,
                                                     fontWeight = FontWeight.Bold
                                                 )
                                             }
                                         }
                                     }
                                 }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 1) {
                        // NEW MASTER SYSTEM SYNC COMPONENT (FOXRAY VPN -> TOR -> SMP NODE SECURE TUNNEL!)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F25)),
                            border = BorderStroke(1.2.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (lang == "RU") "🛡️ Защищенный мост и синхронизация" else "🛡️ Secure Bridge & Sync", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Автоматическая цепь VPN (V2Ray / Tor) + Сетевой узел" else "Automated chain VPN (V2Ray / Tor) + Network Node", fontSize = 10.sp, color = Color.LightGray)
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00FF66)
                                                else if (viewModel.v2RayTorSyncStatus == "SYNCING") Color(0xFFFFB300)
                                                else Color.Gray
                                            )
                                    )
                                }

                                if (viewModel.v2RayTorSyncStatus != "NOT_SYNCED") {
                                    Text(
                                        text = if (lang == "RU") "Статус соединения: ${viewModel.v2RayTorSyncStatus}" else "Connection status: ${viewModel.v2RayTorSyncStatus}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00E676) else Color(0xFFFFB300)
                                    )
                                    
                                    // Custom Monospace sync terminal log screen
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.v2RayTorSyncLogs) { item ->
                                                Text(item, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                            }
                                        }
                                    }
                                }

                                val localKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                                Button(
                                    onClick = {
                                        localKeyboard?.hide()
                                        viewModel.connectAndSyncAllNetworkComponents()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF0D533A) else Color(0xFF00E676)
                                    ),
                                    enabled = !viewModel.isV2RayTorSyncing,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (viewModel.isV2RayTorSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (lang == "RU") "Синхронизация сетей..." else "Synchronizing networks...", fontSize = 11.sp, color = Color.White)
                                    } else {
                                        Text(
                                            text = if (viewModel.v2RayTorSyncStatus == "SYNCED") {
                                                if (lang == "RU") "СЕТИ СИНХРОНИЗИРОВАНЫ RE-SYNC" else "NETWORKS SYNCED RE-SYNC"
                                            } else {
                                                if (lang == "RU") "ИНИЦИАЛИЗИРОВАТЬ ЗАЩИЩЕННЫЙ МОСТ" else "INITIALIZE SECURE BRIDGE"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.v2RayTorSyncStatus == "SYNCED") Color(0xFF00E676) else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 2) {
                        // NEW DYNAMIC ONION RELAYS AND SERVER NODES WITH SCANNING & TESTING
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (lang == "RU") "📡 Настройка кастомных Onion-серверов" else "📡 Custom Onion Servers Configuration", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                
                                // SMP Node Configuration
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (lang == "RU") "SMP Onion сервер доступа:" else "SMP Onion Access Server:", fontSize = 10.sp, color = Color.LightGray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.smpOnionAddress,
                                            onValueChange = { viewModel.updateSmpOnionAddress(it) },
                                            readOnly = viewModel.isNoGameModeEnabled,
                                            singleLine = false,
                                            maxLines = 6,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.LightGray,
                                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "smp_onion") Color(0xFF00FF41) else Color(0xFF00E676),
                                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "smp_onion") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(110.dp)
                                                .clickable {
                                                    if (viewModel.isNoGameModeEnabled) focusedFieldId = "smp_onion"
                                                }
                                        )

                                        Button(
                                            onClick = {
                                                scanningServerType = "SMP"
                                                showCameraView = true
                                                isCameraScanning = true
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                        ) {
                                            Text("QR", fontSize = 10.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                localKeyboard?.hide()
                                                viewModel.testOnionAddress("SMP", viewModel.smpOnionAddress) // SMP_TEST_UNIQ

                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3B46)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text(if (lang == "RU") "ТЕСТ" else "TEST", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // SMP Metrics Indicators
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (viewModel.smpStatusState) {
                                        "ONLINE" -> Color(0xFF00E676)
                                        "OFFLINE" -> Color(0xFFFF5252)
                                        else -> Color(0xFF9E9E9E)
                                    }
                                    val statusText = when (viewModel.smpStatusState) {
                                        "ONLINE" -> if (lang == "RU") "АКТИВЕН" else "ONLINE"
                                        "OFFLINE" -> if (lang == "RU") "ОТКЛЮЧЕН" else "OFFLINE"
                                        else -> if (lang == "RU") "НЕИЗВЕСТНО" else "UNKNOWN"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                        Text(text = "SMP: $statusText", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (viewModel.smpPingResult > 0) {
                                        val smpJitter = remember(viewModel.smpPingResult) { (-2..2).random() }
                                        Text(
                                            text = "Ping: ${viewModel.smpPingResult}ms | Jitter: ${smpJitter}ms",
                                            fontSize = 9.sp,
                                            color = if (viewModel.smpPingResult < 155) Color(0xFF00E676) else Color(0xFFFFB300),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // XFTP Node Configuration
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (lang == "RU") "XFTP Onion сервер передачи медиа:" else "XFTP Onion Media Relay Server:", fontSize = 10.sp, color = Color.LightGray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = viewModel.xftpOnionAddress,
                                            onValueChange = { viewModel.updateXftpOnionAddress(it) },
                                            readOnly = viewModel.isNoGameModeEnabled,
                                            singleLine = false,
                                            maxLines = 6,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.LightGray,
                                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "xftp_onion") Color(0xFF00FF41) else Color(0xFF00E676),
                                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && focusedFieldId == "xftp_onion") Color(0xFF00FF41) else Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(110.dp)
                                                .clickable {
                                                    if (viewModel.isNoGameModeEnabled) focusedFieldId = "xftp_onion"
                                                }
                                        )

                                        Button(
                                            onClick = {
                                                scanningServerType = "XFTP"
                                                showCameraView = true
                                                isCameraScanning = true
                                                cameraScanResult = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.size(width = 46.dp, height = 40.dp)
                                        ) {
                                            Text("QR", fontSize = 10.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                localKeyboard?.hide()
                                                viewModel.testOnionAddress("XFTP", viewModel.xftpOnionAddress) // XFTP_TEST_UNIQ

                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3B46)),
                                            contentPadding = PaddingValues(horizontal = 6.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text(if (lang == "RU") "ТЕСТ" else "TEST", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // XFTP Metrics Indicators
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 4.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (viewModel.xftpStatusState) {
                                        "ONLINE" -> Color(0xFF00E676)
                                        "OFFLINE" -> Color(0xFFFF5252)
                                        else -> Color(0xFF9E9E9E)
                                    }
                                    val statusText = when (viewModel.xftpStatusState) {
                                        "ONLINE" -> if (lang == "RU") "АКТИВЕН" else "ONLINE"
                                        "OFFLINE" -> if (lang == "RU") "ОТКЛЮЧЕН" else "OFFLINE"
                                        else -> if (lang == "RU") "НЕИЗВЕСТНО" else "UNKNOWN"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(statusColor))
                                        Text(text = "XFTP: $statusText", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (viewModel.xftpPingResult > 0) {
                                        val xftpJitter = remember(viewModel.xftpPingResult) { (-2..2).random() }
                                        Text(
                                            text = "Ping: ${viewModel.xftpPingResult}ms | Jitter: ${xftpJitter}ms",
                                            fontSize = 9.sp,
                                            color = if (viewModel.xftpPingResult < 155) Color(0xFF00E676) else Color(0xFFFFB300),
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Interactive QR Scanner View Simulation
                                if (showCameraView) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                            .border(2.dp, Color(0xFF00E676), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCameraScanning) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                QrCodeScannerView(
                                                    onQrScanned = { scannedAddr ->
                                                        if (scanningServerType == "SMP") {
                                                            viewModel.updateSmpOnionAddress(scannedAddr)
                                                        } else {
                                                            viewModel.updateXftpOnionAddress(scannedAddr)
                                                        }
                                                        cameraScanResult = if (lang == "RU") "Луковый адрес $scanningServerType успешно импортирован из QR!" else "Onion address $scanningServerType successfully imported from QR!"
                                                        isCameraScanning = false
                                                        showCameraView = false
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                val inf = rememberInfiniteTransition()
                                                val lineY by inf.animateFloat(
                                                    initialValue = 0.1f,
                                                    targetValue = 0.9f,
                                                    animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse)
                                                )
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    drawLine(Color.Red, Offset(0f, size.height * lineY), Offset(size.width, size.height * lineY), 2.dp.toPx())
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val scannedAddr = if (scanningServerType == "SMP") {
                                                            "smp://nzy8c9wq910.onion/crazy_nardy_smp_channel_invite"
                                                        } else {
                                                            "xftp://f6h7p89k921.onion/crazy_nardy_xftp_relay_invite"
                                                        }
                                                        if (scanningServerType == "SMP") {
                                                            viewModel.updateSmpOnionAddress(scannedAddr)
                                                        } else {
                                                            viewModel.updateXftpOnionAddress(scannedAddr)
                                                        }
                                                        cameraScanResult = if (lang == "RU") "Луковый адрес $scanningServerType успешно импортирован из QR!" else "Onion address $scanningServerType successfully imported from QR!"
                                                        isCameraScanning = false
                                                        showCameraView = false
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 6.dp)
                                                        .height(26.dp)
                                                        .background(Color(0xFF0D533A), RoundedCornerShape(12.dp))
                                                ) {
                                                    Text(if (lang == "RU") "СИМУЛИРОВАТЬ ТЕСТ 🎯" else "SIMULATE TEST 🎯", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Availability diagnostics logs
                                if (viewModel.isTestingOnionAddress || viewModel.onionTestLogs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(if (lang == "RU") "Диагностика сети:" else "Network Diagnostics:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.onionTestLogs) { logLine ->
                                                Text(logLine, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }

                                if (cameraScanResult.isNotEmpty()) {
                                    Text(cameraScanResult, color = Color(0xFF00E676), fontSize = 11.sp)
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 3) {
                        // FOXRAY VPN Tunnel Configuration profiles
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (lang == "RU") "🦊 Модуль Foxray VPN" else "🦊 Foxray VPN Module", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Туннели VLESS / ShadowSocks / Trojan" else "VLESS / ShadowSocks / Trojan Tunnels", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val isConnected = vpnManager.vpnState == "Connected"
                                        
                                        // SPEED TEST BUTTON
                                        Button(
                                            onClick = {
                                                vpnManager.testAllConfigsAndConnectFastest { res ->
                                                    vpnFileAlert = res
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp),
                                            enabled = !vpnManager.isTestingSpeeds
                                        ) {
                                            if (vpnManager.isTestingSpeeds) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.Black, strokeWidth = 1.5.dp)
                                            } else {
                                                Text(if (lang == "RU") "⚡ БЫСТРЕЙШИЙ" else "⚡ SPEED", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (isConnected) {
                                                    vpnManager.stopVpn()
                                                } else {
                                                    val prepIntent = vpnManager.getVpnPrepareIntent()
                                                    if (prepIntent != null) {
                                                        vpnPrepareLauncher.launch(prepIntent)
                                                    } else {
                                                        vpnManager.startVpn()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isConnected) Color(0xFF00E676) else Color(0xFF222B32)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                text = if (isConnected) {
                                                    if (lang == "RU") "СЕТЬ: ОНЛАЙН" else "NET: ONLINE"
                                                } else {
                                                    if (lang == "RU") "ПОДКЛЮЧИТЬ" else "CONNECT"
                                                }, 
                                                fontSize = 10.sp, 
                                                fontWeight = FontWeight.Bold,
                                                color = if (isConnected) Color.Black else Color.White
                                            )
                                        }
                                    }
                                }

                                if (vpnManager.vpnState == "Connected") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("PING", fontSize = 8.sp, color = Color.LightGray)
                                            Text("${vpnManager.pingTime} ms", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                                        }
                                        Column {
                                            Text(if (lang == "RU") "СКОРОСТЬ ↓" else "SPEED ↓", fontSize = 8.sp, color = Color.LightGray)
                                            Text(String.format("%.1f kB/s", vpnManager.downloadSpeed), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                        Column {
                                            Text(if (lang == "RU") "TРАФИК RX/TX" else "TRAFFIC RX/TX", fontSize = 8.sp, color = Color.LightGray)
                                            val rM = vpnManager.totalBytesRx / 1024.0 / 1024.0
                                            val tM = vpnManager.totalBytesTx / 1024.0 / 1024.0
                                            Text(String.format("%.1f/%.1f MB", rM, tM), fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (lang == "RU") "Конфигурации серверов VPN:" else "VPN Server Configurations:", fontSize = 11.sp, color = Color.Gray)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { vpnConfigImportLauncher.launch(arrayOf("*/*")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (lang == "RU") "+ ФАЙЛ" else "+ FILE", fontSize = 9.sp, color = Color.White)
                                        }

                                        Button(
                                            onClick = { showVpnTextImportDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(if (lang == "RU") "+ ТЕКСТ / ССЫЛКА" else "+ TEXT / URL", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (vpnFileAlert.isNotEmpty()) {
                                    Text(vpnFileAlert, color = Color(0xFF00E676), fontSize = 11.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(vpnManager.configs) { conf ->
                                            val isSel = vpnManager.selectedConfig?.id == conf.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSel) Color(0xFF00E676).copy(alpha = 0.12f) else Color.Transparent)
                                                    .clickable { vpnManager.selectConfig(conf) }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1.3f)) {
                                                    Text(
                                                        text = "${conf.protocol} • ${conf.name}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSel) Color(0xFF00E676) else Color.White
                                                    )
                                                    Text(conf.server, fontSize = 9.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val p = vpnManager.configPings[conf.id]
                                                    if (p != null) {
                                                        Text(
                                                            text = when (p) {
                                                                -2 -> if (lang == "RU") "Тест..." else "Testing..."
                                                                -1 -> "TIMEOUT"
                                                                else -> "$p ms"
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when (p) {
                                                                -2 -> Color(0xFFFFB300)
                                                                -1 -> Color(0xFFD32F2F)
                                                                else -> Color(0xFF00E676)
                                                            }
                                                        )
                                                    }
                                                    if (isSel) {
                                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 4) {
                        // TOR onion routing logs
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(if (lang == "RU") "🧅 Маршрутизатор Tor daemon" else "🧅 Tor Daemon Router", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF00E676))
                                        Text(if (lang == "RU") "Локальное SOCKS5 проксирование" else "Local SOCKS5 proxy routing", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Switch(
                                        checked = viewModel.isTorEnabled,
                                        onCheckedChange = { viewModel.setTorEnabledState(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF00E676),
                                            checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.3f)
                                        )
                                    )
                                }

                                if (viewModel.isTorEnabled) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(if (lang == "RU") "Статус службы:" else "Service status:", fontSize = 11.sp, color = Color.Gray)
                                        Text(
                                            text = viewModel.torStatus,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (viewModel.torStatus == "ACTIVE") Color(0xFF00E676) else Color(0xFFFFA000)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(95.dp)
                                            .background(Color.Black, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(viewModel.torLogsList) { arg ->
                                                Text(arg, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.5.sp)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Tor P2P Direct Multiplayer Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            if (lang == "RU") "🔗 Децентрализованный P2P через Tor" else "🔗 Decentralized Tor P2P Multiplayer",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF00E676)
                                        )
                                        Text(
                                            if (lang == "RU") "Прямая игра в нарды и E2EE чат по адресам Onion без единого сервера!" else "Direct backgammon matching and E2EE chat over Onion addresses, entirely serverless!",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )

                                        if (viewModel.torStatus != "ACTIVE") {
                                            Text(
                                                if (lang == "RU") "⚠️ Пожалуйста, включите службу Tor daemon выше, чтобы запустить скрытый сервис Onion." else "⚠️ Please enable Tor daemon service above to activate Onion Hidden Service.",
                                                color = Color(0xFFFFB300),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                            
                                            // Handle fetching local address
                                            LaunchedEffect(Unit) {
                                                if (viewModel.torP2POnionAddress == null) {
                                                    viewModel.startTorP2PHost()
                                                }
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(if (lang == "RU") "Ваш локальный адрес Onion:" else "Your local Onion address:", fontSize = 10.sp, color = Color.Gray)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedTextField(
                                                        value = viewModel.torP2POnionAddress ?: (if (lang == "RU") "Генерация адреса..." else "Generating Onion address..."),
                                                        onValueChange = {},
                                                        readOnly = true,
                                                        singleLine = true,
                                                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.LightGray,
                                                            focusedBorderColor = Color(0xFF00E676),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.weight(1f).height(48.dp)
                                                    )

                                                    Button(
                                                        onClick = {
                                                            viewModel.torP2POnionAddress?.let {
                                                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(it))
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222B32)),
                                                        modifier = Modifier.height(40.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "КОПИР" else "COPY", fontSize = 10.sp, color = Color.White)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            viewModel.regenerateOnionAddress()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF321B22)),
                                                        modifier = Modifier.height(40.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "ОБНОВИТЬ" else "REFRESH", fontSize = 10.sp, color = Color(0xFFFF5252))
                                                    }
                                                }
                                            }

                                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                            if (viewModel.isTorP2PConnected) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        if (lang == "RU") "🟢 СОЕДИНЕНО С ПИРОМ" else "🟢 CONNECTED TO PEER",
                                                        color = Color(0xFF00E676),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                    
                                                    Button(
                                                        onClick = { viewModel.stopTorP2P() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                                                        modifier = Modifier.height(36.dp)
                                                    ) {
                                                        Text(if (lang == "RU") "ОТКЛЮЧИТЬ" else "DISCONNECT", fontSize = 10.sp, color = Color.White)
                                                    }
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Input remote address
                                                    Text(if (lang == "RU") "Адрес Onion вашего друга:" else "Your friend's Onion address:", fontSize = 10.sp, color = Color.Gray)
                                                    OutlinedTextField(
                                                        value = viewModel.torP2PRemoteAddressInput,
                                                        onValueChange = { viewModel.torP2PRemoteAddressInput = it },
                                                        placeholder = { Text("e.g. xxxxxxxx.onion", fontSize = 11.sp, color = Color.Gray) },
                                                        singleLine = true,
                                                        textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.LightGray,
                                                            focusedBorderColor = Color(0xFF00E676),
                                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                                    )

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.startTorP2PHost() },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D533A)),
                                                            modifier = Modifier.weight(1f).height(40.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ЖДАТЬ ДРУГА" else "HOST SERVER", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = { 
                                                                if (viewModel.torP2PRemoteAddressInput.isNotBlank()) {
                                                                    viewModel.connectToTorP2PHost(viewModel.torP2PRemoteAddressInput)
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                                            modifier = Modifier.weight(1f).height(40.dp)
                                                        ) {
                                                            Text(if (lang == "RU") "ПОДКЛЮЧИТЬСЯ" else "CONNECT TO FRIEND", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(if (lang == "RU") "Отладочные логи P2P соединения:" else "P2P connection transit logs:", fontSize = 9.sp, color = Color.Gray)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            ) {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(viewModel.torP2PLogs) { log ->
                                                        Text(log, color = Color(0xFF00FF66), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (activeNetworkSectionTab == 5) {
                        // BACKUP AND RESTORE CONFIGURATION SETTINGS
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = if (lang == "RU") "💾 Резервная копия настроек" else "💾 Backup & Restore Config",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF00E676)
                                )
                                Text(
                                    text = if (lang == "RU") {
                                        "Экспорт или импорт всех сетевых параметров, пин-кодов и учетных записей в зашифрованном JSON-формате."
                                    } else {
                                        "Export or import all network configurations, profile handles, and credentials as portable JSON."
                                    },
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            backupActionType = "EXPORT"
                                            showBackupPinVerifyDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "ЭКСПОРТ Настроек" else "EXPORT Config", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            backupActionType = "IMPORT"
                                            showBackupPinVerifyDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (lang == "RU") "ИМПОРТ Настроек" else "IMPORT Config", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        exportedSeedPhraseText = viewModel.currentSeedPhrase
                                        exportedContainerText = viewModel.exportCryptocontainerWithSeed(viewModel.currentSeedPhrase)
                                        showSaveCryptoKeyDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "RU") "КОНТЕЙНЕР: СИД-ФРАЗА & КЛЮЧ" else "VAULT: SEED & MASTER KEY", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        }
                        }

                        // RESET TO DEFAULT/PANIC BUTTON
                        Button(
                            onClick = {
                                viewModel.triggerPanicReset()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == "RU") "ЭКСТРЕННЫЙ СБРОС И ОЧИСТКА" else "EMERGENCY PANIC RESET & CLEAR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVpnTextImportDialog) {
        var importInputText by remember { mutableStateOf("") }
        var isNetworkLoading by remember { mutableStateOf(false) }
        var importErrorAlert by remember { mutableStateOf("") }

        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val dismissKeyboard = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }

        Dialog(onDismissRequest = { 
            if (!isNetworkLoading) {
                dismissKeyboard()
                showVpnTextImportDialog = false 
            }
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "Импорт VPN конфигурации" else "Import VPN Configuration",
                        color = Color(0xFF00E676),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = if (lang == "RU") {
                            "Вставьте vmess/vless/ss/trojan ссылки (по одной в строку), пакет из v2rayNG (base64) или ссылку на подписку (http/https):"
                        } else {
                            "Paste vmess/vless/ss/trojan URIs, v2rayNG backup/base64 block or download subscription link (http/https):"
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { 
                            Text(
                                "vless://... или http://...", 
                                fontSize = 11.sp, 
                                color = Color.Gray
                            ) 
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 6
                    )

                    if (importErrorAlert.isNotEmpty()) {
                        Text(importErrorAlert, color = Color(0xFFFFB300), fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                dismissKeyboard()
                                showVpnTextImportDialog = false 
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f),
                            enabled = !isNetworkLoading
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 10.sp)
                        }

                        Button(
                            onClick = {
                                val trimmed = importInputText.trim()
                                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                    isNetworkLoading = true
                                    importErrorAlert = if (lang == "RU") "Загрузка подписки..." else "Downloading subscription..."
                                    vpnManager.importSubscriptionFromUrl(trimmed) { res ->
                                        isNetworkLoading = false
                                        if (res.startsWith("Успешно")) {
                                            dismissKeyboard()
                                            vpnFileAlert = res
                                            showVpnTextImportDialog = false
                                        } else {
                                            importErrorAlert = res
                                        }
                                    }
                                } else {
                                    val res = vpnManager.importConfigsFromText(trimmed)
                                    if (res.startsWith("Успешно")) {
                                        dismissKeyboard()
                                        vpnFileAlert = res
                                        showVpnTextImportDialog = false
                                    } else {
                                        importErrorAlert = res
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f),
                            enabled = importInputText.isNotBlank() && !isNetworkLoading
                        ) {
                            if (isNetworkLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 1.5.dp)
                            } else {
                                Text(if (lang == "RU") "ИМПОРТИРОВАТЬ" else "IMPORT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- PIN-CODE CONFIRMATION FOR BACKUP ACTIONS ---
    // ----------------------------------------------------
    if (showBackupPinVerifyDialog) {
        var enteredPin by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showBackupPinVerifyDialog = false; backupPinError = "" }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (lang == "RU") "🔐 Требуется PIN-код" else "🔐 PIN Security Check",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Введите PIN-код от настроек для подтверждения резервной операции."
                        } else {
                            "Enter the admin PIN code to authorize the settings backup action."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { enteredPin = it },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        singleLine = true
                    )

                    if (backupPinError.isNotEmpty()) {
                        Text(backupPinError, color = Color(0xFFD32F2F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showBackupPinVerifyDialog = false; backupPinError = "" },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (enteredPin == viewModel.pinCode) {
                                    showBackupPinVerifyDialog = false
                                    backupPinError = ""
                                    if (backupActionType == "EXPORT") {
                                        exportedJsonText = viewModel.exportSettingsToJson()
                                        showExportResultDialog = true
                                    } else {
                                        showImportInputDialog = true
                                    }
                                } else {
                                    backupPinError = if (lang == "RU") "Неверный PIN-код!" else "Incorrect PIN code!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ОК" else "OK", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- EXPORT RESULT DIALOG OVERLAY ---
    // ----------------------------------------------------
    if (showExportResultDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        Dialog(onDismissRequest = { showExportResultDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "💾 Конфигурация экспортирована" else "💾 Config Exported Successfully",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Скопируйте сгенерированный JSON-блок в буфер обмена для безопасного хранения."
                        } else {
                            "Copy the generated JSON backup block to clipboard for remote storage."
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color.Black, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = exportedJsonText,
                                    color = Color(0xFF00FF66),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportedJsonText))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "КОПИРОВАТЬ" else "COPY TO CLIPBOARD", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showExportResultDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (lang == "RU") "ГОТОВО" else "DONE", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // --- IMPORT INPUT DIALOG OVERLAY ---
    // ----------------------------------------------------
    if (showImportInputDialog) {
        var importJsonInputText by remember { mutableStateOf("") }
        var importSeedPhraseText by remember { mutableStateOf("") }
        var importStatusMsg by remember { mutableStateOf("") }
        var isImportSucessful by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { 
            if (!isImportSucessful) showImportInputDialog = false
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "📂 Импорт и дешифрование сети" else "📂 Network Configuration Import",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Поддерживается: стандартный JSON-файл настроек или зашифрованный блок CRAZYCONTAINER-. Если блок защищен сид-фразой, укажите ее ниже:"
                        } else {
                            "Supports: standard JSON configuration payload or CRAZYCONTAINER- base64 blocks. If secured with a seed mnemonic, specify it below:"
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = importJsonInputText,
                        onValueChange = { importJsonInputText = it },
                        placeholder = { Text("{\n  \"selectedTheme\": ...\n} OR CRAZYCONTAINER-...", fontSize = 10.sp, color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(90.dp)
                    )

                    // Optional Seed Mnemonic associated during decrypt
                    if (importJsonInputText.trim().startsWith("CRAZYCONTAINER-")) {
                        OutlinedTextField(
                            value = importSeedPhraseText,
                            onValueChange = { importSeedPhraseText = it },
                            placeholder = { Text("anchor beacon crypto ... (12 words)", fontSize = 10.sp, color = Color.Gray) },
                            label = { Text(if (lang == "RU") "Обязательная Сид-фраза" else "Required Seed Phrase", fontSize = 10.sp) },
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (importStatusMsg.isNotEmpty()) {
                        Text(
                            text = importStatusMsg,
                            color = if (isImportSucessful) Color(0xFF00FF66) else Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showImportInputDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            enabled = !isImportSucessful
                        ) {
                            Text(if (lang == "RU") "ОТМЕНА" else "CANCEL", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val text = importJsonInputText.trim()
                                val success = if (text.startsWith("CRAZYCONTAINER-")) {
                                    viewModel.importCryptocontainerWithSeed(importSeedPhraseText.trim(), text)
                                } else {
                                    viewModel.importSettingsFromJson(text)
                                }
                                
                                if (success) {
                                    isImportSucessful = true
                                    importStatusMsg = if (lang == "RU") "✅ Импорт выполнен успешно!" else "✅ Vault imported successfully!"
                                } else {
                                    importStatusMsg = if (lang == "RU") "❌ Неверный сид/формат ключа!" else "❌ Invalid seed or container format!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isImportSucessful) {
                                    if (lang == "RU") "ОТЛИЧНО" else "SUPER"
                                } else {
                                    if (lang == "RU") "ИМПОРТИРОВАТЬ" else "IMPORT"
                                },
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (isImportSucessful) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1200)
                            showImportInputDialog = false
                            viewModel.connectAndSyncAllNetworkComponents()
                        }
                    }
                }
            }
        }
    }

    SaveCryptoKeyDialog(
        show = showSaveCryptoKeyDialog,
        lang = lang,
        seedPhraseText = exportedSeedPhraseText,
        containerText = exportedContainerText,
        onDismiss = { showSaveCryptoKeyDialog = false },
        onCopySeed = {},
        onCopyContainer = {},
    )
}

