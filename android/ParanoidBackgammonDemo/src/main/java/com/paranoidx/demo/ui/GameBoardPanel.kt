package com.paranoidx.demo.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paranoidx.demo.game.*

data class BoardUiState(
    val points: List<CheckerStack>,
    val bar: Map<Player, Int>,
    val activePlayer: Player,
    val remainingDice: List<Int>,
    val gameStatus: GameStatus,
    val winner: Player?,
    val selectedPoint: Int? = null,
    val reachablePaths: List<ReachablePath> = emptyList(),
    val humanColor: Player = Player.WHITE,
    val ruleSet: RuleSet = RuleSet.CRAZY
)

@Composable
fun BackgammonBoard(
    state: BoardUiState,
    onPointClick: (Int) -> Unit,
    onRollClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spec = BoardColors()
    val canRoll = state.gameStatus == GameStatus.BEFORE_ROLL
    val isOver = state.gameStatus == GameStatus.GAME_OVER

    Box(
        modifier = modifier
            .aspectRatio(1.8f)
            .background(spec.boardBg, RoundedCornerShape(12.dp))
            .border(4.dp, spec.boardBorder, RoundedCornerShape(12.dp))
            .shadow(6.dp, RoundedCornerShape(12.dp))
    ) {
        // Board content
        Row(modifier = Modifier.fillMaxSize()) {
            // Left half: points 12-17 (top), 6-11 (bottom)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in 12..17) {
                        PointCell(
                            absIndex = i, pointingUp = false,
                            state = state, spec = spec,
                            onClick = { onPointClick(state.absoluteToRel(i) ?: -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in (6..11).reversed()) {
                        PointCell(
                            absIndex = i, pointingUp = true,
                            state = state, spec = spec,
                            onClick = { onPointClick(state.absoluteToRel(i) ?: -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            // Center bar
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
                    .background(spec.boardBorder.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(10.dp, 4.dp)
                                .background(spec.hingeColor, CircleShape)
                        )
                    }
                }
            }
            // Right half: points 18-23 (top), 0-5 (bottom)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in 18..23) {
                        PointCell(
                            absIndex = i, pointingUp = false,
                            state = state, spec = spec,
                            onClick = { onPointClick(state.absoluteToRel(i) ?: -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (i in (0..5).reversed()) {
                        PointCell(
                            absIndex = i, pointingUp = true,
                            state = state, spec = spec,
                            onClick = { onPointClick(state.absoluteToRel(i) ?: -1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Dice / Roll prompt overlay
        if (canRoll || isOver) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (canRoll) {
                    Box(
                        modifier = Modifier
                            .background(Color(0x88000000), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                            .clickable { onRollClick() }
                    ) {
                        Text("🎲 Roll Dice", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (isOver) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(16.dp))
                            .padding(32.dp)
                    ) {
                        Text(
                            "🏆 ${state.winner?.label() ?: "???"} Wins!",
                            color = Color.Yellow, fontSize = 28.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PointCell(
    absIndex: Int,
    pointingUp: Boolean,
    state: BoardUiState,
    spec: BoardColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stack = state.points[absIndex]
    val isDark = absIndex % 2 == 0
    val triangleColor = if (isDark) spec.darkTriangle else spec.lightTriangle
    val glowAnim by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse)
    )

    Box(modifier = modifier.fillMaxHeight().clickable(onClick = onClick)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                if (pointingUp) {
                    moveTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                } else {
                    moveTo(size.width / 2f, size.height)
                    lineTo(size.width, 0f)
                    lineTo(0f, 0f)
                }
                close()
            }
            drawPath(path, triangleColor)
        }

        // Checkers
        if (stack.count > 0 && stack.player != null) {
            val drawCount = minOf(stack.count, 6)
            val isHuman = stack.player == state.humanColor
            Box(modifier = Modifier.fillMaxSize(),
                contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (pointingUp) Arrangement.Bottom else Arrangement.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(140.dp),
                        contentAlignment = if (pointingUp) Alignment.BottomCenter else Alignment.TopCenter
                    ) {
                        val stepY = if (drawCount > 4) 22.dp else 28.dp
                        for (i in 0 until drawCount) {
                            val offset = if (pointingUp) -(i * stepY.value).dp else (i * stepY.value).dp
                            val checkerMod = Modifier
                                .size(38.dp)
                                .offset(y = offset)
                            CheckerPiece(
                                player = stack.player,
                                isHuman = isHuman,
                                modifier = checkerMod,
                                showLabel = i == drawCount - 1 && stack.count > 6,
                                labelCount = stack.count
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckerPiece(
    player: Player,
    isHuman: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    labelCount: Int = 0
) {
    val color = if (player == Player.WHITE) Color(0xFFF5F0E0) else Color(0xFF1A1A2E)
    val border = if (player == Player.WHITE) Color(0xFFCCCCCC) else Color(0xFF444466)
    val accent = if (isHuman) Color(0xFF4FC3F7) else Color(0xFFFF7043)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(color, color.copy(alpha = 0.7f), border),
                    center = Offset(5f, 5f)
                )
            )
            .border(1.5.dp, border, CircleShape)
            .shadow(2.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.55f)
                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape)
        )
        if (showLabel) {
            Text(labelCount.toString(), fontSize = 10.sp, fontWeight = FontWeight.Black,
                color = if (player == Player.WHITE) Color.Black else Color.White)
        }
    }
}

@Composable
fun DiceDisplay(dice: List<Int>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dice.forEach { value ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                    .border(2.dp, Color(0xFF8B6914), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val diceFaces = mapOf(
                    1 to "⚀", 2 to "⚁", 3 to "⚂",
                    4 to "⚃", 5 to "⚄", 6 to "⚅"
                )
                Text(diceFaces[value] ?: "$value", fontSize = 24.sp)
            }
        }
    }
}

private class BoardColors {
    val boardBg = Color(0xFF2C1E14)
    val boardBorder = Color(0xFF1E130B)
    val hingeColor = Color(0xFFB08D57)
    val darkTriangle = Color(0xFF3F2D1E)
    val lightTriangle = Color(0xFFB38D64)
}

private fun BoardUiState.absoluteToRel(abs: Int): Int? {
    val engine = GameEngine(ruleSet)
    return engine.absoluteToRelative(activePlayer, abs)
}
