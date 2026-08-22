/**
 * Game board composables for "Crazy Backgammon".
 *
 * Renders the 24-point backgammon board with triangle cells, checker pieces, dice displays,
 * and lottery-stage overlays. Supports multiple visual themes via [BoardThemeSpecs].
 *
 * Key composables:
 * - [BackgammonBoardContainer] – the full board with click-to-roll/lot interaction.
 * - [PointCell] – a single triangular point that can hold stacked [CheckerPiece]s.
 * - [CheckerPiece] – a styled checker with radial gradient and border per theme.
 */
package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel
import com.example.model.GameStatus
import com.example.model.Player
import com.example.ui.components.BoardDiceDisplay
import com.example.ui.components.LotteryDiceCube
import com.example.ui.screens.LotteryStatusMessage

data class GameBoardState(
    val points: List<Int> = (0..23).map { 0 },
    val barPlayer: Int = 0,
    val barOpponent: Int = 0,
    val homePlayer: Int = 0,
    val homeOpponent: Int = 0,
    val boardOrientation: Boolean = true
)

class BoardThemeSpecs(themeId: String) {
    val id = themeId.lowercase()

    val boardBg: Color = when(id) {
        "bw" -> Color(0xFF121212)
        "rainbow" -> Color(0xFF1A052D)
        "fire" -> Color(0xFF1E0700)
        "water" -> Color(0xFF001122)
        "cosmic" -> Color(0xFF07050F)
        "hacker" -> Color(0xFF000000)
        else -> Color(0xFF2C1E14)
    }

    val boardBorder: Color = when(id) {
        "bw" -> Color(0xFF2E2E2E)
        "rainbow" -> Color(0xFF3B125C)
        "fire" -> Color(0xFF4A1200)
        "water" -> Color(0xFF002C5A)
        "cosmic" -> Color(0xFF211D36)
        "hacker" -> Color(0xFF0F1E0F)
        else -> Color(0xFF1E130B)
    }

    val hingeColor: Color = when(id) {
        "bw" -> Color(0xFF888888)
        "rainbow" -> Color(0xFFFF00FF)
        "fire" -> Color(0xFFFFC107)
        "water" -> Color(0xFF00E5FF)
        "cosmic" -> Color(0xFF00FFCC)
        "hacker" -> Color(0xFF00FF33)
        else -> Color(0xFFB08D57)
    }

    val darkTriangle: Color = when(id) {
        "bw" -> Color(0xFF222222)
        "rainbow" -> Color(0xFF2A0C4E)
        "fire" -> Color(0xFF310B00)
        "water" -> Color(0xFF001E3D)
        "cosmic" -> Color(0xFF131124)
        "hacker" -> Color(0xFF060D06)
        else -> Color(0xFF3F2D1E)
    }

    val lightTriangle: Color = when(id) {
        "bw" -> Color(0xFFBBBBBB)
        "rainbow" -> Color(0xFFBA55D3)
        "fire" -> Color(0xFFFF5722)
        "water" -> Color(0xFF00ACC1)
        "cosmic" -> Color(0xFFFF007F)
        "hacker" -> Color(0xFF005500)
        else -> Color(0xFFB38D64)
    }
}

@Composable
fun BackgammonBoardContainer(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val themeSpecs = remember(viewModel.selectedTheme) { BoardThemeSpecs(viewModel.selectedTheme) }
    val isLotteryStage = viewModel.gameStatus == GameStatus.LOT_STAGE1 || viewModel.gameStatus == GameStatus.LOT_STAGE2
    val canNormalRoll = viewModel.gameStatus == GameStatus.BEFORE_ROLL && !(viewModel.isBotOpponentEnabled && viewModel.activePlayer == Player.BLACK)

    Box(
        modifier = modifier
            .background(themeSpecs.boardBg, RoundedCornerShape(12.dp))
            .border(4.dp, themeSpecs.boardBorder, RoundedCornerShape(12.dp))
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .then(
                if (isLotteryStage || canNormalRoll) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isLotteryStage) {
                            val status = viewModel.gameStatus
                            if (status == GameStatus.LOT_STAGE1) {
                                if (viewModel.isOnlinePlayActive) {
                                    if (!viewModel.onlineLot1UserRolled && !viewModel.isOnline1UserRolling && !viewModel.isOnline1BotRolling) {
                                        viewModel.rollLot1OnlineLocal()
                                    } else if (viewModel.onlineLot1UserRolled && viewModel.onlineLot1BotRolled) {
                                        val isTie = viewModel.diceLot1User == viewModel.diceLot1Bot
                                        if (!isTie) viewModel.transitionToStage2()
                                    }
                                } else {
                                    if (viewModel.diceLot1User == 0 && !viewModel.isRollingDice) {
                                        viewModel.rollLot1()
                                    } else if (viewModel.diceLot1User > 0 && viewModel.diceLot1Bot > 0 && !viewModel.isRollingDice) {
                                        if (viewModel.diceLot1User == viewModel.diceLot1Bot) viewModel.rollLot1()
                                        else viewModel.transitionToStage2()
                                    }
                                }
                            } else if (status == GameStatus.LOT_STAGE2) {
                                if (viewModel.isOnlinePlayActive) {
                                    if (viewModel.localPlayerColor == Player.WHITE) {
                                        if (!viewModel.onlineLot2WhiteRolled && !viewModel.isOnline2WhiteRolling) viewModel.rollLot2OnlineLocalWhite()
                                        else if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled && viewModel.diceLot2White != viewModel.diceLot2Black) viewModel.applyLotStage2WinnerAndStart()
                                    } else {
                                        if (viewModel.onlineLot2WhiteRolled && !viewModel.onlineLot2BlackRolled && !viewModel.isOnline2BlackRolling) viewModel.rollLot2OnlineLocalBlack()
                                        else if (viewModel.onlineLot2WhiteRolled && viewModel.onlineLot2BlackRolled && viewModel.diceLot2White != viewModel.diceLot2Black) viewModel.applyLotStage2WinnerAndStart()
                                    }
                                } else {
                                    if (viewModel.diceLot2White == 0 && !viewModel.isRollingDice) viewModel.rollLot2()
                                    else if (viewModel.diceLot2White > 0 && viewModel.diceLot2Black > 0 && !viewModel.isRollingDice) {
                                        if (viewModel.diceLot2White == viewModel.diceLot2Black) viewModel.rollLot2()
                                        else viewModel.applyLotStage2WinnerAndStart()
                                    }
                                }
                            }
                        } else viewModel.rollDice()
                    }
                } else Modifier
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in 12..17) PointCell(absoluteIndex = i, pointingUp = false, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in downToRange(11, 6)) PointCell(absoluteIndex = i, pointingUp = true, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
            }
            Box(
                modifier = Modifier.width(18.dp).fillMaxHeight().background(themeSpecs.boardBorder).border(width = 1.dp, color = themeSpecs.boardBorder.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                    Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                    Box(modifier = Modifier.size(10.dp, 4.dp).background(themeSpecs.hingeColor))
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in 18..23) PointCell(absoluteIndex = i, pointingUp = false, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in downToRange(5, 0)) PointCell(absoluteIndex = i, pointingUp = true, viewModel = viewModel, modifier = Modifier.weight(1f))
                }
            }
        }
        if (isLotteryStage) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val lotValueLeft = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.diceLot1Bot else viewModel.diceLot2Black
                    val lotRollingLeft = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.isRollingDice || viewModel.isOnline1BotRolling
                        else viewModel.isRollingDice || viewModel.isOnline2BlackRolling
                    if (lotValueLeft > 0 || lotRollingLeft) {
                        LotteryDiceCube(value = if (lotValueLeft > 0) lotValueLeft else 1, isRolling = lotRollingLeft, themeId = viewModel.selectedTheme, isLeftPlaySide = true)
                    }
                }
                Spacer(modifier = Modifier.width(18.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val lotValueRight = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.diceLot1User else viewModel.diceLot2White
                    val lotRollingRight = if (viewModel.gameStatus == GameStatus.LOT_STAGE1) viewModel.isRollingDice || viewModel.isOnline1UserRolling
                        else viewModel.isRollingDice || viewModel.isOnline2WhiteRolling
                    if (lotValueRight > 0 || lotRollingRight) {
                        LotteryDiceCube(value = if (lotValueRight > 0) lotValueRight else 1, isRolling = lotRollingRight, themeId = viewModel.selectedTheme, isLeftPlaySide = false)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp, start = 8.dp, end = 8.dp), contentAlignment = Alignment.TopCenter) {
                LotteryStatusMessage(viewModel)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (viewModel.activePlayer == Player.BLACK && viewModel.gameStatus != GameStatus.GAME_OVER) BoardDiceDisplay(viewModel)
                }
                Spacer(modifier = Modifier.width(18.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (viewModel.activePlayer == Player.WHITE && viewModel.gameStatus != GameStatus.GAME_OVER) BoardDiceDisplay(viewModel)
                }
            }
        }
    }
}

private fun downToRange(start: Int, end: Int): IntArray {
    val size = start - end + 1
    val array = IntArray(size)
    var value = start
    for (i in 0 until size) { array[i] = value-- }
    return array
}

@Composable
fun PointCell(absoluteIndex: Int, pointingUp: Boolean, viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val relativeIndex = viewModel.absoluteToRelative(viewModel.activePlayer, absoluteIndex)
    val isSelected = viewModel.selectedPointIndex == relativeIndex
    val isHighlightedDestination = remember(viewModel.selectedPointIndex, viewModel.reachablePaths) { viewModel.reachablePaths.any { it.finalTo == relativeIndex } }
    val stack = viewModel.boardState[absoluteIndex]
    val themeSpecs = remember(viewModel.selectedTheme) { BoardThemeSpecs(viewModel.selectedTheme) }
    val darkTriangleColor = themeSpecs.darkTriangle
    val lightTriangleColor = themeSpecs.lightTriangle
    val triangleColor = if (absoluteIndex % 2 == 0) darkTriangleColor else lightTriangleColor
    val glowAnim by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Reverse)
    )
    Box(
        modifier = modifier.fillMaxHeight().clickable(onClick = { viewModel.handlePointClicked(relativeIndex) }).testTag("point_cell_$absoluteIndex")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trianglePath = Path().apply {
                if (pointingUp) { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                else { moveTo(size.width / 2f, size.height); lineTo(size.width, 0f); lineTo(0f, 0f); close() }
            }
            drawPath(path = trianglePath, color = triangleColor)
            if (isSelected) drawPath(path = trianglePath, color = Color.Green, style = Stroke(width = 3.dp.toPx()), alpha = glowAnim)
            else if (isHighlightedDestination) drawPath(path = trianglePath, color = Color.Cyan, style = Stroke(width = 3.dp.toPx()), alpha = glowAnim)
        }
        val outerNumber = absoluteIndex + 1
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter) {
            Text(text = outerNumber.toString(), fontSize = 8.sp, color = Color(0xFFA59283).copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
        }
        if (stack.count > 0 && stack.player != null) {
            val maxCheckersToDraw = 6
            val drawCount = if (stack.count > maxCheckersToDraw) maxCheckersToDraw else stack.count
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter) {
                Column(modifier = Modifier.padding(bottom = if (pointingUp) 8.dp else 0.dp, top = if (!pointingUp) 8.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (pointingUp) Arrangement.Bottom else Arrangement.Top) {
                    Box(modifier = Modifier.width(48.dp).height(165.dp), contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter) {
                        val stepY = if (drawCount > 4) 19.5.dp else 24.dp
                        for (i in 0 until drawCount) {
                            val offsetMultiplier = if (pointingUp) i else (drawCount - 1 - i)
                            val yOffset = if (pointingUp) -(offsetMultiplier * stepY.value).dp else (offsetMultiplier * stepY.value).dp
                            val visualColor = if (stack.player == Player.WHITE) viewModel.humanPlayerColor else viewModel.botPlayerColor
                            CheckerPiece(player = visualColor, themeId = viewModel.selectedTheme, modifier = Modifier.size(46.dp).offset(y = yOffset), showNumberLabel = (i == drawCount - 1 && stack.count > maxCheckersToDraw), labelCount = stack.count)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckerPiece(player: Player, themeId: String, modifier: Modifier = Modifier, showNumberLabel: Boolean = false, labelCount: Int = 0) {
    val id = themeId.lowercase()
    val pieceGradient = remember(player, id) {
        if (player == Player.WHITE) {
            val colors = when(id) {
                "bw" -> listOf(Color(0xFFFFFFFF), Color(0xFFE2E2E2), Color(0xFFCCCCCC))
                "rainbow" -> listOf(Color(0xFF00FFFF), Color(0xFF00FFCC), Color(0xFF2979FF))
                "fire" -> listOf(Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800))
                "water" -> listOf(Color(0xFFE0F7FA), Color(0xFF80DEEA), Color(0xFF00ACC1))
                "cosmic" -> listOf(Color(0xFF00FFCC), Color(0xFF00CFA2), Color(0xFF104E40))
                "hacker" -> listOf(Color(0xFF33FF33), Color(0xFF22DD22), Color(0xFF004400))
                else -> listOf(Color(0xFFFFFFFF), Color(0xFFE2DFD8), Color(0xFFC8C4B7))
            }
            Brush.radialGradient(colors = colors, center = Offset(10f, 10f))
        } else {
            val colors = when(id) {
                "bw" -> listOf(Color(0xFF333333), Color(0xFF1B1B1B), Color(0xFF000000))
                "rainbow" -> listOf(Color(0xFFFF00FF), Color(0xFFD500F9), Color(0xFF4A148C))
                "fire" -> listOf(Color(0xFFE64A19), Color(0xFFD84315), Color(0xFF1E0700))
                "water" -> listOf(Color(0xFF1565C0), Color(0xFF0D47A1), Color(0xFF000C1A))
                "cosmic" -> listOf(Color(0xFFFF007F), Color(0xFFC2185B), Color(0xFF3F0B20))
                "hacker" -> listOf(Color(0xFF002200), Color(0xFF001100), Color(0xFF000000))
                else -> listOf(Color(0xFF4C4C4E), Color(0xFF232325), Color(0xFF0F0F10))
            }
            Brush.radialGradient(colors = colors, center = Offset(10f, 10f))
        }
    }
    val borderColor = remember(player, id) {
        if (player == Player.WHITE) when(id) {
            "bw" -> Color(0xFFFFFFFF); "rainbow" -> Color(0xFF00FFFF); "fire" -> Color(0xFFFFEB3B)
            "water" -> Color(0xFFE0F7FA); "cosmic" -> Color(0xFF00FFCC); "hacker" -> Color(0xFF00FF33)
            else -> Color(0xFFAFAFAF)
        } else when(id) {
            "bw" -> Color(0xFF555555); "rainbow" -> Color(0xFFFF00FF); "fire" -> Color(0xFFFF1744)
            "water" -> Color(0xFF1A237E); "cosmic" -> Color(0xFFFF007F); "hacker" -> Color(0xFF004400)
            else -> Color(0xFFB08D57)
        }
    }
    val innerRingColor = remember(player, id) {
        if (player == Player.WHITE) when(id) { "bw" -> Color(0xFFCCCCCC); "hacker" -> Color(0xFF009900); else -> Color(0xFFCDCDCD) }
        else when(id) { "bw" -> Color(0xFF222222); "hacker" -> Color(0xFF002200); else -> Color(0xFF535355) }
    }
    Box(
        modifier = modifier.clip(CircleShape).background(pieceGradient)
            .border(width = 1.3.dp, color = borderColor, shape = CircleShape).shadow(2.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxSize(0.6f).border(width = 1.dp, color = innerRingColor, shape = CircleShape))
        if (showNumberLabel) Text(text = labelCount.toString(), fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (player == Player.WHITE) Color.Black else Color.White, textAlign = TextAlign.Center)
    }
}
