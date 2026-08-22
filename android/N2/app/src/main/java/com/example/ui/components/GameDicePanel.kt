/**
 * Dice display and lottery-status composables.
 *
 * Key composables:
 * - [GameDicePanel] – standalone dice panel with roll button and doubling cube.
 * - [DiceCube] / [BoardDiceDisplay] – animated 3D dice rendered on the game board.
 * - [LotteryDiceCube] – larger animated dice used during the lottery (colour-assignment) stage.
 * - [LotteryStatusMessage] – contextual instructions shown during the lottery phase.
 * - [DiceFace] – static 2D dice-face rendering with pips.
 */
package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.GameViewModel
import com.example.ui.screens.Language

data class DiceState(
    val dice1: Int = 1,
    val dice2: Int = 2,
    val isRolling: Boolean = false,
    val currentPlayer: String = "White",
    val isDoublingCubeAvailable: Boolean = false,
    val doublingCubeValue: Int = 1
)

@Composable
fun GameDicePanel(
    state: DiceState,
    onRoll: () -> Unit = {},
    onDouble: () -> Unit = {},
    onAcceptDouble: () -> Unit = {},
    onDeclineDouble: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ход: ${state.currentPlayer}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                DiceFace(value = state.dice1, isRolling = state.isRolling)
                DiceFace(value = state.dice2, isRolling = state.isRolling)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRoll, enabled = !state.isRolling, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.isRolling) "Бросок..." else "Бросить кости")
            }
            if (state.isDoublingCubeAvailable) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDouble) { Text("Куб удвоения (x${state.doublingCubeValue})") }
                }
            }
        }
    }
}

@Composable
fun DiceCube(value: Int, isRolling: Boolean, themeId: String = "warm") {
    val id = themeId.lowercase()
    val rotX by animateFloatAsState(targetValue = if (isRolling) 1260f else 0f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Restart) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val rotY by animateFloatAsState(targetValue = if (isRolling) 1440f else 0f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(550, easing = LinearEasing), repeatMode = RepeatMode.Restart) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val rotZ by animateFloatAsState(targetValue = if (isRolling) 1080f else 0f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(650, easing = LinearEasing), repeatMode = RepeatMode.Restart) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val bounceY by animateFloatAsState(targetValue = if (isRolling) -35f else 0f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(250, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse) else spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
    val rollX by animateFloatAsState(targetValue = if (isRolling) 15f else 0f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(350, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse) else spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
    val scaleFactor by animateFloatAsState(targetValue = if (isRolling) 1.3f else 1f, animationSpec = if (isRolling) infiniteRepeatable(animation = tween(250, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse) else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    val containerColor = when (id) { "hacker" -> Color.Black; "bw" -> Color.White; "fire" -> Color(0xFF4A1200); "water" -> Color(0xFF001E3D); "cosmic" -> Color(0xFF131124); "rainbow" -> Color(0xFF2E0943); else -> Color.White }
    val borderColor = when (id) { "hacker" -> Color(0xFF00FF33); "bw" -> Color.Black; "fire" -> Color(0xFFFF5722); "water" -> Color(0xFF00E5FF); "cosmic" -> Color(0xFF00FFCC); "rainbow" -> Color(0xFFFF00FF); else -> Color.LightGray }
    val dotColor = when (id) { "hacker" -> Color(0xFF00FF33); "bw" -> Color.Black; "fire" -> Color(0xFFFFC107); "water" -> Color(0xFF80DEEA); "cosmic" -> Color(0xFFFF007F); "rainbow" -> Color(0xFF00FFFF); else -> Color.Black }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.size(36.dp).graphicsLayer { rotationX = rotX; rotationY = rotY; rotationZ = rotZ; translationX = rollX * density; translationY = bounceY * density; scaleX = scaleFactor; scaleY = scaleFactor; cameraDistance = 12f * density }
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)).shadow(if (isRolling) 12.dp else 2.dp, RoundedCornerShape(8.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                val d = size.width; val r = 2.4.dp.toPx(); val color = dotColor
                when (value) {
                    1 -> drawCircle(color, r, Offset(d/2, d/2))
                    2 -> { drawCircle(color, r, Offset(d/4, d/4)); drawCircle(color, r, Offset(3*d/4, 3*d/4)) }
                    3 -> { drawCircle(color, r, Offset(d/4, d/4)); drawCircle(color, r, Offset(d/2, d/2)); drawCircle(color, r, Offset(3*d/4, 3*d/4)) }
                    4 -> { drawCircle(color, r, Offset(d/4, d/4)); drawCircle(color, r, Offset(3*d/4, d/4)); drawCircle(color, r, Offset(d/4, 3*d/4)); drawCircle(color, r, Offset(3*d/4, 3*d/4)) }
                    5 -> { drawCircle(color, r, Offset(d/4, d/4)); drawCircle(color, r, Offset(3*d/4, d/4)); drawCircle(color, r, Offset(d/2, d/2)); drawCircle(color, r, Offset(d/4, 3*d/4)); drawCircle(color, r, Offset(3*d/4, 3*d/4)) }
                    6 -> { drawCircle(color, r, Offset(d/4, d/4)); drawCircle(color, r, Offset(3*d/4, d/4)); drawCircle(color, r, Offset(d/4, d/2)); drawCircle(color, r, Offset(3*d/4, d/2)); drawCircle(color, r, Offset(d/4, 3*d/4)); drawCircle(color, r, Offset(3*d/4, 3*d/4)) }
                }
            }
        }
    }
}

@Composable
fun BoardDiceDisplay(viewModel: GameViewModel) {
    val isBotTurn = viewModel.isBotOpponentEnabled && viewModel.activePlayer == Player.BLACK
    val lang = viewModel.selectedLanguage
    val theme = viewModel.selectedTheme
    when {
        viewModel.isRollingDice -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                DiceCube(value = viewModel.diceValue1, isRolling = true, themeId = theme)
                DiceCube(value = viewModel.diceValue2, isRolling = true, themeId = theme)
            }
        }
        viewModel.gameStatus == GameStatus.BEFORE_ROLL -> {
            if (!isBotTurn) {
                Card(modifier = Modifier.clickable { viewModel.rollDice() }.shadow(6.dp, RoundedCornerShape(10.dp)), colors = CardDefaults.cardColors(containerColor = Color(0xFFD3A373).copy(alpha = 0.9f), contentColor = Color.Black), shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f))) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Text(text = "БРОСОК", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, letterSpacing = 1.sp)
                        }
                        Text(text = "коснитесь доски", fontSize = 7.5.sp, color = Color.Black.copy(alpha = 0.7f), fontWeight = FontWeight.Light)
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)), shape = RoundedCornerShape(8.dp)) {
                    Text(text = "Думает...", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
        viewModel.gameStatus == GameStatus.PLAYER_MOVE -> {
            val remaining = viewModel.remainingDice
            if (remaining.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    remaining.forEach { valD -> DiceCube(value = valD, isRolling = false, themeId = theme) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    DiceCube(value = viewModel.diceValue1, isRolling = false, themeId = theme)
                    DiceCube(value = viewModel.diceValue2, isRolling = false, themeId = theme)
                }
            }
        }
    }
}

@Composable
private fun DiceFace(value: Int, isRolling: Boolean) {
    val dots = when (value) {
        1 -> listOf(true, false, false, false, false, false, false, false, true)
        2 -> listOf(false, false, true, false, false, false, true, false, false)
        3 -> listOf(true, false, false, false, true, false, false, false, true)
        4 -> listOf(true, false, true, false, false, false, true, false, true)
        5 -> listOf(true, false, true, false, true, false, true, false, true)
        6 -> listOf(true, false, true, true, false, true, true, false, true)
        else -> listOf(false, false, false, false, false, false, false, false, false)
    }
    Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(6.dp)) {
            dots.chunked(3).forEach { row ->
                Row(modifier = Modifier.weight(1f)) {
                    row.forEach { filled ->
                        Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (filled) Surface(modifier = Modifier.size(8.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LotteryDiceCube(
    value: Int,
    isRolling: Boolean,
    themeId: String = "warm",
    isLeftPlaySide: Boolean = false
) {
    val id = themeId.lowercase()

    val rotX by animateFloatAsState(
        targetValue = if (isRolling) 1260f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotY by animateFloatAsState(
        targetValue = if (isRolling) 1440f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val rotZ by animateFloatAsState(
        targetValue = if (isRolling) 1080f else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val dropY by animateFloatAsState(
        targetValue = if (isRolling) -450f else 0f,
        animationSpec = if (isRolling) {
            tween(120, easing = LinearEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    val shakeX by animateFloatAsState(
        targetValue = if (isRolling) (if (isLeftPlaySide) -20f else 20f) else 0f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(280, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
        }
    )

    val scaleFactor by animateFloatAsState(
        targetValue = if (isRolling) 1.4f else 1.1f,
        animationSpec = if (isRolling) {
            infiniteRepeatable(
                animation = tween(240, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        }
    )

    val containerColor = when (id) {
        "hacker" -> Color.Black
        "bw" -> Color.White
        "fire" -> Color(0xFF4A1200)
        "water" -> Color(0xFF001E3D)
        "cosmic" -> Color(0xFF131124)
        "rainbow" -> Color(0xFF2E0943)
        else -> Color.White
    }

    val borderColor = when (id) {
        "hacker" -> Color(0xFF00FF33)
        "bw" -> Color.Black
        "fire" -> Color(0xFFFF5722)
        "water" -> Color(0xFF00E5FF)
        "cosmic" -> Color(0xFF00FFCC)
        "rainbow" -> Color(0xFFFF00FF)
        else -> Color.LightGray
    }

    val dotColor = when (id) {
        "hacker" -> Color(0xFF00FF33)
        "bw" -> Color.Black
        "fire" -> Color(0xFFFFC107)
        "water" -> Color(0xFF80DEEA)
        "cosmic" -> Color(0xFFFF007F)
        "rainbow" -> Color(0xFF00FFFF)
        else -> Color.Black
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                rotationZ = rotZ
                translationX = shakeX * density
                translationY = dropY * density
                scaleX = scaleFactor
                scaleY = scaleFactor
                cameraDistance = 14f * density
            }
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .shadow(if (isRolling) 16.dp else 4.dp, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val d = size.width
                val r = 2.8.dp.toPx()
                val color = dotColor

                when (value) {
                    1 -> {
                        drawCircle(color, r, Offset(d/2, d/2))
                    }
                    2 -> {
                        drawCircle(color, r, Offset(d/4, d/4))
                        drawCircle(color, r, Offset(3*d/4, 3*d/4))
                    }
                    3 -> {
                        drawCircle(color, r, Offset(d/4, d/4))
                        drawCircle(color, r, Offset(d/2, d/2))
                        drawCircle(color, r, Offset(3*d/4, 3*d/4))
                    }
                    4 -> {
                        drawCircle(color, r, Offset(d/4, d/4))
                        drawCircle(color, r, Offset(3*d/4, d/4))
                        drawCircle(color, r, Offset(d/4, 3*d/4))
                        drawCircle(color, r, Offset(3*d/4, 3*d/4))
                    }
                    5 -> {
                        drawCircle(color, r, Offset(d/4, d/4))
                        drawCircle(color, r, Offset(3*d/4, d/4))
                        drawCircle(color, r, Offset(d/2, d/2))
                        drawCircle(color, r, Offset(d/4, 3*d/4))
                        drawCircle(color, r, Offset(3*d/4, 3*d/4))
                    }
                    6 -> {
                        drawCircle(color, r, Offset(d/4, d/4))
                        drawCircle(color, r, Offset(3*d/4, d/4))
                        drawCircle(color, r, Offset(d/4, d/2))
                        drawCircle(color, r, Offset(3*d/4, d/2))
                        drawCircle(color, r, Offset(d/4, 3*d/4))
                        drawCircle(color, r, Offset(3*d/4, 3*d/4))
                    }
                }
            }
        }
    }
}

@Composable
fun LotteryStatusMessage(viewModel: GameViewModel) {
    val lang = viewModel.selectedLanguage
    val status = viewModel.gameStatus
    val isRolling = viewModel.isRollingDice || viewModel.isOnline1UserRolling || viewModel.isOnline1BotRolling || viewModel.isOnline2WhiteRolling || viewModel.isOnline2BlackRolling

    val titleText: String
    val descText: String

    if (status == GameStatus.LOT_STAGE1) {
        titleText = Language.get("lot_stage1_title", lang)
        descText = when {
            isRolling -> {
                if (lang == "RU") "Кубики летят на доску..." else "Dices are falling..."
            }
            viewModel.isOnlinePlayActive -> {
                if (viewModel.onlineLot1UserRolled && viewModel.onlineLot1BotRolled) {
                    val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                    if (isTie) {
                        Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Нажмите на доску" else "Tap board")
                    } else {
                        val localWonColors = viewModel.diceLot1User > viewModel.diceLot1Bot
                        val wonText = if (localWonColors) {
                            if (lang == "RU") "Вы выиграли Белые! Нажмите, чтобы продолжить." else "You won White! Tap board to continue."
                        } else {
                            if (lang == "RU") "Соперник выиграл Белые! Нажмите, чтобы продолжить." else "Opponent won White! Tap board to continue."
                        }
                        wonText
                    }
                } else if (viewModel.onlineLot1UserRolled) {
                    if (lang == "RU") "Ожидаем бросок соперника..." else "Waiting for opponent roll..."
                } else {
                    if (lang == "RU") "Коснитесь доски, чтобы бросить кубик!" else "Touch the board to drop your die!"
                }
            }
            viewModel.diceLot1User > 0 && viewModel.diceLot1Bot > 0 -> {
                val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                if (isTie) {
                    Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы перебросить" else "Touch board to reroll")
                } else {
                    val userWon = viewModel.diceLot1User > viewModel.diceLot1Bot
                    val winText = if (!viewModel.isBotOpponentEnabled) {
                        if (userWon) {
                            if (lang == "RU") "Игрок 1 выиграл Белые! Нажмите для следующего этапа." else "Player 1 won White! Touch board to proceed."
                        } else {
                            if (lang == "RU") "Игрок 2 выиграл Белые! Нажмите для следующего этапа." else "Player 2 won White! Touch board to proceed."
                        }
                    } else {
                        if (userWon) Language.get("lot_result_winner_colors_white", lang) else Language.get("lot_result_winner_colors_black", lang)
                    }
                    winText + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы продолжить" else "Touch board to proceed")
                }
            }
            else -> {
                if (lang == "RU") "Коснитесь доски, чтобы бросить кубики!" else "Touch the board to drop your dice!"
            }
        }
    } else if (status == GameStatus.LOT_STAGE2) {
        titleText = Language.get("lot_stage2_title", lang)
        descText = when {
            isRolling -> {
                if (lang == "RU") "Кубики летят на доску..." else "Dices are falling..."
            }
            viewModel.isOnlinePlayActive -> {
                if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled) {
                    val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                    if (isTie) {
                        Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Нажмите на доску" else "Tap board")
                    } else {
                        val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                        val winningColor = if (whiteWon) Player.WHITE else Player.BLACK
                        val isLocalTurnWinner = viewModel.localPlayerColor == winningColor
                        val winText = if (isLocalTurnWinner) {
                            if (lang == "RU") "Вы ходите первыми! Нажмите, чтобы начать." else "You go first! Tap board to begin."
                        } else {
                            if (lang == "RU") "Соперник ходит первым! Нажмите, чтобы начать." else "Opponent goes first! Tap board to begin."
                        }
                        winText
                    }
                } else {
                    if (viewModel.localPlayerColor == Player.WHITE) {
                        if (!viewModel.onlineLot2WhiteRolled) {
                            if (lang == "RU") "Коснитесь доски, чтобы бросить Белый кубик!" else "Touch board to drop White die!"
                        } else {
                            if (lang == "RU") "Ожидаем бросок Черного кубика..." else "Waiting for Black die roll..."
                        }
                    } else {
                        if (!viewModel.onlineLot2WhiteRolled) {
                            if (lang == "RU") "Ожидаем бросок соперника..." else "Waiting for opponent to drop..."
                        } else if (!viewModel.onlineLot2BlackRolled) {
                            if (lang == "RU") "Коснитесь доски, чтобы бросить Черный кубик!" else "Touch board to drop Black die!"
                        } else {
                            ""
                        }
                    }
                }
            }
            viewModel.diceLot2White > 0 && viewModel.diceLot2Black > 0 -> {
                val isTie = viewModel.diceLot2White == viewModel.diceLot2Black
                if (isTie) {
                    Language.get("lot_result_tie", lang) + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы перебросить" else "Touch board to reroll")
                } else {
                    val whiteWon = viewModel.diceLot2White > viewModel.diceLot2Black
                    val winnerColor = if (whiteWon) Player.WHITE else Player.BLACK
                    val firstMoveText = if (!viewModel.isBotOpponentEnabled) {
                        val localWinnerName = if (winnerColor == viewModel.humanPlayerColor) {
                            Language.get("p2p_player1", lang).replace(" (Белые)", "")
                        } else {
                            Language.get("p2p_player2", lang).replace(" (Черные)", "")
                        }
                        if (lang == "RU") {
                            "Жеребьёвка завершена! $localWinnerName выиграл(а) первый ход (%1\$s против %2\$s)."
                        } else {
                            "Lottery complete! $localWinnerName won first turn (%1\$s vs %2\$s)."
                        }
                    } else {
                        val winningVisualColor = if (whiteWon) viewModel.humanPlayerColor else viewModel.botPlayerColor
                        if (winningVisualColor == Player.WHITE) {
                            Language.get("lot_result_first_move_white", lang)
                        } else {
                            Language.get("lot_result_first_move_black", lang)
                        }
                    }
                    val formatted = firstMoveText
                        .replace("%1\$s", viewModel.diceLot2White.toString())
                        .replace("%2\$s", viewModel.diceLot2Black.toString())
                    
                    formatted + "\n" + (if (lang == "RU") "Коснитесь доски, чтобы начать игру!" else "Touch board to start the game!")
                }
            }
            else -> {
                if (lang == "RU") "Коснитесь доски, чтобы определить первый ход!" else "Touch board to drop dice for first move!"
            }
        }
    } else {
        titleText = ""
        descText = ""
    }

    if (titleText.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xEE111A24)
            ),
            border = BorderStroke(1.dp, Color(0xFFD3A373).copy(alpha = 0.5f)),
            modifier = Modifier
                .wrapContentWidth()
                .padding(top = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = titleText.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFD3A373),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
