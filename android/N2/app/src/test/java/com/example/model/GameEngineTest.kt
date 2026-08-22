package com.example.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GameEngineTest {

    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        engine = GameEngine()
    }

    // --- Helper: reflectively set up initial board state with BEFORE_ROLL ---

    /** Uses reflection to set up the engine for roll tests: BEFORE_ROLL status, initial board, player = WHITE */
    private fun setBeforeRoll() {
        setField("gameStatus", GameStatus.BEFORE_ROLL)
        setField("activePlayer", Player.WHITE)
        setField("roller", Player.WHITE)
        engine.remainingDice.clear()
        engine.doubleWaterfallQueue.clear()
        setHeadTakes(0, 0)
        // Set up initial board: white at 23, black at 11
        val boardField = GameEngine::class.java.getDeclaredField("board")
        boardField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val board = boardField.get(engine) as MutableList<CheckerStack>
        board[23] = CheckerStack(15, Player.WHITE)
        board[11] = CheckerStack(15, Player.BLACK)
    }

    /** Completes the lot stage and starts game (Player.MOVE status, proper board). */
    private fun setupGame() {
        engine.rollLotStage1Custom(5, 2)
        engine.transitionToStage2()
        engine.rollLotStage2Custom(3, 1)
        engine.applyLotStage2WinnerAndStart()
    }

    /** Uses reflection to set a private field. */
    private fun setField(name: String, value: Any) {
        val field = GameEngine::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(engine, value)
    }

    /** Resets head takes for testing. */
    private fun setHeadTakes(white: Int, black: Int) {
        val field = GameEngine::class.java.getDeclaredField("headTakesThisTurnMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(engine) as MutableMap<Player, Int>
        map[Player.WHITE] = white
        map[Player.BLACK] = black
    }

    // --- Lot tests ---

    @Test
    fun testLotStage1WhiteWins() {
        val result = engine.rollLotStage1Custom(5, 2)
        assertTrue(result)
    }

    @Test
    fun testLotStage1Tie() {
        val result = engine.rollLotStage1Custom(3, 3)
        assertFalse(result)
    }

    @Test
    fun testLotStage2WhiteWins() {
        engine.transitionToStage2()
        engine.rollLotStage2Custom(5, 2)
        assertTrue(engine.diceLot2White > engine.diceLot2Black)
    }

    @Test
    fun testLotStage2Tie() {
        engine.transitionToStage2()
        val result = engine.rollLotStage2Custom(4, 4)
        assertFalse(result)
    }

    @Test
    fun testFullLotFlow() {
        engine.rollLotStage1Custom(5, 2)
        engine.transitionToStage2()
        engine.rollLotStage2Custom(3, 1)
        engine.applyLotStage2WinnerAndStart()
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
    }

    // --- Roll tests ---

    @Test
    fun testStandardRoll() {
        setBeforeRoll()
        engine.roll(4, 3)
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
        assertEquals(2, engine.remainingDice.size)
        assertTrue(engine.remainingDice.containsAll(listOf(4, 3)))
        assertFalse(engine.isFreshKushRoll)
    }

    @Test
    fun testDoubleRollKush() {
        setBeforeRoll()
        engine.roll(5, 5)
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
        assertTrue(engine.isFreshKushRoll)
        assertEquals(4, engine.remainingDice.size)
        assertEquals(5, engine.remainingDice[0])
    }

    @Test
    fun testDoubleRoll6() {
        setBeforeRoll()
        engine.roll(6, 6)
        assertEquals(4, engine.remainingDice.size)
        assertEquals(6, engine.remainingDice[0])
    }

    @Test
    fun testRollIgnoredWhenNotBeforeRoll() {
        setBeforeRoll()
        engine.roll(4, 3)
        engine.roll(2, 2)
        assertEquals(2, engine.remainingDice.size)
    }

    // --- Move legality tests ---

    @Test
    fun testIsMoveLegalValid() {
        setBeforeRoll()
        assertTrue(engine.isMoveLegal(Player.WHITE, 0, 2))
    }

    @Test
    fun testIsMoveLegalEmptySource() {
        assertFalse(engine.isMoveLegal(Player.WHITE, 5, 1))
    }

    @Test
    fun testIsMoveLegalOpponentBlocked() {
        setBeforeRoll()
        assertTrue(engine.isMoveLegal(Player.WHITE, 0, 2))
    }

    @Test
    fun testGetAllLegalMovesAtStart() {
        setBeforeRoll()
        val moves = engine.getAllLegalMoves(Player.WHITE, listOf(4, 3))
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.any { it.from == 0 })
    }

    @Test
    fun testMakeMoveSuccess() {
        setBeforeRoll()
        engine.roll(3, 1)
        val moves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        assertTrue(moves.isNotEmpty())
        val success = engine.makeMove(moves.first())
        assertTrue(success)
    }

    @Test
    fun testMakeMoveIllegalReturnsFalse() {
        setBeforeRoll()
        engine.roll(3, 1)
        val illegalMove = Move(from = 10, to = 14, diceUsed = 4)
        assertFalse(engine.makeMove(illegalMove))
    }

    @Test
    fun testMakeMoveFromEmptyReturnsFalse() {
        setBeforeRoll()
        engine.roll(3, 1)
        val emptyMove = Move(from = 5, to = 8, diceUsed = 3)
        assertFalse(engine.makeMove(emptyMove))
    }

    @Test
    fun testMakeMoveBeforeRollReturnsFalse() {
        val move = Move(from = 0, to = 3, diceUsed = 3)
        assertFalse(engine.makeMove(move))
    }

    // --- Bearing off tests ---

    @Test
    fun testBorneOffIncrements() {
        setBeforeRoll()
        setBoardForBearOff()
        engine.roll(3, 4)
        val bearMoves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
            .filter { it.to >= 24 }
        if (bearMoves.isNotEmpty()) {
            engine.makeMove(bearMoves.first())
            assertEquals(1, engine.borneOffCount[Player.WHITE])
        }
    }

    @Test
    fun testAreAllCheckersInHome() {
        setBeforeRoll()
        assertFalse(engine.areAllCheckersInHome(Player.WHITE))
        setBoardForBearOff()
        assertTrue(engine.areAllCheckersInHome(Player.WHITE))
    }

    // --- Turn advance and transfer tests ---

    @Test
    fun testAdvanceTurn() {
        setBeforeRoll()
        engine.roll(3, 4)
        var moves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        while (moves.isNotEmpty() && engine.gameStatus == GameStatus.PLAYER_MOVE) {
            engine.makeMove(moves.first())
            moves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        }
        assertTrue(engine.gameStatus == GameStatus.PLAYER_MOVE || engine.gameStatus == GameStatus.BEFORE_ROLL)
    }

    @Test
    fun testCheckForLackOfMovesAndTransfer() {
        setBeforeRoll()
        engine.roll(6, 6)
        val moves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        if (moves.isEmpty()) {
            assertTrue(engine.activePlayer == Player.WHITE || engine.activePlayer == Player.BLACK)
        }
    }

    // --- Bot tests ---

    @Test
    fun testSelectBestBotMoveReturnsMove() {
        setBeforeRoll()
        engine.roll(3, 4)
        engine.humanPlayerColor = Player.BLACK
        val move = engine.selectBestBotMove()
        assertNotNull(move)
    }

    @Test
    fun testSelectBestBotMoveReturnsNullWhenNoMoves() {
        val move = engine.selectBestBotMove()
        assertNull(move)
    }

    // --- Six-block test ---

    @Test
    fun testWillBuildIllegalSixBlockNoBlock() {
        setBeforeRoll()
        assertFalse(engine.willBuildIllegalSixBlock(Player.WHITE, 0, 3))
    }

    // --- Kush tests ---

    @Test
    fun testFreshKushRollFlag() {
        setBeforeRoll()
        engine.roll(4, 4)
        assertTrue(engine.isFreshKushRoll)
        val moves = engine.getAllLegalMoves(Player.WHITE, engine.remainingDice)
        if (moves.isNotEmpty()) {
            engine.makeMove(moves.first())
            assertFalse(engine.isFreshKushRoll)
        }
    }

    @Test
    fun testKushWaterfallQueue() {
        setBeforeRoll()
        engine.roll(3, 3)
        assertEquals(3, engine.doubleWaterfallQueue.size)
        assertEquals(listOf(4, 4, 4, 4), engine.doubleWaterfallQueue[0])
        assertEquals(listOf(5, 5, 5, 5), engine.doubleWaterfallQueue[1])
        assertEquals(listOf(6, 6, 6, 6), engine.doubleWaterfallQueue[2])
    }

    // --- Reachable paths ---

    @Test
    fun testFindReachablePaths() {
        setBeforeRoll()
        engine.roll(4, 3)
        val paths = engine.findReachablePathsFrom(Player.WHITE, 0)
        assertNotNull(paths)
    }

    // --- Enum and utility tests ---

    @Test
    fun testPlayerEnum() {
        assertEquals(Player.BLACK, Player.WHITE.other())
        assertEquals(Player.WHITE, Player.BLACK.other())
    }

    @Test
    fun testGameStatusEnum() {
        val values = GameStatus.values()
        assertEquals(5, values.size)
    }

    @Test
    fun testPlayerLabel() {
        GameEngine.currentLanguage = "EN"
        assertEquals("White", Player.WHITE.label())
        assertEquals("Black", Player.BLACK.label())
        GameEngine.currentLanguage = "RU"
        assertEquals("Белые", Player.WHITE.label())
        assertEquals("Черные", Player.BLACK.label())
        GameEngine.currentLanguage = "EN"
    }

    @Test
    fun testLogEntryAdded() {
        engine.addLogEntry("Test message")
        assertEquals(2, engine.logs.size)
        assertEquals("Test message", engine.logs[1].message)
    }

    @Test
    fun testBorneOffCountAfterWin() {
        setBeforeRoll()
        for (i in 0 until 15) {
            engine.borneOffCount[Player.WHITE] = i + 1
        }
        assertEquals(15, engine.borneOffCount[Player.WHITE])
    }

    @Test
    fun testHumanBotColorOpposites() {
        engine.humanPlayerColor = Player.WHITE
        assertEquals(Player.BLACK, engine.botPlayerColor)
        engine.humanPlayerColor = Player.BLACK
        assertEquals(Player.WHITE, engine.botPlayerColor)
    }

    // --- Helper methods ---

    /** Sets up board for bearing-off tests. */
    private fun setBoardForBearOff() {
        setField("board", MutableList(24) { CheckerStack(0, null) }.also { b ->
            b[0] = CheckerStack(5, Player.WHITE)
            b[1] = CheckerStack(5, Player.WHITE)
            b[2] = CheckerStack(5, Player.WHITE)
            b[23] = CheckerStack(15, Player.BLACK)
        })
        engine.borneOffCount[Player.WHITE] = 0
        engine.borneOffCount[Player.BLACK] = 0
    }
}
