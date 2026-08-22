package com.paranoidx.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paranoidx.demo.game.*
import com.paranoidx.demo.network.GameP2PManager
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    ruleSet: RuleSet,
    p2pManager: GameP2PManager? = null,
    onBack: () -> Unit
) {
    val engine = remember { GameEngine(ruleSet) }
    engine.resetGame()

    var gameStatus by remember { mutableStateOf(engine.gameStatus) }
    var board by remember { mutableStateOf(engine.board.toList()) }
    var remainingDice by remember { mutableStateOf(engine.remainingDice.toList()) }
    var activePlayer by remember { mutableStateOf(engine.activePlayer) }
    var winner by remember { mutableStateOf(engine.winner) }
    var selectedPoint by remember { mutableStateOf<Int?>(null) }
    var reachablePaths by remember { mutableStateOf<List<ReachablePath>>(emptyList()) }
    var logs by remember { mutableStateOf(engine.logs.toList()) }
    var turnKey by remember { mutableStateOf(0) }
    var p2pStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun syncState() {
        gameStatus = engine.gameStatus
        board = engine.board.toList()
        remainingDice = engine.remainingDice.toList()
        activePlayer = engine.activePlayer
        winner = engine.winner
        logs = engine.logs.toList()
    }

    // Bot auto-play
    LaunchedEffect(turnKey) {
        if (engine.gameStatus == GameStatus.GAME_OVER) return@LaunchedEffect
        // Don't auto-play in P2P mode — wait for network moves
        if (p2pManager != null) return@LaunchedEffect

        var safety = 50
        while (engine.activePlayer != engine.humanPlayerColor
            && engine.gameStatus == GameStatus.PLAYER_MOVE && safety > 0) {
            kotlinx.coroutines.delay(400)
            val move = engine.selectBestBotMove()
            if (move != null) {
                engine.makeMove(move)
                syncState()
            } else break
            safety--
        }
    }

    // P2P move listener
    LaunchedEffect(turnKey, p2pManager) {
        if (p2pManager == null || !p2pManager.isConnected) return@LaunchedEffect
        if (engine.gameStatus == GameStatus.GAME_OVER) return@LaunchedEffect

        while (engine.activePlayer != engine.humanPlayerColor
            && engine.gameStatus == GameStatus.PLAYER_MOVE) {
            val move = p2pManager.receiveMove()
            if (move != null) {
                val (from, to, _) = move
                // Find legal move matching received coordinates
                val legal = engine.getAllLegalMoves(activePlayer, remainingDice)
                val match = legal.find { it.from == from && it.to == to }
                if (match != null) {
                    engine.makeMove(match)
                    engine.addLogEntry("P2P move: $from→$to")
                    syncState()
                } else {
                    p2pStatus = "⚠️ Invalid opponent move $from→$to"
                    break
                }
            } else {
                kotlinx.coroutines.delay(200)
                if (!p2pManager.isConnected) break
            }
        }
    }

    val isP2pGame = p2pManager?.isConnected == true
    val uiState = BoardUiState(
        points = board,
        bar = engine.bar.toMap(),
        activePlayer = activePlayer,
        remainingDice = remainingDice,
        gameStatus = gameStatus,
        winner = winner,
        selectedPoint = selectedPoint,
        reachablePaths = reachablePaths,
        ruleSet = ruleSet
    )

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("←", fontSize = 18.sp) }
            Text(
                "${ruleSet.displayName} — ${activePlayer.label()} turn",
                style = MaterialTheme.typography.titleSmall
            )
            if (isP2pGame) Text("🌐", fontSize = 18.sp) else Spacer(Modifier.width(30.dp))
        }

        if (p2pStatus.isNotEmpty()) {
            Text(p2pStatus, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        // Dice
        if (remainingDice.isNotEmpty()) {
            DiceDisplay(
                dice = remainingDice,
                modifier = Modifier.padding(vertical = 4.dp).align(Alignment.CenterHorizontally)
            )
        }

        // Board
        BackgammonBoard(
            state = uiState,
            onPointClick = { relIdx ->
                if (gameStatus != GameStatus.PLAYER_MOVE) return@BackgammonBoard
                if (activePlayer != engine.humanPlayerColor) return@BackgammonBoard

                if (selectedPoint == null) {
                    val moves = engine.getAllLegalMoves(activePlayer, remainingDice)
                    if (moves.any { it.from == relIdx }) {
                        selectedPoint = relIdx
                        reachablePaths = engine.findReachablePathsFrom(activePlayer, relIdx)
                    }
                } else {
                    val from = selectedPoint!!
                    val paths = reachablePaths.filter { it.finalTo == relIdx }
                    if (paths.isNotEmpty()) {
                        val path = paths.minByOrNull { it.steps.size }!!
                        val move = path.steps.first()
                        engine.makeMove(move)
                        engine.addLogEntry("Human: $from → $relIdx")
                        selectedPoint = null
                        reachablePaths = emptyList()
                        syncState()
                        // Send move via P2P if connected
                        if (isP2pGame) {
                            coroutineScope.launch {
                                p2pManager!!.sendMove(move.from, move.to, move.diceUsed)
                            }
                        }
                        turnKey++
                    } else if (relIdx != from) {
                        val moves = engine.getAllLegalMoves(activePlayer, remainingDice)
                        if (moves.any { it.from == relIdx }) {
                            selectedPoint = relIdx
                            reachablePaths = engine.findReachablePathsFrom(activePlayer, relIdx)
                        } else {
                            selectedPoint = null; reachablePaths = emptyList()
                        }
                    } else {
                        selectedPoint = null; reachablePaths = emptyList()
                    }
                }
            },
            onRollClick = {
                if (gameStatus != GameStatus.BEFORE_ROLL) return@BackgammonBoard
                engine.gameStatus = GameStatus.BEFORE_ROLL
                engine.roll((1..6).random(), (1..6).random())
                engine.addLogEntry("Roll: ${remainingDice.first()}${if (remainingDice.size > 1 && remainingDice.first() != remainingDice.last()) ",${remainingDice.last()}" else ""}")
                syncState()
                turnKey++
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Score
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("White: ${engine.borneOffCount[Player.WHITE]}/15", fontSize = 12.sp)
            Text("Black: ${engine.borneOffCount[Player.BLACK]}/15", fontSize = 12.sp)
            if (isP2pGame && p2pManager?.isConnected == true) {
                Text("🌐 Linked", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Log (compact)
        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            LazyColumn(modifier = Modifier.height(80.dp)) {
                items(logs.takeLast(10)) { entry ->
                    Text(entry.message, fontSize = 9.sp, maxLines = 1)
                }
            }
        }

        // New game
        if (gameStatus == GameStatus.GAME_OVER) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    engine.resetGame()
                    selectedPoint = null; reachablePaths = emptyList()
                    syncState()
                    turnKey = 0
                    p2pStatus = ""
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("New Game")
            }
        }
    }
}
