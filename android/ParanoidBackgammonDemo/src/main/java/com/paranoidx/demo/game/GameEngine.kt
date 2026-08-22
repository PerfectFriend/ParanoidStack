package com.paranoidx.demo.game

/** Player: white or black checkers */
enum class Player {
    WHITE, BLACK;

    fun other(): Player = if (this == WHITE) BLACK else WHITE
    fun label(): String = if (this == WHITE) "White" else "Black"
}

/** Game lifecycle status */
enum class GameStatus {
    LOT_STAGE1, LOT_STAGE2, BEFORE_ROLL, PLAYER_MOVE, GAME_OVER
}

/** 3 supported backgammon rule sets */
enum class RuleSet(val displayName: String) {
    LONG("Длинные нарды"),
    SHORT("Короткие нарды"),
    CRAZY("CrazyGammon")
}

/** A stack of checkers on one point (null player = empty) */
data class CheckerStack(val count: Int, val player: Player?)

/** A single move in relative coordinates */
data class Move(
    val from: Int,        // 0..23 relative
    val to: Int,          // 1..24 (24 = bear off)
    val diceUsed: Int,
    val boardHash: String? = null
)

/** Reachable path for UI highlighting */
data class ReachablePath(val finalTo: Int, val steps: List<Move>)

/** Game log entry */
data class GameLogEntry(val timestampString: String, val message: String)

/** AI difficulty levels for the bot opponent */
enum class AIDifficulty {
    EASY,   // Random legal moves
    MEDIUM, // Scored moves with noise
    HARD    // Best deterministic move
}

/** A single backgammon move */
data class BarState(val count: Int)

/** Core game engine supporting 3 rule sets */
class GameEngine(val ruleSet: RuleSet = RuleSet.CRAZY) {

    var board = MutableList(24) { CheckerStack(0, null) }
        private set

    var activePlayer: Player = Player.WHITE
        private set

    var roller: Player = Player.WHITE
        private set

    val remainingDice = mutableListOf<Int>()
    val doubleWaterfallQueue = mutableListOf<List<Int>>()

    var gameStatus: GameStatus = GameStatus.BEFORE_ROLL
        private set

    var winner: Player? = null
        private set

    var borneOffCount = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)
        private set

    val logs = mutableListOf<GameLogEntry>()

    // Bar state (only used in SHORT rules — hit checkers)
    var bar = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)
        private set

    var headTakesThisTurn = 0
        private set

    var humanPlayerColor: Player = Player.WHITE
    var difficulty: AIDifficulty = AIDifficulty.HARD
    var diceLot1User = 0
    var diceLot1Bot = 0
    var diceLot2White = 0
    var diceLot2Black = 0

    init { resetGame() }

    fun resetGame() {
        board = MutableList(24) { CheckerStack(0, null) }
        activePlayer = Player.WHITE
        roller = Player.WHITE
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        headTakesThisTurn = 0
        borneOffCount[Player.WHITE] = 0
        borneOffCount[Player.BLACK] = 0
        bar[Player.WHITE] = 0
        bar[Player.BLACK] = 0
        gameStatus = GameStatus.BEFORE_ROLL
        winner = null
        logs.clear()
        humanPlayerColor = Player.WHITE
        diceLot1User = 0
        diceLot1Bot = 0
        diceLot2White = 0
        diceLot2Black = 0
        setupInitialBoard()
        addLogEntry("New game started — ${ruleSet.displayName}")
    }

    private fun setupInitialBoard() {
        when (ruleSet) {
            RuleSet.CRAZY -> {
                // N2 style: white all at 23, black all at 11
                board[23] = CheckerStack(15, Player.WHITE)
                board[11] = CheckerStack(15, Player.BLACK)
            }
            RuleSet.SHORT -> {
                // Standard backgammon setup (0-indexed)
                // White: 2@24→23, 5@13→12, 3@8→7, 5@6→5
                // Black: 2@1→0, 5@12→11, 3@17→16, 5@19→18
                board[23] = CheckerStack(2, Player.WHITE)
                board[12] = CheckerStack(5, Player.WHITE)
                board[7] = CheckerStack(3, Player.WHITE)
                board[5] = CheckerStack(5, Player.WHITE)
                board[0] = CheckerStack(2, Player.BLACK)
                board[11] = CheckerStack(5, Player.BLACK)
                board[16] = CheckerStack(3, Player.BLACK)
                board[18] = CheckerStack(5, Player.BLACK)
            }
            RuleSet.LONG -> {
                // Long backgammon: all 15 on one point
                board[0] = CheckerStack(15, Player.WHITE)  // white all at 0
                board[23] = CheckerStack(15, Player.BLACK) // black all at opposite end (usually 0 as well in long, but different direction)
            }
        }
    }

    /** Convert relative (0-23) to absolute board index for a player */
    fun relativeToAbsolute(player: Player, rel: Int): Int {
        return when (ruleSet) {
            RuleSet.CRAZY -> if (player == Player.WHITE) (23 - rel + 24) % 24
                             else (11 - rel + 24) % 24
            RuleSet.SHORT -> ((if (player == Player.WHITE) 23 else 0) - rel + 24) % 24
            RuleSet.LONG -> ((if (player == Player.WHITE) 23 else 0) - rel + 24) % 24
        }
    }

    /** Convert absolute index to relative for a player */
    fun absoluteToRelative(player: Player, abs: Int): Int {
        return when (ruleSet) {
            RuleSet.CRAZY -> if (player == Player.WHITE) (23 - abs + 24) % 24
                             else (11 - abs + 24) % 24
            RuleSet.SHORT -> ((if (player == Player.WHITE) 23 else 0) - abs + 24) % 24
            RuleSet.LONG -> ((if (player == Player.WHITE) 23 else 0) - abs + 24) % 24
        }
    }

    fun addLogEntry(message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        logs.add(GameLogEntry(sdf.format(java.util.Date()), message))
    }

    /** Roll dice and begin move phase */
    fun roll(d1: Int, d2: Int) {
        if (gameStatus != GameStatus.BEFORE_ROLL) return
        activePlayer = roller
        headTakesThisTurn = 0
        addLogEntry("🎲 ${roller.label()} rolls: $d1 + $d2")

        when (ruleSet) {
            RuleSet.CRAZY -> {
                if (d1 == d2) {
                    doubleWaterfallQueue.clear()
                    for (size in d1..6) {
                        doubleWaterfallQueue.add(listOf(size, size, size, size))
                    }
                    remainingDice.clear()
                    remainingDice.addAll(doubleWaterfallQueue.removeAt(0))
                    val chainStr = (d1..6).joinToString(" → ") { "4×$it" }
                    addLogEntry("🔥 CRAZY CUSH $d1:$d1! Cascade chain: $chainStr")
                } else {
                    doubleWaterfallQueue.clear()
                    remainingDice.clear()
                    remainingDice.addAll(listOf(d1, d2))
                }
            }
            RuleSet.SHORT -> {
                doubleWaterfallQueue.clear()
                remainingDice.clear()
                if (d1 == d2) {
                    // Double = 4 of the same value (no cascade in short)
                    remainingDice.addAll(listOf(d1, d1, d1, d1))
                    addLogEntry("🔥 Double $d1:$d1 — 4 moves")
                } else {
                    remainingDice.addAll(listOf(d1, d2))
                }
            }
            RuleSet.LONG -> {
                doubleWaterfallQueue.clear()
                remainingDice.clear()
                if (d1 == d2) {
                    // Long: double = 4 moves from any stock
                    remainingDice.addAll(listOf(d1, d1, d1, d1))
                    addLogEntry("💰 Double $d1:$d1 — 4 moves (Long rules)")
                } else {
                    remainingDice.addAll(listOf(d1, d2))
                }
            }
        }
        gameStatus = GameStatus.PLAYER_MOVE
        checkForNoMoves()
    }

    /** Main move execution */
    fun makeMove(move: Move): Boolean {
        if (gameStatus != GameStatus.PLAYER_MOVE) return false
        if (!isMoveLegal(activePlayer, move.from, move.diceUsed)) return false

        // Consume dice
        if (!remainingDice.remove(move.diceUsed)) return false

        val absFrom = relativeToAbsolute(activePlayer, move.from)
        val srcStack = board[absFrom]
        board[absFrom] = CheckerStack(srcStack.count - 1, if (srcStack.count - 1 == 0) null else activePlayer)

        if (move.from == 0) headTakesThisTurn++

        if (move.to >= 24) {
            // Bear off
            borneOffCount[activePlayer] = (borneOffCount[activePlayer] ?: 0) + 1
            addLogEntry("📤 ${activePlayer.label()} bears off from ${move.from + 1}")
            if (borneOffCount[activePlayer] == 15) {
                winner = activePlayer; gameStatus = GameStatus.GAME_OVER
                addLogEntry("🏆 ${activePlayer.label()} WINS!")
                return true
            }
        } else if (ruleSet == RuleSet.SHORT && isOpponentBlot(activePlayer, move.to)) {
            // Short: hitting opponent blot
            val absTo = relativeToAbsolute(activePlayer, move.to)
            val hitCount = board[absTo].count
            board[absTo] = CheckerStack(1, activePlayer)
            bar[activePlayer.other()] = (bar[activePlayer.other()] ?: 0) + hitCount
            addLogEntry("💥 ${activePlayer.label()} hits opponent at ${move.to + 1}!")
        } else if (ruleSet == RuleSet.LONG && move.to < 24) {
            // Long: simple move (no hitting)
            val absTo = relativeToAbsolute(activePlayer, move.to)
            val destStack = board[absTo]
            board[absTo] = CheckerStack(destStack.count + 1, activePlayer)
            addLogEntry("👉 ${activePlayer.label()}: ${move.from + 1} → ${move.to + 1}")
        } else if (move.to < 24) {
            val absTo = relativeToAbsolute(activePlayer, move.to)
            val destStack = board[absTo]
            board[absTo] = CheckerStack(destStack.count + 1, activePlayer)
            addLogEntry("👉 ${activePlayer.label()}: ${move.from + 1} → ${move.to + 1}")
        }

        // Waterfall cascade (CRAZY only)
        if (remainingDice.isEmpty() && doubleWaterfallQueue.isNotEmpty()) {
            loadNextWaterfallPhase()
        } else if (remainingDice.isEmpty()) {
            advanceTurn()
        } else {
            checkForNoMoves()
        }
        return true
    }

    private fun loadNextWaterfallPhase() {
        if (doubleWaterfallQueue.isEmpty()) { advanceTurn(); return }
        remainingDice.clear()
        remainingDice.addAll(doubleWaterfallQueue.removeAt(0))
        activePlayer = roller
        addLogEntry("⚡ Next cascade: $remainingDice")
        checkForNoMoves()
    }

    private fun advanceTurn() {
        roller = roller.other()
        activePlayer = roller
        headTakesThisTurn = 0
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        gameStatus = GameStatus.BEFORE_ROLL
        addLogEntry("🔄 Turn passes to ${roller.label()}")
    }

    // ---- Move Validation ----

    fun isMoveLegal(player: Player, fromRel: Int, dice: Int): Boolean {
        val toRel = fromRel + dice
        val absFrom = relativeToAbsolute(player, fromRel)
        val srcStack = board[absFrom]
        if (srcStack.player != player || srcStack.count == 0) return false

        return when (ruleSet) {
            RuleSet.CRAZY -> isMoveLegalCrazy(player, fromRel, toRel, dice)
            RuleSet.SHORT -> isMoveLegalShort(player, fromRel, toRel, dice)
            RuleSet.LONG -> isMoveLegalLong(player, fromRel, toRel, dice)
        }
    }

    private fun isMoveLegalCrazy(player: Player, fromRel: Int, toRel: Int, dice: Int): Boolean {
        // Bear off
        if (toRel >= 24) {
            if (!areAllInHome(player)) return false
            return toRel == 24 // exact only
        }
        // No internal home moves
        if (fromRel >= 18 && toRel < 24) return false
        // Destination check
        val absTo = relativeToAbsolute(player, toRel)
        val dest = board[absTo]
        if (dest.player != null && dest.player != player) return false
        // Head rule
        if (fromRel == 0 && player != roller) return false
        if (fromRel == 0 && ruleSet != RuleSet.LONG && headTakesThisTurn >= 1) return false
        // 6-block fence
        if (willBuildSixBlock(player, fromRel, toRel)) return false
        return true
    }

    private fun isMoveLegalShort(player: Player, fromRel: Int, toRel: Int, dice: Int): Boolean {
        if (toRel >= 24) {
            if (!areAllInHome(player)) return false
            return toRel == 24
        }
        val absTo = relativeToAbsolute(player, toRel)
        val dest = board[absTo]
        // Can land on: empty, own checkers, or at most 1 opponent checker (blot)
        if (dest.player != null && dest.player != player && dest.count > 1) return false
        return true
    }

    private fun isMoveLegalLong(player: Player, fromRel: Int, toRel: Int, dice: Int): Boolean {
        if (toRel >= 24) {
            if (!areAllInHome(player)) return false
            return true // any dice that reaches or passes home in long
        }
        val absTo = relativeToAbsolute(player, toRel)
        val dest = board[absTo]
        // Can't land where opponent has checkers
        if (dest.player != null && dest.player != player) return false
        // Head rule: only 1 from head per turn
        if (fromRel == 0 && headTakesThisTurn >= 1) return false
        return true
    }

    /** Check if an opponent has a single checker (blot) at the target position (Short rules) */
    private fun isOpponentBlot(player: Player, toRel: Int): Boolean {
        if (toRel >= 24) return false
        val absTo = relativeToAbsolute(player, toRel)
        val dest = board[absTo]
        return dest.player != null && dest.player != player && dest.count == 1
    }

    /** Check if all player's checkers are in the home zone */
    fun areAllInHome(player: Player): Boolean {
        for (r in 0..17) {
            val abs = relativeToAbsolute(player, r)
            if (board[abs].player == player) return false
        }
        return true
    }

    /** Get all legal moves for a player with given dice values */
    fun getAllLegalMoves(player: Player, diceValues: List<Int>): List<Move> {
        if (diceValues.isEmpty()) return emptyList()
        val uniqueDice = diceValues.distinct()
        val moves = mutableListOf<Move>()
        for (r in 0..23) {
            val abs = relativeToAbsolute(player, r)
            if (board[abs].player != player || board[abs].count == 0) continue
            for (d in uniqueDice) {
                if (isMoveLegal(player, r, d)) {
                    moves.add(Move(from = r, to = r + d, diceUsed = d))
                }
            }
        }
        return moves
    }

    /** Check and transfer turns if no legal moves exist */
    private fun checkForNoMoves() {
        if (gameStatus != GameStatus.PLAYER_MOVE) return
        val legals = getAllLegalMoves(activePlayer, remainingDice)
        if (legals.isNotEmpty()) return

        when (ruleSet) {
            RuleSet.CRAZY -> {
                if (doubleWaterfallQueue.isNotEmpty()) {
                    loadNextWaterfallPhase()
                } else {
                    addLogEntry("⚠️ ${activePlayer.label()} has no moves — turn ends")
                    remainingDice.clear()
                    advanceTurn()
                }
            }
            RuleSet.SHORT -> {
                addLogEntry("⚠️ ${activePlayer.label()} has no moves — turn ends")
                remainingDice.clear()
                advanceTurn()
            }
            RuleSet.LONG -> {
                addLogEntry("⚠️ ${activePlayer.label()} has no moves — turn ends")
                remainingDice.clear()
                advanceTurn()
            }
        }
    }

    /** Find reachable positions for UI highlighting */
    fun findReachablePathsFrom(player: Player, fromRel: Int): List<ReachablePath> {
        val results = mutableListOf<ReachablePath>()
        val steps = mutableListOf<Move>()
        val origBoard = board.map { it.copy() }
        val origDice = remainingDice.toList()
        val origHead = headTakesThisTurn

        fun search(curr: Int) {
            if (steps.isNotEmpty()) {
                val last = steps.last().to
                if (results.none { it.finalTo == last }) {
                    results.add(ReachablePath(last, steps.toList()))
                }
            }
            if (remainingDice.isEmpty() || curr >= 24) return
            for (d in remainingDice.distinct()) {
                if (!isMoveLegal(player, curr, d)) continue
                val absFrom = relativeToAbsolute(player, curr)
                val toRel = curr + d
                val prevFrom = board[absFrom]
                board[absFrom] = CheckerStack(prevFrom.count - 1, if (prevFrom.count - 1 == 0) null else player)
                if (curr == 0) headTakesThisTurn++
                if (!remainingDice.remove(d)) continue
                steps.add(Move(from = curr, to = toRel, diceUsed = d))
                search(toRel)
                steps.removeAt(steps.size - 1)
                remainingDice.add(d)
                headTakesThisTurn = origHead
                board[absFrom] = prevFrom
            }
        }
        try { search(fromRel) } finally {
            for (i in board.indices) board[i] = origBoard[i].copy()
            remainingDice.clear(); remainingDice.addAll(origDice)
            headTakesThisTurn = origHead
        }
        return results
    }

    /** Check if move creates illegal 6-block fence (CRAZY only) */
    private fun willBuildSixBlock(player: Player, fromRel: Int, toRel: Int): Boolean {
        if (ruleSet != RuleSet.CRAZY) return false
        // Simplified: clone board and check for 6 consecutive
        val tempBoard = board.toMutableList()
        val absFrom = relativeToAbsolute(player, fromRel)
        val absTo = if (toRel < 24) relativeToAbsolute(player, toRel) else -1
        val src = tempBoard[absFrom]
        tempBoard[absFrom] = CheckerStack(src.count - 1, if (src.count - 1 == 0) null else player)
        if (absTo >= 0) {
            val dest = tempBoard[absTo]
            tempBoard[absTo] = CheckerStack(dest.count + 1, player)
        }
        val occupied = BooleanArray(24) { i -> tempBoard[i].player == player }
        for (start in 0 until 24) {
            var seq = 0
            for (o in 0 until 6) { val idx = (start + o) % 24; if (occupied[idx]) seq++ else break }
            if (seq == 6) return true
        }
        return false
    }

    // ---- AI Bot ----

    fun selectBestBotMove(): Move? {
        val legals = getAllLegalMoves(activePlayer, remainingDice)
        if (legals.isEmpty()) return null
        when (difficulty) {
            AIDifficulty.EASY -> {
                // Pure random — least predictable, weakest
                return legals.random()
            }
            AIDifficulty.MEDIUM -> {
                // Scored with noise — occasionally suboptimal
                val scored = legals.map { it to scoreMove(it) }
                val maxScore = scored.maxOf { it.second }
                val threshold = maxScore * 0.7f // accept moves within 70% of best
                val candidates = scored.filter { it.second >= threshold }
                return candidates.random().first
            }
            AIDifficulty.HARD -> {
                return bestMove(legals)
            }
        }
    }

    /** Score a move from 0-100 for MEDIUM difficulty */
    private fun scoreMove(move: Move): Int {
        var score = 10
        val absTo = relativeToAbsolute(activePlayer, move.to)
        // Bonus: bearing off
        if (move.to >= 24) score += 30
        // Bonus: entering home
        if (move.to >= 18 && move.to < 24) score += 15
        // Bonus: moving from crowded point (5+ checkers)
        val fromCount = board[relativeToAbsolute(activePlayer, move.from)].count
        if (fromCount >= 5) score += 10
        // Bonus: moving to safe point (no opponent hit risk) — only SHORT rules
        if (ruleSet == RuleSet.SHORT) {
            if (board[absTo].count <= 1 || board[absTo].player == activePlayer) score += 8
        }
        return score
    }

    /** HARD mode: deterministic best move selection */
    private fun bestMove(legals: List<Move>): Move {
        // 1. Bear off priority
        val bearOff = legals.filter { it.to >= 24 }
        if (bearOff.isNotEmpty()) return bearOff.maxByOrNull { it.from }!!
        // 2. Move from head if crowded
        val headCheck = board[relativeToAbsolute(activePlayer, 0)]
        val headMoves = legals.filter { it.from == 0 }
        if (headMoves.isNotEmpty() && headCheck.count > 5) return headMoves.random()
        // 3. Enter home
        val enterHome = legals.filter { it.from < 18 && it.to >= 18 }
        if (enterHome.isNotEmpty()) return enterHome.maxByOrNull { it.to }!!
        // 4. Disperse from crowded points
        val fromClogged = legals.sortedByDescending { board[relativeToAbsolute(activePlayer, it.from)].count }
        if (fromClogged.isNotEmpty()) return fromClogged.first()
        return legals.random()
    }

    /** Board hash for P2P integrity */
    fun computeBoardHash(): String {
        val sb = StringBuilder()
        for (point in board) {
            sb.append(point.player?.name ?: "E").append(':').append(point.count).append(';')
        }
        sb.append(activePlayer.name).append(':')
        sb.append(remainingDice.joinToString(",")).append(':')
        sb.append(gameStatus.name)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sb.toString().encodeToByteArray())
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
