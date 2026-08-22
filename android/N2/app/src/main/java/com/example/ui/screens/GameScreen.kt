/**
 * Основной экран игры — самый большой Compose-файл приложения (~10 500 строк).
 *
 * ## Содержимое
 * - [GameScreen] — главный Composable экрана: доска нард, HUD-панель, радио, Walkie-Talkie.
 * - [WelcomeScreenLayout] — приветственный экран (выбор языка, радио, режима игры, имени).
 * - [SettingsDialog] — диалог настроек (тема, язык, Tor, SimpleX, VPN, криптоконтейнер).
 * - [SimpleXFullScreenChat] — полноэкранный чат SimpleX с контактами.
 * - [LogsDialog], [StatsDialog], [RulesDialog], [LeaderboardDialog] — вспомогательные диалоги.
 * - [RadioDialog], [PinUnlockDialog], [PinSetupDialog] — диалоги радио и PIN-доступа.
 * - [JrebiyDialog] — диалог жеребьёвки (лота).
 *
 * ## Состояние
 * Управляется через [GameViewModel]. Все UI-состояния (диалоги, режимы) хранятся
 * в локальных remember-переменных внутри [GameScreen].
 *
 * ## Безопасность
 * - ScreenSecurityManager включается/выключается при открытии/закрытии чувствительных диалогов.
 * - PIN-блокировка для доступа к SimpleX-чату.
 * - Duress PIN для экстренного сброса.
 */
package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.ui.GameViewModel
import com.example.ui.viewmodels.AudioViewModel
import com.example.ui.screens.chat.SimpleXFullScreenChat
import com.example.ui.screens.dialogs.LeaderboardDialog
import com.example.ui.screens.dialogs.LogsDialog
import com.example.ui.screens.dialogs.PinUnlockDialog
import com.example.ui.screens.dialogs.RadioDialog
import com.example.ui.screens.dialogs.RulesDialog
import com.example.ui.screens.dialogs.StatsDialog
import com.example.ui.screens.dialogs.localizedLabel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Главный экран игры. Содержит доску, HUD-панель, радио, голосовую связь и все диалоги.
 * Реагирует на изменения в GameViewModel и отображает доску нард с анимацией ходов.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GameScreen(viewModel: GameViewModel, audioViewModel: AudioViewModel? = null) {
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage
    
    var showPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.showWelcomeScreen, viewModel.isNoGameModeEnabled) {
        val activity = context as? android.app.Activity
        if (viewModel.isNoGameModeEnabled) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (viewModel.showWelcomeScreen) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showAboutRulesDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showLeaderboardDialog by remember { mutableStateOf(false) }
    var showRadioDialog by remember { mutableStateOf(false) }
    var showOnlineMatchmakingDialog by remember { mutableStateOf(false) }
    var isSimpleXFullScreenChatOpen by remember { mutableStateOf(false) }
    var chatInitialTabSegment by remember { mutableStateOf(0) }
    var showRadioLanguageDialog by remember { mutableStateOf(false) }
    var selectedRadioLanguage by remember { mutableStateOf("RU") }
    var showRadioLockAlert by remember { mutableStateOf(false) }

    LaunchedEffect(isSimpleXFullScreenChatOpen, showSettingsDialog, showPinDialog) {
        if (isSimpleXFullScreenChatOpen || showSettingsDialog || showPinDialog) {
            com.example.security.ScreenSecurityManager.enableScreenSecurity()
        } else {
            com.example.security.ScreenSecurityManager.disableScreenSecurity()
        }
    }
    
    val logsListState = rememberLazyListState()
    
    // Automatically scroll logs to bottom when a new entry is added
    LaunchedEffect(viewModel.turnLogs.size) {
        if (viewModel.turnLogs.isNotEmpty()) {
            logsListState.animateScrollToItem(viewModel.turnLogs.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        if (viewModel.isNoGameModeEnabled) {
            MatrixNoGameScreen(viewModel = viewModel)
        } else if (viewModel.showWelcomeScreen) {
            WelcomeScreenLayout(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
            // Left Column: The massive Backgammon Board — centered in available space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BackgammonBoardContainer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(1.15f)
                    )
                }
            }

            // Right Column: Compact floating sidebar
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Unified Sidebar Header: Turn indicator + Settings button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lang == "RU") "ХОД v" else "TURN v",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        
                        // Active Player Color Token
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        ) {
                            CheckerPiece(
                                player = viewModel.activePlayer,
                                themeId = viewModel.selectedTheme,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Exit and Settings Buttons Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Exit Button
                        IconButton(
                            onClick = {
                                // Stop radio, VPN, Tor and exit app completely clearing memory
                                viewModel.radioManager.stop()
                                viewModel.vpnManager.stopVpn()
                                viewModel.setTorEnabledState(false)
                                (context as? android.app.Activity)?.finishAffinity()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (lang == "RU") "Выход" else "Exit",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Settings Button
                        IconButton(
                            onClick = { 
                                showSettingsDialog = true
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = if (lang == "RU") "Настройки" else "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Compact Scores Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚪ " + (if (lang == "RU") "Мы" else "US") + ": ${viewModel.borneOffCount[Player.WHITE]}/15",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "⚫ " + (if (lang == "RU") "Бот" else "BOT") + ": ${viewModel.borneOffCount[Player.BLACK]}/15",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // If Match hasn't started, show start match view
                if (!viewModel.isMatchStarted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                        ),
                        border = BorderStroke(1.5.dp, Color(0xFFD3A373).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (lang == "RU") "ГОТОВ К ИГРЕ! 🎲" else "READY TO PLAY! 🎲",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                
                                val modeLabel = when (viewModel.selectedWelcomeMode) {
                                    0 -> if (lang == "RU") "Бот-зануда" else "Dull Bot Opponent"
                                    1 -> if (lang == "RU") "Один экран" else "P2P Single Screen"
                                    2 -> if (lang == "RU") "Безопасный онлайн" else "Secure Online Play"
                                    else -> "PvP"
                                }
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(if (lang == "RU") "РЕЖИМ:" else "MODE:", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(modeLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.onStartGameBtnPressed() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("onstart_game_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (lang == "RU") "НАЧАТЬ 🎲" else "START 🎲",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                } else {
                    // Match has started - Game UI
                    val canBearOff = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) {
                        viewModel.reachablePaths.any { it.finalTo >= 24 }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Zarik Bot Joke Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🤖 " + (if (lang == "RU") "ЗАРИК-БОТ" else "ZARIK BOT"),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = { viewModel.fetchBotJoke(if (lang == "RU") "человек просит новую шутку" else "human asks for another joke") },
                                        modifier = Modifier.size(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "\"${viewModel.botSpeechBubble}\"",
                                    fontSize = 7.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 9.sp
                                )
                            }
                        }

                        // Web Radio Card (with custom Play/Stop long press dialog trigger)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val radio = viewModel.radioManager
                                val currentChan = radio.currentChannel ?: radio.channels.firstOrNull()
                                
                                Text(
                                    text = if (lang == "RU") "РАДИОПРИЕМНИК 📻" else "WEB RADIO 📻",
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Text(
                                    text = currentChan?.name ?: (if (lang == "RU") "Передатчик выключен" else "Receiver Off"),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (radio.isLoading) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(1.dp), color = MaterialTheme.colorScheme.primary)
                                }
                                if (radio.errorMessage != null) {
                                    Text(text = radio.errorMessage ?: "", fontSize = 6.sp, color = Color.Red, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                val playStopButtonText = if (radio.isPlaying) "■ STOP" else "▶ PLAY"
                                val playStopColor = if (radio.isPlaying) Color.Red.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(playStopColor)
                                        .combinedClickable(
                                            onClick = {
                                                if (radio.isPlaying) {
                                                    radio.stop()
                                                } else {
                                                    val channelToPlay = currentChan ?: radio.channels.firstOrNull()
                                                    if (channelToPlay != null) radio.play(channelToPlay)
                                                }
                                            },
                                            onLongClick = {
                                                showRadioLanguageDialog = true
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = playStopButtonText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Bear off & Roll Dice Buttons if active and needed to preserve core game play
                        if (viewModel.gameStatus == GameStatus.BEFORE_ROLL && viewModel.activePlayer == Player.WHITE && !viewModel.isRollingDice) {
                            Button(
                                onClick = { viewModel.rollDice() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth().height(26.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(if (lang == "RU") "БРОСИТЬ КУБИКИ" else "ROLL DICE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        if (canBearOff) {
                            Button(
                                onClick = { viewModel.handleBearOffClicked() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD3A373)),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.fillMaxWidth().height(26.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(if (lang == "RU") "ВЫБРОСИТЬ ФИШКУ" else "BEAR OFF CHIP", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }

                // Walkie-Talkie Voice Messages Status (shows burning warnings / history if any)
                if (viewModel.simplexVoiceMessages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(4.dp)
                    ) {
                        val firstMsg = viewModel.simplexVoiceMessages.last()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${firstMsg.sender} (${firstMsg.durationSec}s)", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(progress = { firstMsg.playProgress }, modifier = Modifier.fillMaxWidth().height(2.dp))
                                if (!viewModel.isVoiceSavingEnabled) {
                                    Text(if (lang == "RU") "🔥 Сгорает после проигрывания" else "🔥 Destroys post-play", fontSize = 5.5.sp, color = Color.Red)
                                }
                            }
                            if (!firstMsg.isPlayed) {
                                IconButton(onClick = { viewModel.playVoiceMessage(firstMsg) }, modifier = Modifier.size(16.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(10.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Custom 3-seconds long pressed TALK button
                var pressStartTime by remember { mutableStateOf(0L) }
                val scope = rememberCoroutineScope()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (viewModel.talkieState) {
                                com.example.ui.GameViewModel.TalkieState.OFF -> Color.Gray
                                com.example.ui.GameViewModel.TalkieState.CONNECTING -> Color.Gray.copy(alpha = 0.6f)
                                com.example.ui.GameViewModel.TalkieState.READY -> {
                                    if (viewModel.isRecordingVoice) Color.Red else Color(0xFF2ECC71)
                                }
                            }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    pressStartTime = System.currentTimeMillis()
                                    try {
                                        awaitRelease()
                                    } finally {
                                        val holdDuration = System.currentTimeMillis() - pressStartTime
                                        if (holdDuration >= 3000) {
                                            // Long press 3s triggered!
                                            if (viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.OFF) {
                                                viewModel.startTalkieConnection()
                                            } else if (viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.READY) {
                                                if (viewModel.isVoiceSavingEnabled) {
                                                    viewModel.showDisconnectOptionsDialog = true
                                                } else {
                                                    viewModel.showContactListDialog = true
                                                }
                                            }
                                        } else {
                                            // Short click triggered!
                                            if (viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.OFF || viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.CONNECTING) {
                                                viewModel.triggerTalkieError1488()
                                            } else if (viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.READY) {
                                                if (viewModel.isRecordingVoice) {
                                                    viewModel.stopAndSendVoiceRecording()
                                                } else {
                                                    viewModel.startVoiceRecording()
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            viewModel.isRecordingVoice -> {
                                Text(
                                    text = "ЗАПИСЬ (00:0${viewModel.recordingDurationSec})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.CONNECTING -> {
                                Text(
                                    text = viewModel.talkieStepText,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                            viewModel.talkieState == com.example.ui.GameViewModel.TalkieState.READY -> {
                                Text(
                                    text = "TALK 🎙️ (${viewModel.talkieSelectedContact?.name ?: "Зарик Бот"})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                            else -> {
                                Text(
                                    text = "TALK 🎙️",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Overlays, Logs & Dialogs
    if (showLogsDialog) {
        LogsDialog(
            logs = viewModel.turnLogs,
            onDismiss = { showLogsDialog = false }
        )
    }

    if (showRadioLanguageDialog) {
        Dialog(onDismissRequest = { showRadioLanguageDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == "RU") "Язык вещания & Станции" else "Broadcasting Language & Stations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // List of Channels based on selected game language
                    val filteredChannels = viewModel.radioManager.channels.filter { channel ->
                        val langLower = lang.lowercase()
                        channel.id.startsWith("custom_") || channel.id.startsWith("${langLower}_")
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(180.dp).fillMaxWidth()
                    ) {
                        items(filteredChannels) { channel ->
                            val isCurrent = viewModel.radioManager.currentChannel?.id == channel.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        viewModel.radioManager.play(channel)
                                        showRadioLanguageDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = channel.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showRadioLanguageDialog = false },
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text(if (lang == "RU") "Закрыть" else "Close", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (viewModel.showTalkieErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showTalkieErrorDialog = false },
            title = { Text(if (lang == "RU") "ОШИБКА ПОДКЛЮЧЕНИЯ" else "CONNECTION FAILURE", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 14.sp) },
            text = { Text(if (lang == "RU") "ERROR 1488: Сбой сквозного шифрования SimpleX/Tor пакетов. VPN туннель разорван удаленным узлом." else "ERROR 1488: E2EE handshaking node breakdown. VPN tunnel reset by peer.", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.showTalkieErrorDialog = false }
                ) {
                    Text("OK", fontSize = 11.sp)
                }
            }
        )
    }

    if (viewModel.showContactListDialog) {
        Dialog(onDismissRequest = { viewModel.showContactListDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == "RU") "Контакты SimpleX" else "SimpleX Contacts",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(180.dp).fillMaxWidth()) {
                        items(viewModel.simplexContacts) { contact ->
                            val isSelected = viewModel.talkieSelectedContact?.handle == contact.handle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable {
                                        viewModel.talkieSelectedContact = contact
                                        viewModel.showContactListDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(contact.handle, fontSize = 8.5.sp, color = Color.Gray)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.showContactListDialog = false },
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text(if (lang == "RU") "Закрыть" else "Close", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (viewModel.showDisconnectOptionsDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisconnectOptionsDialog = false },
            title = { Text(if (lang == "RU") "Сессия SimpleX: Активна" else "SimpleX Session: Active", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = { Text(if (lang == "RU") "Ваши аудиозаписи сохранены локально. Выберите действие:" else "Your E2EE recordings are stored locally. Make your choice:", fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showDisconnectOptionsDialog = false
                        viewModel.talkieState = com.example.ui.GameViewModel.TalkieState.OFF
                    }
                ) {
                    Text(if (lang == "RU") "Отключиться" else "Disconnect", fontSize = 11.sp)
                }
            },
            dismissButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.showDisconnectOptionsDialog = false
                        viewModel.simplexVoiceMessages.clear()
                        viewModel.talkieState = com.example.ui.GameViewModel.TalkieState.OFF
                    }
                ) {
                    Text(if (lang == "RU") "Стереть и Выйти" else "Clear & Disconnect", fontSize = 11.sp, color = Color.White)
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onNetworkClick = {
                showSettingsDialog = false
                showPinDialog = true
            }
        )
    }

    if (showPinDialog) {
        PinUnlockDialog(
            viewModel = viewModel,
            onDismiss = { showPinDialog = false },
            onUnlockSuccess = {
                showPinDialog = false
                chatInitialTabSegment = 1
                isSimpleXFullScreenChatOpen = true
                viewModel.connectAndSyncAllNetworkComponents()
            },
            onDismissAll = {
                showPinDialog = false
            }
        )
    }

    if (isSimpleXFullScreenChatOpen) {
        SimpleXFullScreenChat(
            viewModel = viewModel,
            initialTabSegment = chatInitialTabSegment,
            onDismiss = { isSimpleXFullScreenChatOpen = false }
        )
    }

    if (showRadioDialog) {
        RadioDialog(
            audioViewModel = audioViewModel ?: return,
            onDismiss = { showRadioDialog = false },
            onArmageddonSelected = {
                viewModel.markArmageddonSelected()
                showRadioDialog = false
                showPinDialog = true
            }
        )
    }

    if (showStatsDialog) {
        StatsDialog(
            viewModel = viewModel,
            onDismiss = { showStatsDialog = false }
        )
    }

    if (showAboutRulesDialog) {
        RulesDialog(lang = viewModel.selectedLanguage, onDismiss = { showAboutRulesDialog = false })
    }

    if (showLeaderboardDialog) {
        LeaderboardDialog(lang = viewModel.selectedLanguage, onDismiss = { showLeaderboardDialog = false })
    }

    if (showOnlineMatchmakingDialog) {
        Dialog(onDismissRequest = { if (!viewModel.isMatchmaking) showOnlineMatchmakingDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Language.get("online_search_opponent", lang),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (viewModel.isMatchmaking) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Matchmaking Logs panel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color.Black, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.matchmakingLogs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = if (logLine.contains("🎉") || logLine.contains("🏁")) Color(0xFF00FF66) else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!viewModel.isMatchmaking) {
                            Button(
                                onClick = { viewModel.startOnlineMatchmaking() },
                                modifier = Modifier.weight(1.5f)
                            ) {
                                Text("ПОИСК", fontWeight = FontWeight.Bold)
                            }
                            
                            OutlinedButton(
                                onClick = { showOnlineMatchmakingDialog = false },
                                modifier = Modifier.weight(1.0f)
                            ) {
                                Text(Language.get("close", lang))
                            }
                        } else {
                            Text(
                                text = "Инициализация безопасной Tor/SMP сессии...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (!viewModel.showWelcomeScreen && viewModel.isMatchStarted && (viewModel.gameStatus == GameStatus.LOT_STAGE1 || viewModel.gameStatus == GameStatus.LOT_STAGE2)) {
        // Comment out popup to let players touch board directly to drop dice.
        // JrebiyDialog(viewModel = viewModel)
    }

    // Game Over Overlay Dialog
    viewModel.winner?.let { winnerPlayer ->
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Конец Партии!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = if (winnerPlayer == Player.WHITE) {
                                    if (viewModel.humanPlayerColor == Player.WHITE) Color(0xFFEFEDE8) else Color(0xFF252528)
                                } else {
                                    if (viewModel.botPlayerColor == Player.WHITE) Color(0xFFEFEDE8) else Color(0xFF252528)
                                },
                                shape = CircleShape
                            )
                            .border(2.dp, Color(0xFFD3A373), CircleShape)
                            .shadow(4.dp, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (winnerPlayer == Player.WHITE) Icons.Default.Face else Icons.Default.Star,
                            contentDescription = "Avatar winner",
                            tint = if (winnerPlayer == Player.WHITE) Color.DarkGray else Color.Yellow,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val winnerName = if (winnerPlayer == Player.WHITE) {
                        val gotColor = viewModel.humanPlayerColor.localizedLabel(lang)
                        if (lang == "RU") "Игрок ($gotColor)" else "Player ($gotColor)"
                    } else {
                        val gotColor = viewModel.botPlayerColor.localizedLabel(lang)
                        if (lang == "RU") "Бот ($gotColor)" else "Bot ($gotColor)"
                    }
                    Text(
                        text = "$winnerName ${Language.get("winner", lang)}!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val whiteCount = viewModel.borneOffCount[if (viewModel.humanPlayerColor == Player.WHITE) Player.WHITE else Player.BLACK] ?: 0
                    val blackCount = viewModel.borneOffCount[if (viewModel.humanPlayerColor == Player.BLACK) Player.WHITE else Player.BLACK] ?: 0
                    Text(
                        text = if (lang == "RU") "Счёт: Белые $whiteCount - Черные $blackCount" else "Score: White $whiteCount - Black $blackCount",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.startNewGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_restart_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Play again")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Играть снова")
                    }
                }
            }
        }
    }
}

// Extension to scale modifiers using graphicsLayer for proper visual and touch scaling
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
)

/**
 * Секция отображения шашек бота и его лога на доске.
 */
@Composable
fun BotOpponentSection(viewModel: GameViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bot Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF252528), CircleShape)
                            .border(1.5.dp, Color(0xFFD3A373), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Bot Avatar",
                            tint = Color(0xFFEFEDE8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    // Online pulse dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Green, CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Speech Bubble containing jokes or comments
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (viewModel.selectedLanguage == "RU") "Зарик-Победитель (ИИ-бот)" else "Zarik Winner (AI Bot)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (viewModel.isGeminiLoadingJoke) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = viewModel.botSpeechBubble,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Sarcasm refresh button
                IconButton(
                    onClick = { viewModel.fetchBotJoke(if (viewModel.selectedLanguage == "RU") "человек просит новую шутку" else "human asks for another joke") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сарказм",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Interactive character chat
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var chatInputText by remember { mutableStateOf("") }
                
                TextField(
                    value = chatInputText,
                    onValueChange = { chatInputText = it },
                    placeholder = {
                        Text(
                            text = if (viewModel.selectedLanguage == "RU") "Подколоть Зарика..." else "Provoke Zarik...",
                            fontSize = 11.5.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = {
                        if (chatInputText.isNotBlank()) {
                            val contextPrefix = if (viewModel.selectedLanguage == "RU") {
                                "Пользователь провоцирует/спрашивает бота: "
                            } else {
                                "User provokes/asks the bot: "
                            }
                            viewModel.fetchBotJoke("$contextPrefix \"$chatInputText\"")
                            chatInputText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = chatInputText.isNotBlank() && !viewModel.isGeminiLoadingJoke
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (chatInputText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Панель управления кубиками: отображение значений, кнопки броска, авто-режим.
 */
@Composable
fun DiceControlAndStatus(viewModel: GameViewModel) {
    val player = viewModel.activePlayer
    val lang = viewModel.selectedLanguage
    val theme = viewModel.selectedTheme
    val canRoll = viewModel.gameStatus == GameStatus.BEFORE_ROLL && !(viewModel.isBotOpponentEnabled && player == Player.BLACK)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Active Turn Tracker Header
        val visualColor = if (player == Player.WHITE) viewModel.humanPlayerColor else viewModel.botPlayerColor
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (visualColor == Player.WHITE) Color(0xFFEFEDE8) else Color(0xFF252528)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .border(
                    width = 1.dp,
                    color = if (visualColor == Player.WHITE) Color.DarkGray.copy(alpha = 0.3f) else Color(0xFFB08D57),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (viewModel.gameStatus == GameStatus.GAME_OVER) Language.get("status_game_over", lang).uppercase() else "${Language.get("score_title", lang).uppercase()}: ${visualColor.localizedLabel(lang)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (visualColor == Player.WHITE) Color.Black else Color(0xFFEBE0D0)
                )
                
                if (viewModel.isOpponentPlayingTransferred && viewModel.gameStatus != GameStatus.GAME_OVER) {
                    Text(
                        text = Language.get("intercept_turn", lang),
                        color = Color.Red,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Exited Checkers Count (Home exit counters)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val whitePlayer = if (viewModel.humanPlayerColor == Player.WHITE) Player.WHITE else Player.BLACK
            val blackPlayer = if (viewModel.humanPlayerColor == Player.BLACK) Player.WHITE else Player.BLACK
            ScoreIndicator(label = Language.get("white_borne_off", lang), count = viewModel.borneOffCount[whitePlayer] ?: 0, color = Color(0xFFDCDAD4))
            ScoreIndicator(label = Language.get("black_borne_off", lang), count = viewModel.borneOffCount[blackPlayer] ?: 0, color = Color(0xFF333335))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Large Graphical Dice representation
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (viewModel.gameStatus == GameStatus.PLAYER_MOVE && viewModel.remainingDice.isNotEmpty()) {
                // Show remaining dice values as clean small cubes
                viewModel.remainingDice.forEach { valD ->
                    DiceCube(value = valD, isRolling = viewModel.isRollingDice, themeId = theme)
                    Spacer(modifier = Modifier.width(6.dp))
                }
            } else {
                // Static template dice when waiting to roll
                DiceCube(value = viewModel.diceValue1, isRolling = viewModel.isRollingDice, themeId = theme)
                Spacer(modifier = Modifier.width(10.dp))
                DiceCube(value = viewModel.diceValue2, isRolling = viewModel.isRollingDice, themeId = theme)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Direct manual bear-off trigger button if selection exists and is valid
        val canBearOff = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) {
            viewModel.reachablePaths.any { it.finalTo >= 24 }
        }

        if (canBearOff) {
            Button(
                onClick = { viewModel.handleBearOffClicked() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD3A373),
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("bear_off_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = "Exit", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Language.get("bear_off", lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (canRoll) {
            // Inform players they can touch the board to roll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Language.get("roll_desc", lang).uppercase(),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Context text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        viewModel.gameStatus == GameStatus.GAME_OVER -> Language.get("status_game_over", lang)
                        viewModel.isRollingDice -> Language.get("status_rolling", lang)
                        viewModel.isBotOpponentEnabled && player == Player.BLACK -> Language.get("status_bot_turn", lang)
                        viewModel.isAutoPlayerMoveActive && player == Player.WHITE -> Language.get("status_auto_move", lang)
                        else -> Language.get("status_your_turn", lang)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Индикатор счёта: метка, количество шашек и цвет. */
@Composable
fun ScoreIndicator(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .border(0.5.dp, Color.Gray, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: ",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count/15",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Диалог настроек игры: ИИ, авто-бросок, радио, подключение к серверу. */
@Composable
fun SettingsDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onNetworkClick: () -> Unit
) = GameSettingsDialog(viewModel, onDismiss, onNetworkClick)






/** Экран приветствия: язык, радио, режим игры, имя игрока. */
@Composable
fun WelcomeScreenLayout(viewModel: GameViewModel, modifier: Modifier = Modifier) = GameWelcomeScreen(viewModel, modifier)


/**
 * Полноэкранный чат SimpleX.
 * Вынесен в отдельный файл [com.example.ui.screens.chat.SimpleXChatScreen].
 */
@Composable
fun SimpleXFullScreenChat(
    viewModel: GameViewModel,
    initialTabSegment: Int = 0,
    onDismiss: () -> Unit
) {
    // Function moved to com.example.ui.screens.chat.SimpleXChatScreen
}


/** Селектор режима аудио: рация, радио или тишина. */
@Composable
fun MatchAudioModeSelector(viewModel: com.example.ui.GameViewModel) {
    val lang = viewModel.selectedLanguage
    val currentMode = viewModel.matchAudioMode
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (lang == "RU") "АУДИОРЕЖИМ ИГРЫ (Один из трёх)" else "GAME AUDIO MODE (One of three)",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    com.example.ui.GameViewModel.MatchAudioMode.WALKIE_TALKIE to (if (lang == "RU") "🎙️ Рация" else "🎙️ Talkie"),
                    com.example.ui.GameViewModel.MatchAudioMode.RADIO to (if (lang == "RU") "📻 Радио" else "📻 Radio"),
                    com.example.ui.GameViewModel.MatchAudioMode.SILENCE to (if (lang == "RU") "🔇 Тишина" else "🔇 Silence")
                ).forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                viewModel.updateMatchAudioMode(mode)
                            }
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Карточка рации: запись, воспроизведение, список контактов. */
@Composable
fun SimplexWalkieTalkieCard(viewModel: com.example.ui.GameViewModel) {
    val lang = viewModel.selectedLanguage
    val voiceMsgs = viewModel.simplexVoiceMessages
    val isRecording = viewModel.isRecordingVoice
    val recDuration = viewModel.recordingDurationSec
    
    // Find first incoming voice message (the most relevant received message)
    val activeInboundMsg = voiceMsgs.firstOrNull { it.sender != "Вы" && it.sender != "You" }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isRecording) Color.Red else (if (viewModel.simplexStatus == "CONNECTED") Color.Green else Color.Gray), CircleShape)
                    )
                    Text(
                        text = if (isRecording) {
                            if (lang == "RU") "ЭФИР 🔴" else "ON AIR 🔴"
                        } else {
                            if (lang == "RU") "SimpleX Рация 🎙️" else "SimpleX Walkie-Talkie 🎙️"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = { viewModel.isWalkieTalkieMuted = !viewModel.isWalkieTalkieMuted },
                    modifier = Modifier.size(16.dp)
                ) {
                    Text(
                        text = if (viewModel.isWalkieTalkieMuted) "🔇" else "🔊",
                        fontSize = 11.sp
                    )
                }
            }

            // Warning banner if audio is muted or on hold
            if (viewModel.isWalkieTalkieMuted || viewModel.matchAudioMode != com.example.ui.GameViewModel.MatchAudioMode.WALKIE_TALKIE) {
                val bannerText = when {
                    viewModel.matchAudioMode == com.example.ui.GameViewModel.MatchAudioMode.RADIO -> 
                        if (lang == "RU") "📻 Радио активно • Рация на удержании" else "📻 Radio Mode Active • Talkie on hold"
                    viewModel.matchAudioMode == com.example.ui.GameViewModel.MatchAudioMode.SILENCE ->
                        if (lang == "RU") "🔇 Режим тишины • Все звуки отключены" else "🔇 Silent Mode • All audio muted"
                    else ->
                        if (lang == "RU") "🔇 Звук входящих сообщений рации выключен" else "🔇 Walkie-talkie audio is muted"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(vertical = 4.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = bannerText,
                        color = Color.Red,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // BUTTON 1 (UPPER): INCOMING MESSAGE PLAYBACK / PAUSE / STANDBY
            if (activeInboundMsg != null) {
                val isPlayingMsg = activeInboundMsg.isPlaying
                Button(
                    onClick = {
                        if (isPlayingMsg) {
                            viewModel.pauseVoiceMessage(activeInboundMsg)
                        } else {
                            viewModel.playVoiceMessage(activeInboundMsg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingMsg) Color.Red else Color(0xFF2E7D32) // Red when playing, Green when ready to play
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isPlayingMsg) "⏸ PAUSE" else "▶ PLAY INBOUND [${activeInboundMsg.sender}]",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }
                
                // Active Message Stats and progress bar
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${activeInboundMsg.sender} (${activeInboundMsg.durationSec}s)",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (viewModel.isVoiceSavingEnabled) {
                                if (lang == "RU") "💾 Криптоконтейнер" else "💾 Cryptocontainer"
                            } else {
                                if (lang == "RU") "🔥 Автоудаление" else "🔥 Auto-destruction"
                            },
                            fontSize = 7.sp,
                            color = if (viewModel.isVoiceSavingEnabled) Color.Green else Color.Red
                        )
                    }
                    LinearProgressIndicator(
                        progress = { activeInboundMsg.playProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = if (isPlayingMsg) Color.Red else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            } else {
                // Standby Button (No messages received yet)
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "ОЖИДАНИЕ ПРИЕМА..." else "STANDBY (READY TO RX)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // BUTTON 2 (LOWER): VOICE TRANSMIT / RECORDING
            Button(
                onClick = {
                    if (isRecording) {
                        viewModel.stopAndSendVoiceRecording()
                    } else {
                        viewModel.startVoiceRecording()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isRecording) {
                            if (lang == "RU") "🔴 ЭФИР (00:${String.format("%02d", recDuration)})" else "🔴 ON AIR (00:${String.format("%02d", recDuration)})"
                        } else {
                            if (lang == "RU") "🎙️ ЗАПИСАТЬ СООБЩЕНИЕ" else "🎙️ RECORD VOICE MESSAGE"
                        },
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }

            // DYNAMIC WALKIE-TALKIE SETTINGS (Toggles for Auto-Play & Storage mode)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Opt 1: Autoplay vs manual play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") {
                            if (viewModel.isAutoPlayVoiceEnabled) "▶ Автовоспроизведение входящих" else "⏸ Ручное воспроизведение"
                        } else {
                            if (viewModel.isAutoPlayVoiceEnabled) "▶ Auto-play Inbound" else "⏸ Manual Playback"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = viewModel.isAutoPlayVoiceEnabled,
                        onCheckedChange = { viewModel.isAutoPlayVoiceEnabled = it },
                        modifier = Modifier.scale(0.6f).height(16.dp).width(30.dp)
                    )
                }

                // Opt 2: Cryptocontainer vs Auto-destruction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "RU") {
                            if (viewModel.isVoiceSavingEnabled) "💾 Сохранять в криптоконтейнере" else "🔥 Автоудаление после прослушивания"
                        } else {
                            if (viewModel.isVoiceSavingEnabled) "💾 Save to Crypto-container" else "🔥 Auto-destruct post-play"
                        },
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = viewModel.isVoiceSavingEnabled,
                        onCheckedChange = { viewModel.isVoiceSavingEnabled = it },
                        modifier = Modifier.scale(0.6f).height(16.dp).width(30.dp)
                    )
                }

                // Opt 3: Subscriber Tier settings selector row (Free, Premium, Royal)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (lang == "RU") "👑 Тариф аккаунта (тест лимитов):" else "👑 Account Tier (limits test):",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        com.example.ui.UserTier.values().forEach { tier ->
                            val isSelected = viewModel.currentUserTier == tier
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        0.5.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f), 
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { viewModel.updateUserTier(tier) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tier.name,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ряд быстрых кнопок: радио, режим аудио, настройки чата. */
@Composable
fun LargeQuickControlsRow(
    viewModel: com.example.ui.GameViewModel,
    onRadioClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val lang = viewModel.selectedLanguage
    val isRadioPlaying = viewModel.radioManager.isPlaying
    val activeStationName = viewModel.radioManager.activeTrackTitle
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Button 1: Live Radio Control
        Card(
            modifier = Modifier
                .weight(1.0f)
                .height(44.dp)
                .clickable { onRadioClick() },
            colors = CardDefaults.cardColors(
                containerColor = if (isRadioPlaying) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.dp,
                if (isRadioPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        viewModel.radioManager.togglePlay()
                    },
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            if (isRadioPlaying) Color.Red else MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isRadioPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Radio",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lang == "RU") "📻 РАДИО-ЭФИР" else "📻 LIVE RADIO",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRadioPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRadioPlaying) activeStationName else (if (lang == "RU") "ВКЛЮЧИТЬ" else "CLICK TO PLAY"),
                        fontSize = 6.2.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Button 2: Options / Settings (Previously Button 3)
        Card(
            modifier = Modifier
                .weight(1.0f)
                .height(44.dp)
                .clickable { onSettingsClick() },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (lang == "RU") "НАСТРОЙКИ" else "SETTINGS",
                        fontSize = 7.2.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = if (lang == "RU") "УЗЛЫ И VPN" else "NODES & VPN",
                        fontSize = 6.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}


/** Статусное сообщение в процессе жеребьёвки. */
@Composable
fun LotteryStatusMessage(viewModel: GameViewModel) = com.example.ui.components.LotteryStatusMessage(viewModel)


@Composable
fun LotteryDiceCube(
    value: Int,
    isRolling: Boolean,
    themeId: String = "warm",
    isLeftPlaySide: Boolean = false
) = com.example.ui.components.LotteryDiceCube(value, isRolling, themeId, isLeftPlaySide)

/** Мастер настройки криптоконтейнера: создание/восстановление из seed-фразы. */
@Composable
fun CryptocontainerSetupWizard(viewModel: GameViewModel, onDismiss: () -> Unit) = GameCryptoWizard(viewModel, onDismiss)
