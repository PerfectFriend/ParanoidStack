/**
 * Core game engine for "Crazy Backgammon" (Сумасшедшие нарды).
 *
 * ## Architecture
 * The engine manages a 24-point backboard as a [MutableList] of [CheckerStack], with each player
 * owning 15 checkers. Coordinate mapping uses relative indices (0–23) per player:
 * - **White** starts at absolute index 23 and moves counter-clockwise toward index 0.
 * - **Black** starts at absolute index 11 and moves counter-clockwise toward index 12.
 *
 * ## Game flow
 * 1. **Lot stage** ([GameStatus.LOT_STAGE1], [GameStatus.LOT_STAGE2]) – determines colour and who goes first.
 * 2. **Before roll** ([GameStatus.BEFORE_ROLL]) – waiting for the roller to throw dice.
 * 3. **Player move** ([GameStatus.PLAYER_MOVE]) – executing moves with remaining dice values.
 * 4. **Game over** ([GameStatus.GAME_OVER]) – one player has borne off all 15 checkers.
 *
 * ### Kush (double) cascade
 * Rolling a double (e.g. 5:5) produces a waterfall queue: [5,5,5,5] then [6,6,6,6].
 * If the active player cannot move, the remaining dice cascade transfers to the opponent.
 *
 * ### Block rule
 * Building a contiguous block of 6 points occupied by the same player is illegal unless the
 * opponent has already crossed that block (has a checker beyond it in relative coordinates).
 *
 * @property board 24-point board indexed by absolute position.
 * @property activePlayer the player whose turn it currently is.
 * @property roller the player who performed the dice roll (may differ from activePlayer after transfer).
 * @property remainingDice dice values yet to be consumed in the current sub-phase.
 * @property doubleWaterfallQueue cascade queue for kush (double) rolls.
 * @property gameStatus current lifecycle status of the game.
 */
package com.example.model

import android.util.Log

/**
 * Игрок: белые (WHITE) или чёрные (BLACK).
 */
enum class Player {
    WHITE, BLACK;

    fun other(): Player = if (this == WHITE) BLACK else WHITE
    fun label(): String {
        return if (GameEngine.currentLanguage == "RU") {
            if (this == WHITE) "Белые" else "Черные"
        } else {
            if (this == WHITE) "White" else "Black"
        }
    }
}

/**
 * Состояние игры: жеребьёвка, ожидание броска, ход игрока, конец игры.
 */
enum class GameStatus {
    LOT_STAGE1,
    LOT_STAGE2,
    BEFORE_ROLL,
    PLAYER_MOVE, // Active player's turn to move checkers
    GAME_OVER
}

/**
 * Стопка шашек: количество и владелец (null — пустая клетка).
 */
data class CheckerStack(
    val count: Int,
    val player: Player?
)

/**
 * Ход в относительных координатах: from (0..23), to (1..24, где 24 = выход с доски), diceUsed — значение кубика.
 */
data class Move(
    val from: Int,
    val to: Int,
    val diceUsed: Int,
    val boardHash: String? = null  // SHA-256 hash of board state for online integrity verification
)

/**
 * Достижимый путь: конечная позиция и последовательность ходов.
 */
data class ReachablePath(
    val finalTo: Int,
    val steps: List<Move>
)

/**
 * Запись в логе игры: временная метка и сообщение.
 */
data class GameLogEntry(
    val timestampString: String,
    val message: String
)

/**
 * Игровой движок «Сумасшедшие нарды».
 * Управляет доской, ходами, кубами (включая каскадные куш-броски), жеребьёвкой и проверками правил.
 *
 * The engine supports:
 * - Relative ↔ absolute coordinate conversion ([relativeToAbsolute], [absoluteToRelative]).
 * - Legal-move validation with head-taking limits, home restrictions, and the 6-block fence rule.
 * - Kush (double) cascades with automatic turn transfer when no legal moves exist.
 * - Bot AI move selection via [selectBestBotMove].
 * - Reachable-path search for UI highlighting ([findReachablePathsFrom]).
 */
class GameEngine {

    companion object {
    /** Текущий язык интерфейса (EN/RU). */
    var currentLanguage: String = "EN"
    }

    /** Доска: 24 пункта в абсолютных индексах 0..23. */
    @Volatile
    var board = MutableList(24) { CheckerStack(0, null) }
        private set

    /** Текущий активный игрок (чей сейчас ход). */
    @Volatile
    var activePlayer: Player = Player.WHITE
        private set

    /** Игрок, который совершил бросок (не обязательно совпадает с activePlayer при передаче хода). */
    @Volatile
    var roller: Player = Player.WHITE
        private set

    /** Оставшиеся значения кубиков для текущей подфазы хода. */
    val remainingDice = mutableListOf<Int>()

    /** Очередь каскадных фаз для кушей (например, [[6,6,6,6]] при броске 5:5). */
    val doubleWaterfallQueue = mutableListOf<List<Int>>()

    /** Сколько шашек снято с головы (отн. индекс 0) каждым игроком в ТЕКУЩЕМ ходу. */
    val headTakesThisTurnMap = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)

    /** Сколько шашек снято с головы активным игроком. */
    val headTakesThisTurn: Int
        get() = headTakesThisTurnMap[activePlayer] ?: 0

    /** True, если активный игрок играет переданными ходами соперника. */
    @Volatile
    var isOpponentPlayingTransferred = false
        private set

    /** True, если только что был сделан свежий куш-бросок. */
    @Volatile
    var isFreshKushRoll = false
        private set

    /** Счётчик выброшенных с доски шашек для каждого игрока. */
    @Volatile
    var borneOffCount = mutableMapOf(Player.WHITE to 0, Player.BLACK to 0)
        private set

    /** Текущее состояние игры. */
    @Volatile
    var gameStatus: GameStatus = GameStatus.BEFORE_ROLL
        private set

    /** Победитель (заполняется при окончании игры). */
    @Volatile
    var winner: Player? = null
        private set

    /** Время начала текущего хода (unix timestamp). */
    @Volatile
    var turnStartTimeMillis: Long = 0
        private set

    /** Лог игры. */
    val logs = mutableListOf<GameLogEntry>()

    /** Флаг первого хода для каждого игрока (важно для исключения правила головы). */
    private val isFirstTurnMap = mutableMapOf(Player.WHITE to true, Player.BLACK to true)

    /** На первом ходу при куше — оригинальное значение кубика (3/4/5/6). 0 = не первый ход или не куш. */
    @Volatile
    var firstTurnKushDice: Int = 0
        private set

    /** История бросков кубиков. */
    private val diceRollHistory = mutableListOf<Pair<Int, Int>>()

    /** Цвет игрока-человека. */
    @Volatile
    var humanPlayerColor: Player = Player.WHITE

    /** Цвет бота (противоположный цвету человека). */
    val botPlayerColor: Player
        get() = humanPlayerColor.other()

    /** Значения кубиков на первом этапе жеребьёвки. */
    var diceLot1User: Int = 0
    var diceLot1Bot: Int = 0

    /** Значения кубиков на втором этапе жеребьёвки. */
    var diceLot2White: Int = 0
    var diceLot2Black: Int = 0

    init {
        resetGame()
    }

    /** Сбрасывает игру в начальное состояние: расставляет шашки, очищает кубы и логи. */
    fun resetGame() {
        board = MutableList(24) { CheckerStack(0, null) }
        
        // White starts in the top-right corner (absolute index 23)
        board[23] = CheckerStack(15, Player.WHITE)
        
        // Black starts in the bottom-left corner from White's view, which is Black's far-right (absolute index 11)
        board[11] = CheckerStack(15, Player.BLACK)

        activePlayer = Player.WHITE
        roller = Player.WHITE
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isOpponentPlayingTransferred = false
        isFreshKushRoll = false
        borneOffCount[Player.WHITE] = 0
        borneOffCount[Player.BLACK] = 0
        gameStatus = GameStatus.LOT_STAGE1
        winner = null
        turnStartTimeMillis = System.currentTimeMillis()
        logs.clear()
        isFirstTurnMap[Player.WHITE] = true
        isFirstTurnMap[Player.BLACK] = true
        firstTurnKushDice = 0
        diceRollHistory.clear()

        humanPlayerColor = Player.WHITE
        diceLot1User = 0
        diceLot1Bot = 0
        diceLot2White = 0
        diceLot2Black = 0

        addLogEntry("Новый сеанс игры начат. Требуется провести жеребьёвку!")
    }

    /** Переводит игру со 1-го этапа жеребьёвки на 2-й. */
    fun transitionToStage2() {
        if (gameStatus == GameStatus.LOT_STAGE1) {
            gameStatus = GameStatus.LOT_STAGE2
        }
    }

    /**
     * Кастомная жеребьёвка цвета (1-й этап) с заданными значениями кубиков.
     * @return true, если победитель определён, false — ничья, нужен переброс.
     */
    fun rollLotStage1Custom(userVal: Int, botVal: Int, oppName: String? = null): Boolean {
        diceLot1User = userVal
        diceLot1Bot = botVal
        
        val oppDisplay = oppName ?: "Бот"
        addLogEntry("🎲 Жеребьёвка цвета: Вы выбросили $diceLot1User, $oppDisplay выбросил $diceLot1Bot")
        
        if (diceLot1User > diceLot1Bot) {
            humanPlayerColor = Player.WHITE
            addLogEntry("⚪ Вы выиграли жеребьёвку! Цвет Ваших фишек — Белые. $oppDisplay играет Черными.")
            return true
        } else if (diceLot1User < diceLot1Bot) {
            humanPlayerColor = Player.BLACK
            addLogEntry("⚫ $oppDisplay выиграл жеребьёвку! Цвет Ваших фишек — Черные. $oppDisplay играет Белыми.")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке цветов! Переброс!")
            return false
        }
    }

    /**
     * Кастомная жеребьёвка первого хода (2-й этап) с заданными значениями кубиков.
     * @return true, если победитель определён, false — ничья.
     */
    fun rollLotStage2Custom(whiteVal: Int, blackVal: Int, oppName: String? = null): Boolean {
        diceLot2White = whiteVal
        diceLot2Black = blackVal
        
        val oppDisplay = oppName ?: "Бот"
        val whiteStr = if (humanPlayerColor == Player.WHITE) "Вы (Белые)" else "$oppDisplay (Белые)"
        val blackStr = if (humanPlayerColor == Player.WHITE) "$oppDisplay (Черные)" else "Вы (Черные)"
        
        addLogEntry("🎲 Жеребьёвка первого хода: $whiteStr выбросили $diceLot2White, $blackStr выбросили $diceLot2Black")
        
        if (diceLot2White > diceLot2Black) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ $whiteStr выиграли жеребьёвку первого хода!")
            return true
        } else if (diceLot2White < diceLot2Black) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ $blackStr выиграли жеребьёвку первого хода!")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке первого хода! Переброс!")
            return false
        }
    }

    /** Случайная жеребьёвка цвета (1-й этап) против бота. */
    fun rollLotStage1(): Boolean {
        diceLot1User = (1..6).random()
        diceLot1Bot = (1..6).random()
        
        addLogEntry("🎲 Жеребьёвка цвета: Вы выбросили $diceLot1User, Бот выбросил $diceLot1Bot")
        
        if (diceLot1User > diceLot1Bot) {
            humanPlayerColor = Player.WHITE
            addLogEntry("⚪ Вы выиграли жеребьёвку! Цвет Ваших фишек — Белые. Бот играет Черными.")
            return true
        } else if (diceLot1User < diceLot1Bot) {
            humanPlayerColor = Player.BLACK
            addLogEntry("⚫ Бот выиграл жеребьёвку! Цвет Ваших фишек — Черные. Бот играет Белыми.")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке цветов! Переброс!")
            return false
        }
    }

    /** Случайная жеребьёвка первого хода (2-й этап) против бота. */
    fun rollLotStage2(): Boolean {
        diceLot2White = (1..6).random()
        diceLot2Black = (1..6).random()
        
        val whiteStr = if (humanPlayerColor == Player.WHITE) "Вы (Белые)" else "Вы (Черные)"
        val blackStr = if (humanPlayerColor == Player.WHITE) "Бот (Черные)" else "Бот (Белые)"
        
        addLogEntry("🎲 Жеребьёвка первого хода: $whiteStr выбросили $diceLot2White, $blackStr выбросили $diceLot2Black")
        
        if (diceLot2White > diceLot2Black) {
            activePlayer = Player.WHITE
            roller = Player.WHITE
            addLogEntry("⚪ $whiteStr выиграли жеребьёвку первого хода!")
            return true
        } else if (diceLot2White < diceLot2Black) {
            activePlayer = Player.BLACK
            roller = Player.BLACK
            addLogEntry("⚫ $blackStr выиграли жеребьёвку первого хода!")
            return true
        } else {
            addLogEntry("🔄 Ничья в жеребьёвке первого хода! Переброс!")
            return false
        }
    }

    /** Применяет результаты 2-го этапа жеребьёвки и запускает игру. */
    fun applyLotStage2WinnerAndStart() {
        recordTurnStarted()
        isOpponentPlayingTransferred = false
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isFirstTurnMap[Player.WHITE] = true
        isFirstTurnMap[Player.BLACK] = true
        
        doubleWaterfallQueue.clear()
        remainingDice.clear()
        remainingDice.add(diceLot2White)
        remainingDice.add(diceLot2Black)
        
        val activeLabel = if (activePlayer == Player.WHITE) {
            "Игрок (${humanPlayerColor.label()})"
        } else {
            "Бот (${humanPlayerColor.other().label()})"
        }
        addLogEntry("🎮 Жеребьёвка завершена! Игра официально запущена. Ходит $activeLabel кубиками $diceLot2White:$diceLot2Black.")
        
        gameStatus = GameStatus.PLAYER_MOVE
        checkForLackOfMovesAndTransfer()
    }

    // Helper: Map relative coordinates (0 to 23) to absolute board indices (0 to 23)
    // White: starts at 23, moves counter-clockwise to 0. Path: 23, 22, ..., 0
    // Black: starts at 11, moves counter-clockwise to 12. Path: 11, 10, ..., 0, 23, ..., 12
    /**
     * Преобразует относительный индекс (0..23) в абсолютный (0..23) для данного игрока.
     * Белые: 23 → 0, Чёрные: 11 → 0.
     */
    fun relativeToAbsolute(player: Player, relativeIndex: Int): Int {
        return if (player == Player.WHITE) {
            (23 - relativeIndex + 24) % 24
        } else {
            (11 - relativeIndex + 24) % 24
        }
    }

    // Helper: Map absolute board indices to relative coordinates for a player
    /** Преобразует абсолютный индекс доски в относительный для указанного игрока. */
    fun absoluteToRelative(player: Player, absoluteIndex: Int): Int {
        return if (player == Player.WHITE) {
            (23 - absoluteIndex + 24) % 24
        } else {
            (11 - absoluteIndex + 24) % 24
        }
    }

    /** Добавляет запись в лог игры с временной меткой. */
    fun addLogEntry(message: String) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeString = sdf.format(java.util.Date())
        logs.add(GameLogEntry(timeString, message))
        Log.i("CrazyBackgammonEngine", "[$timeString] $message")
    }

    /** Фиксирует время начала хода в логе. */
    fun recordTurnStarted() {
        turnStartTimeMillis = System.currentTimeMillis()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeStr = sdf.format(java.util.Date(turnStartTimeMillis))
        addLogEntry("▶️ Начало хода игрока ${activePlayer.label()} зафиксировано автоматически в $timeStr.")
    }

    /**
     * Обрабатывает бросок кубиков и запускает фазу хода.
     * В случае дубля (куша) формирует каскадную очередь (например, 5:5 → [5,5,5,5] → [6,6,6,6]).
     */
    fun roll(d1: Int, d2: Int) {
        if (gameStatus != GameStatus.BEFORE_ROLL) return
        
        recordTurnStarted()
        isOpponentPlayingTransferred = false
        activePlayer = roller
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isFirstTurnMap[roller] = isFirstTurnMap[roller] == true && borneOffCount[roller] == 0 && countCheckersOutsideHead(roller) == 0

        addLogEntry("🎲 ${roller.label()} бросает зарики: $d1 и $d2")

        if (d1 == d2) {
            // It's a double (куш)!
            // Rules for double:
            // 6:6 -> Runs standard 4x6
            // 5:5 -> Runs [5,5,5,5] then [6,6,6,6]
            // D:D -> Runs sequences from D up to 6 sequentially!
            doubleWaterfallQueue.clear()
            for (size in d1..6) {
                doubleWaterfallQueue.add(listOf(size, size, size, size))
            }
            remainingDice.clear()
            remainingDice.addAll(doubleWaterfallQueue.removeAt(0))
            
            val chainStr = (d1..6).joinToString(" -> ") { "4x$it" }
            addLogEntry("🔥 КРЕЙЗИ КУШ $d1:$d1! Цепочка каскадов: $chainStr")
            isFreshKushRoll = true
        } else {
            // Standard non-double roll
            doubleWaterfallQueue.clear()
            remainingDice.clear()
            remainingDice.add(d1)
            remainingDice.add(d2)
            isFreshKushRoll = false
        }

        // На первом ходу при куше запоминаем оригинальное значение для правила головы
        firstTurnKushDice = if (isFirstTurnMap[roller] == true && d1 == d2) d1 else 0

        gameStatus = GameStatus.PLAYER_MOVE
        checkForLackOfMovesAndTransfer()
    }

    /** Counts how many of this player's checkers are outside the head position (relative index 0).
     * Used to determine if the first-turn head-exemption rule still applies. */
    private fun countCheckersOutsideHead(player: Player): Int {
        var count = 0
        for (r in 1..23) {
            val abs = relativeToAbsolute(player, r)
            if (board[abs].player == player) {
                count += board[abs].count
            }
        }
        return count
    }

    /**
     * Проверяет, есть ли у активного игрока легальные ходы.
     * Если нет — передаёт оставшиеся ходы сопернику или переходит к следующему каскаду/ходу.
     */
    fun checkForLackOfMovesAndTransfer() {
        if (gameStatus != GameStatus.PLAYER_MOVE) return

        val legalMoves = getAllLegalMoves(activePlayer, remainingDice)
        if (legalMoves.isEmpty() && remainingDice.isNotEmpty()) {
            val opponent = activePlayer.other()
            
            if (isFreshKushRoll) {
                addLogEntry("⛔ Выпал куш, но у игрока ${activePlayer.label()} нет возможности сделать первый ход! Ход аннулирован и завершен.")
                isFreshKushRoll = false
                remainingDice.clear()
                doubleWaterfallQueue.clear()
                isOpponentPlayingTransferred = false
                advanceTurn()
                return
            }

            // Waterfall check: if activePlayer is the roller who got stuck, or opponent who got stuck
            if (isOpponentPlayingTransferred) {
                // Opponent already took over, and got stuck too!
                addLogEntry("⚠️ У перехватившего игрока ${activePlayer.label()} тоже нет ходов с зариками $remainingDice!")
                if (doubleWaterfallQueue.isNotEmpty()) {
                    loadNextWaterfallPhase()
                } else {
                    remainingDice.clear()
                    advanceTurn()
                }
            } else {
                // Roller can't play! Transfer all leftovers to the opponent!
                isOpponentPlayingTransferred = true
                activePlayer = opponent
                
                addLogEntry("🔴 У игрока ${opponent.other().label()} нет ходов! Остаток ходов $remainingDice передан игроку ${opponent.label()}.")
                
                // Reset head taking rules for opponent
                headTakesThisTurnMap[opponent] = 0
                
                // Check if opponent can make moves with these transferred dice
                val opponentMoves = getAllLegalMoves(activePlayer, remainingDice)
                if (opponentMoves.isEmpty()) {
                    // Try to load next waterfall phase (if any is left) for the opponent
                    if (doubleWaterfallQueue.isNotEmpty()) {
                        loadNextWaterfallPhase()
                    } else {
                        addLogEntry("⚠️ У игрока ${activePlayer.label()} также нет ходов! Ход завершен.")
                        remainingDice.clear()
                        advanceTurn()
                    }
                } else {
                    addLogEntry("⚡ Игрок ${activePlayer.label()} вступает в игру с переданными ходами!")
                }
            }
        }
    }

    /** Advances to the next phase in a kush (double) cascade.
     * Removes the current phase from [doubleWaterfallQueue], resets the active player to the
     * original [roller], and re-checks for legal moves. If the queue is empty, ends the turn. */
    private fun loadNextWaterfallPhase() {
        if (doubleWaterfallQueue.isNotEmpty()) {
            remainingDice.clear()
            remainingDice.addAll(doubleWaterfallQueue.removeAt(0))
            addLogEntry("⚡ Переход к следующему каскаду куша: $remainingDice")
            // A new cascade must start with the original roller!
            activePlayer = roller
            isOpponentPlayingTransferred = false
            checkForLackOfMovesAndTransfer()
        } else {
            advanceTurn()
        }
    }

    /**
     * Выполняет ход шашки: обновляет доску, расходует кубик, проверяет выброс и победу.
     * @return true, если ход успешен.
     */
    fun makeMove(move: Move): Boolean {
        if (gameStatus != GameStatus.PLAYER_MOVE) return false

        // Verify move integrity for online play (board hash check)
        if (move.boardHash != null && !verifyMoveIntegrity(move)) {
            Log.e("GameEngine", "Move integrity verification failed — possible board tampering")
            return false
        }

        // Double check eligibility
        if (!isMoveLegal(activePlayer, move.from, move.diceUsed)) {
            Log.e("GameEngine", "Illegal move attempt of size ${move.diceUsed} from ${move.from}")
            return false
        }

        isFreshKushRoll = false

        // Consume dice
        val diceRemoved = remainingDice.remove(move.diceUsed)
        if (!diceRemoved) {
            Log.e("GameEngine", "Dice value ${move.diceUsed} not found in remainingDice=$remainingDice")
            return false
        }

        val absFrom = relativeToAbsolute(activePlayer, move.from)
        
        // Update source stack
        val sourceStack = board[absFrom]
        board[absFrom] = CheckerStack(
            count = sourceStack.count - 1,
            player = if (sourceStack.count - 1 == 0) null else activePlayer
        )

        // If from is head
        if (move.from == 0) {
            headTakesThisTurnMap[activePlayer] = (headTakesThisTurnMap[activePlayer] ?: 0) + 1
        }

        if (move.to >= 24) {
            // Borne off!
            borneOffCount[activePlayer] = (borneOffCount[activePlayer] ?: 0) + 1
            addLogEntry("📤 ${activePlayer.label()} выбросил фишку с позиции ${move.from + 1}")

            // Check win condition
            if (borneOffCount[activePlayer] == 15) {
                winner = activePlayer
                gameStatus = GameStatus.GAME_OVER
                addLogEntry("🏆 ИГРА ЗАВЕРШЕНА! Игрок ${activePlayer.label()} победил!")
                return true
            }
        } else {
            // Normal board move
            val absTo = relativeToAbsolute(activePlayer, move.to)
            val destStack = board[absTo]
            board[absTo] = CheckerStack(
                count = (destStack.count) + 1,
                player = activePlayer
            )
            addLogEntry("👉 ${activePlayer.label()} походил: ${move.from + 1} -> ${move.to + 1} (кубик: ${move.diceUsed})")
        }

        // Check if current phase of dice is fully completed
        if (remainingDice.isEmpty()) {
            if (doubleWaterfallQueue.isNotEmpty()) {
                loadNextWaterfallPhase()
            } else {
                advanceTurn()
            }
        } else {
            checkForLackOfMovesAndTransfer()
        }

        return true
    }

    /** Ends the current turn: switches [roller] to the next player, resets turn state,
     * clears remaining dice and cascade queue, and sets status back to [GameStatus.BEFORE_ROLL]. */
    private fun advanceTurn() {
        // Mark the departing player's first turn as complete
        isFirstTurnMap[roller] = false
        roller = roller.other()
        activePlayer = roller
        headTakesThisTurnMap[Player.WHITE] = 0
        headTakesThisTurnMap[Player.BLACK] = 0
        isOpponentPlayingTransferred = false
        isFreshKushRoll = false
        firstTurnKushDice = 0
        remainingDice.clear()
        doubleWaterfallQueue.clear()
        gameStatus = GameStatus.BEFORE_ROLL
        turnStartTimeMillis = System.currentTimeMillis()
        addLogEntry("🔄 Переход хода к ${roller.label()}.")
    }

    /** Проверяет, все ли шашки игрока находятся в доме (отн. 18..23). */
    fun areAllCheckersInHome(player: Player): Boolean {
        var outsideCount = 0
        for (r in 0..17) {
            val abs = relativeToAbsolute(player, r)
            if (board[abs].player == player) {
                outsideCount += board[abs].count
            }
        }
        return outsideCount == 0
    }

    /**
     * Проверяет легальность хода с учётом: владельца шашки, выброса, дома,
     * блокировки соперником, правила головы и правила 6 подряд.
     */
    fun isMoveLegal(player: Player, fromRel: Int, dice: Int): Boolean {
        val toRel = fromRel + dice

        // Check source stack owners
        val absFrom = relativeToAbsolute(player, fromRel)
        val srcStack = board[absFrom]
        if (srcStack.player != player || srcStack.count == 0) return false

        // Case 1: Bear off (exiting off the board)
        if (toRel >= 24) {
            // Can only bear off if all checkers are inside the home (relative 18..23)
            if (!areAllCheckersInHome(player)) return false

            // ONLY exact match exit is allowed under these crazy rules!
            // E.g., if checker is at relative 22 (needs 2), a roll of 3 (toRel = 25) is illegal.
            return toRel == 24
        }

        // Case 2: Move on the board
        // A. Home restriction: "Внутри дома ходы не делаются - возможны только выходы"
        // Once a checker reaches the home (index >= 18), it cannot move to another home point (toRel < 24)
        if (fromRel >= 18 && toRel < 24) {
            return false
        }

        // B. Destination point block check
        val absTo = relativeToAbsolute(player, toRel)
        val destStack = board[absTo]
        if (destStack.player != null && destStack.player != player) {
            // Locked by opponent checker! In Long Backgammon, >=1 opponent checker blocks!
            return false
        }

        // C. Head taking rules: Only the original roller can move from head.
        // First turn: up to 2 head takes (exceptions for kush where one checker covers all)
        // 5:5 kush = one checker can do all cascade phases → only 1 head take
        // 3:3, 4:4, 6:6 kush = need 2 checkers → 2 head takes
        if (fromRel == 0) {
            if (player != roller) {
                return false
            }
            val taken = headTakesThisTurnMap[player] ?: 0
            val isFirst = isFirstTurnMap[player] == true
            val maxHeadTakes = when {
                !isFirst -> 1                                  // обычные ходы — 1 фишка
                firstTurnKushDice > 0 && firstTurnKushDice != 5 -> 2  // куш 3:3/4:4/6:6 — 2 фишки
                else -> 1                                      // первый ход не-куш или 5:5 куш — 1 фишка
            }
            if (taken >= maxHeadTakes) {
                return false
            }
        }

        // D. Linear block check (building 6 occupied cells in a row)
        if (willBuildIllegalSixBlock(player, fromRel, toRel)) {
            return false
        }

        return true
    }

    /** Возвращает все легальные ходы для указанного игрока с данными значениями кубиков. */
    fun getAllLegalMoves(player: Player, diceValues: List<Int>): List<Move> {
        if (diceValues.isEmpty()) return emptyList()

        val uniqueDice = diceValues.distinct()
        val moves = mutableListOf<Move>()

        for (r in 0..23) {
            val abs = relativeToAbsolute(player, r)
            val stack = board[abs]
            if (stack.player != player || stack.count == 0) continue

            for (d in uniqueDice) {
                if (isMoveLegal(player, r, d)) {
                    moves.add(Move(from = r, to = r + d, diceUsed = d))
                }
            }
        }
        return moves
    }

    /**
     * Ищет все достижимые пути из указанной позиции с использованием оставшихся кубиков.
     * Симулирует ходы на временной копии доски и возвращает уникальные конечные позиции.
     */
    fun findReachablePathsFrom(player: Player, fromRel: Int): List<ReachablePath> {
        val results = mutableListOf<ReachablePath>()
        val currentSteps = mutableListOf<Move>()
        
        val originalBoard = board.map { it.copy() }
        val originalRemainingDice = remainingDice.toList()
        val originalHeadTakes = headTakesThisTurnMap.toMap()
        
        fun search(currFrom: Int) {
            if (currentSteps.isNotEmpty()) {
                val finalTo = currentSteps.last().to
                val existing = results.find { it.finalTo == finalTo }
                if (existing == null) {
                    results.add(ReachablePath(finalTo, currentSteps.toList()))
                } else if (currentSteps.size < existing.steps.size) {
                    results.remove(existing)
                    results.add(ReachablePath(finalTo, currentSteps.toList()))
                }
            }
            
            if (remainingDice.isEmpty()) return
            if (currFrom >= 24) return
            
            val distinctDice = remainingDice.distinct()
            for (dice in distinctDice) {
                if (isMoveLegal(player, currFrom, dice)) {
                    val absFrom = relativeToAbsolute(player, currFrom)
                    val toRel = currFrom + dice
                    val absTo = if (toRel < 24) relativeToAbsolute(player, toRel) else -1
                    
                    val prevBoardFrom = board[absFrom]
                    val prevBoardTo = if (toRel < 24 && absTo != -1) board[absTo] else null
                    val prevHeadTakes = headTakesThisTurnMap[player] ?: 0
                    
                    // Simulate Move on the real board to keep track of block rules and legal checks
                    board[absFrom] = CheckerStack(
                        count = prevBoardFrom.count - 1,
                        player = if (prevBoardFrom.count - 1 == 0) null else player
                    )
                    if (toRel < 24 && absTo != -1) {
                        val destStack = board[absTo]
                        board[absTo] = CheckerStack(
                            count = destStack.count + 1,
                            player = player
                        )
                    }
                    
                    if (!remainingDice.remove(dice)) {
                        // Should not happen in simulation, but fail safely
                        continue
                    }
                    if (currFrom == 0) {
                        headTakesThisTurnMap[player] = prevHeadTakes + 1
                    }
                    
                    currentSteps.add(Move(from = currFrom, to = toRel, diceUsed = dice))
                    
                    search(toRel)
                    
                    // Backtrack
                    currentSteps.removeAt(currentSteps.size - 1)
                    board[absFrom] = prevBoardFrom
                    if (toRel < 24 && absTo != -1 && prevBoardTo != null) {
                        board[absTo] = prevBoardTo
                    }
                    remainingDice.add(dice)
                    headTakesThisTurnMap[player] = prevHeadTakes
                }
            }
        }
        
        try {
            search(fromRel)
        } finally {
            // Restore original state completely (even if search threw an exception)
            board = originalBoard.toMutableList()
            remainingDice.clear()
            remainingDice.addAll(originalRemainingDice)
            headTakesThisTurnMap.clear()
            headTakesThisTurnMap.putAll(originalHeadTakes)
        }
        
        return results
    }

    /**
     * Проверяет, создаст ли ход запрещённый блок из 6 занятых клеток подряд.
     * Блок разрешён, если хотя бы одна шашка соперника уже пересекла его.
     */
    fun willBuildIllegalSixBlock(player: Player, fromRel: Int, toRel: Int): Boolean {
        // Clone state
        val tempBoard = board.toMutableList()
        val absFrom = relativeToAbsolute(player, fromRel)
        val absTo = relativeToAbsolute(player, toRel)

        val src = tempBoard[absFrom]
        tempBoard[absFrom] = CheckerStack(src.count - 1, if (src.count - 1 == 0) null else player)

        val dest = tempBoard[absTo]
        tempBoard[absTo] = CheckerStack(dest.count + 1, player)

        val occupied = BooleanArray(24) { i -> tempBoard[i].player == player }
        var hasFenceOfSix = false
        var fenceStartAbs = -1

        // Search for 6 consecutive blocks
        for (start in 0 until 24) {
            var sequence = 0
            for (o in 0 until 6) {
                val idx = (start + o) % 24
                if (occupied[idx]) sequence++ else break
            }
            if (sequence == 6) {
                hasFenceOfSix = true
                fenceStartAbs = start
                break
            }
        }

        if (!hasFenceOfSix) return false

        // Verify if opponent checker has ALREADY crossed the fence
        val opponent = player.other()
        val fenceOpponentRelatives = (0 until 6).map { o ->
            val absFencePoint = (fenceStartAbs + o) % 24
            absoluteToRelative(opponent, absFencePoint)
        }
        val maxFenceOpponentRel = fenceOpponentRelatives.maxOrNull() ?: 0

        // Opponent checkers relatives on board
        val opponentRelatives = mutableListOf<Int>()
        for (i in 0 until 24) {
            if (tempBoard[i].player == opponent && tempBoard[i].count > 0) {
                opponentRelatives.add(absoluteToRelative(opponent, i))
            }
        }

        val hasCrossed = opponentRelatives.any { r -> r > maxFenceOpponentRel }
        return !hasCrossed // If none has crossed, then building the block is illegal (locked them up!)
    }

    /**
     * Эвристический выбор лучшего хода для бота:
     * 1) выброс с доски, 2) ход с головы, 3) вход в дом, 4) разброс с загруженных пунктов.
     */
    fun selectBestBotMove(): Move? {
        val legals = getAllLegalMoves(activePlayer, remainingDice)
        if (legals.isEmpty()) return null

        // 1. Prioritize bearing off (exiting board)
        val bearOffMoves = legals.filter { it.to >= 24 }
        if (bearOffMoves.isNotEmpty()) {
            return bearOffMoves.maxByOrNull { it.from } // prefer bearing off from lower numbers/furthest back if possible, or exact matches
        }

        // 2. Prioritize moving from head if we haven't taken yet and has checkers to unlock
        val headMoves = legals.filter { it.from == 0 }
        if (headMoves.isNotEmpty() && board[relativeToAbsolute(activePlayer, 0)].count > 5) {
            return headMoves.random()
        }

        // 3. Prioritize entering home (since once inside they can't move internally)
        val enterHomeMoves = legals.filter { it.from < 18 && it.to >= 18 }
        if (enterHomeMoves.isNotEmpty()) {
            return enterHomeMoves.maxByOrNull { it.to } // enter deepest possible
        }

        // 4. Default heuristic: Prefer moves that move from clogged points (many checkers on single point > 3)
        // to disperse and cover open areas, OR prefer moving furthermost checkers
        val fromClogged = legals.sortedByDescending { board[relativeToAbsolute(activePlayer, it.from)].count }
        if (fromClogged.isNotEmpty()) {
            // Evaluate if we can move our furthest checker forward
            val furthestCheckers = legals.filter { it.from < 12 }
            if (furthestCheckers.isNotEmpty()) {
                return furthestCheckers.random()
            }
            return fromClogged.first()
        }

        return legals.random()
    }

    /** Compute SHA-256 hash of current board state for online integrity verification */
    fun computeBoardHash(): String {
        val sb = StringBuilder()
        for (point in board) {
            sb.append(point.player?.name ?: "EMPTY").append(':').append(point.count).append(';')
        }
        sb.append(activePlayer.name).append(':')
        sb.append(remainingDice.joinToString(",")).append(':')
        sb.append(gameStatus.name)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sb.toString().encodeToByteArray())
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

    /** Verify that a move's boardHash matches the current board state */
    private fun verifyMoveIntegrity(move: Move): Boolean {
        val expectedHash = computeBoardHash()
        return move.boardHash == expectedHash
    }
}
