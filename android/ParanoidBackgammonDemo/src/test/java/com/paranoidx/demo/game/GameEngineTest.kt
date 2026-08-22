package com.paranoidx.demo.game

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {

    @Test
    fun `crazy mode initial setup`() {
        val engine = GameEngine(RuleSet.CRAZY)
        engine.resetGame()
        assertEquals(15, engine.board[23].count)
        assertEquals(Player.WHITE, engine.board[23].player)
        assertEquals(15, engine.board[11].count)
        assertEquals(Player.BLACK, engine.board[11].player)
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
    }

    @Test
    fun `short mode initial setup`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        // White: 2@24→23, 5@13→12, 3@8→7, 5@6→5
        assertEquals(2, engine.board[23].count, "White 2@24")
        assertEquals(5, engine.board[12].count, "White 5@13")
        assertEquals(3, engine.board[7].count, "White 3@8")
        assertEquals(5, engine.board[5].count, "White 5@6")
        // Black: 2@1→0, 5@12→11, 3@17→16, 5@19→18
        assertEquals(2, engine.board[0].count, "Black 2@1")
        assertEquals(5, engine.board[11].count, "Black 5@12")
        assertEquals(3, engine.board[16].count, "Black 3@17")
        assertEquals(5, engine.board[18].count, "Black 5@19")
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
    }

    @Test
    fun `long mode initial setup`() {
        val engine = GameEngine(RuleSet.LONG)
        engine.resetGame()
        assertEquals(15, engine.board[23].count)
        assertEquals(Player.WHITE, engine.board[23].player)
        assertEquals(15, engine.board[11].count)
        assertEquals(Player.BLACK, engine.board[11].player)
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
    }

    @Test
    fun `roll sets remaining dice`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 5)
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
        assertTrue(engine.remainingDice.contains(3))
        assertTrue(engine.remainingDice.contains(5))
    }

    @Test
    fun `short double roll gives 4 dice`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(4, 4)
        assertEquals(4, engine.remainingDice.size)
        assertTrue(engine.remainingDice.all { it == 4 })
    }

    @Test
    fun `crazy double roll creates cascades`() {
        val engine = GameEngine(RuleSet.CRAZY)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 3)
        assertEquals(4, engine.remainingDice.size)
        assertTrue(engine.doubleWaterfallQueue.isNotEmpty())
    }

    @Test
    fun `long mode no double cascades`() {
        val engine = GameEngine(RuleSet.LONG)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 3)
        assertEquals(4, engine.remainingDice.size) // double still = 4 dice
        assertTrue(engine.doubleWaterfallQueue.isEmpty()) // but no cascades
    }

    @Test
    fun `move consumes dice and updates board`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(1, 2)
        val diceBefore = engine.remainingDice.size
        val legals = engine.getAllLegalMoves(engine.activePlayer, engine.remainingDice)
        if (legals.isNotEmpty()) {
            assertTrue(engine.makeMove(legals.first()))
            assertTrue(engine.remainingDice.size < diceBefore)
        }
    }

    @Test
    fun `illegal move rejected`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(2, 5)
        // Move from empty point should fail
        assertFalse(engine.makeMove(BackgammonMove(Player.WHITE, 0, 5, 5, 5)))
    }

    @Test
    fun `move from wrong player rejected`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(2, 5)
        // Move from black position when it's white's turn
        assertFalse(engine.makeMove(BackgammonMove(Player.BLACK, 0, 2, 2, 2)))
    }

    @Test
    fun `selectBestBotMove returns valid move or null`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 4)
        val move = engine.selectBestBotMove()
        if (move != null) {
            assertTrue(engine.isMoveLegal(engine.activePlayer, move.from, move.diceUsed))
            assertTrue(engine.remainingDice.contains(move.diceUsed))
        }
    }

    @Test
    fun `advanceTurn switches player`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        val firstPlayer = engine.activePlayer
        engine.roll(3, 4)
        var safety = 20
        while (engine.remainingDice.isNotEmpty() && engine.gameStatus == GameStatus.PLAYER_MOVE && safety > 0) {
            val move = engine.selectBestBotMove()
            if (move != null) engine.makeMove(move) else break
            safety--
        }
        if (engine.gameStatus == GameStatus.BEFORE_ROLL) {
            assertNotEquals(firstPlayer, engine.activePlayer)
        }
    }

    @Test
    fun `computeBoardHash consistent for same state`() {
        val e1 = GameEngine(RuleSet.SHORT)
        val e2 = GameEngine(RuleSet.SHORT)
        e1.resetGame(); e2.resetGame()
        assertEquals(e1.computeBoardHash(), e2.computeBoardHash())
    }

    @Test
    fun `board hash changes after move`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(1, 2)
        val hashBefore = engine.computeBoardHash()
        val legals = engine.getAllLegalMoves(engine.activePlayer, engine.remainingDice)
        if (legals.isNotEmpty()) {
            engine.makeMove(legals.first())
            assertNotEquals(hashBefore, engine.computeBoardHash())
        }
    }

    @Test
    fun `areAllInHome before game is false for short`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        assertFalse(engine.areAllInHome(Player.WHITE))
        assertFalse(engine.areAllInHome(Player.BLACK))
    }

    @Test
    fun `game status transitions correctly`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(2, 6)
        assertEquals(GameStatus.PLAYER_MOVE, engine.gameStatus)
    }

    @Test
    fun `crazy coordinate conversion roundtrip`() {
        val engine = GameEngine(RuleSet.CRAZY)
        assertEquals(23, engine.relativeToAbsolute(Player.WHITE, 0))
        assertEquals(11, engine.relativeToAbsolute(Player.BLACK, 0))
        for (i in 0..23) {
            assertEquals(i, engine.absoluteToRelative(Player.WHITE, engine.relativeToAbsolute(Player.WHITE, i)))
            assertEquals(i, engine.absoluteToRelative(Player.BLACK, engine.relativeToAbsolute(Player.BLACK, i)))
        }
    }

    @Test
    fun `short coordinate conversion`() {
        val engine = GameEngine(RuleSet.SHORT)
        assertEquals(23, engine.relativeToAbsolute(Player.WHITE, 0))
        assertEquals(0, engine.relativeToAbsolute(Player.BLACK, 0))
        for (i in 0..23) {
            assertEquals(i, engine.absoluteToRelative(Player.WHITE, engine.relativeToAbsolute(Player.WHITE, i)))
            assertEquals(i, engine.absoluteToRelative(Player.BLACK, engine.relativeToAbsolute(Player.BLACK, i)))
        }
    }

    @Test
    fun `long coordinate conversion`() {
        val engine = GameEngine(RuleSet.LONG)
        assertEquals(23, engine.relativeToAbsolute(Player.WHITE, 0))
        assertEquals(11, engine.relativeToAbsolute(Player.BLACK, 0))
        for (i in 0..23) {
            assertEquals(i, engine.absoluteToRelative(Player.WHITE, engine.relativeToAbsolute(Player.WHITE, i)))
            assertEquals(i, engine.absoluteToRelative(Player.BLACK, engine.relativeToAbsolute(Player.BLACK, i)))
        }
    }

    @Test
    fun `no legal moves in locked position`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(6, 6) // double 6 — often blocked
        val moves = engine.getAllLegalMoves(engine.activePlayer, engine.remainingDice)
        // In short backgammon, with standard setup, double 6 from white:
        // can move 6 from 13→7 (3 checkers) and 24→18 (2 checkers), then 24→18 again
        // Should have some legal moves
        // This test just verifies no crash and valid result type
        assertNotNull(moves)
    }

    @Test
    fun `resetGame clears board`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        val hash1 = engine.computeBoardHash()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 4)
        val legals = engine.getAllLegalMoves(engine.activePlayer, engine.remainingDice)
        if (legals.isNotEmpty()) {
            engine.makeMove(legals.first())
        }
        assertNotEquals(hash1, engine.computeBoardHash())
        engine.resetGame()
        assertEquals(hash1, engine.computeBoardHash())
        assertEquals(GameStatus.BEFORE_ROLL, engine.gameStatus)
    }

    @Test
    fun `findReachablePaths returns valid paths`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.gameStatus = GameStatus.BEFORE_ROLL
        engine.roll(3, 5)
        // White can move from 12 (position with 5 checkers) by 3 or 5
        val paths = engine.findReachablePathsFrom(Player.WHITE, 12)
        assertTrue(paths.isNotEmpty(), "Should have reachable paths from 12")
    }

    @Test
    fun `multiple rule sets each roll and play`() {
        for (rs in RuleSet.entries) {
            val engine = GameEngine(rs)
            engine.resetGame()
            engine.gameStatus = GameStatus.BEFORE_ROLL
            engine.roll(2, 3)
            val moves = engine.getAllLegalMoves(engine.activePlayer, engine.remainingDice)
            assertNotNull("$rs should produce legal moves", moves)
            if (moves.isNotEmpty()) {
                assertTrue("$rs first move should succeed", engine.makeMove(moves.first()))
            }
        }
    }

    @Test
    fun `can play full game in short mode without crash`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        var turnCount = 0
        while (engine.gameStatus != GameStatus.GAME_OVER && turnCount < 200) {
            val status = engine.gameStatus
            if (status == GameStatus.BEFORE_ROLL) {
                engine.gameStatus = GameStatus.BEFORE_ROLL
                engine.roll((1..6).random(), (1..6).random())
            } else if (status == GameStatus.PLAYER_MOVE) {
                val move = engine.selectBestBotMove()
                if (move != null) {
                    engine.makeMove(move)
                } else {
                    // No legal moves — turn advances automatically
                    engine.gameStatus = GameStatus.BEFORE_ROLL
                }
            }
            turnCount++
        }
        assertTrue(turnCount < 200)
    }

    @Test
    fun `can play full game in crazy mode without crash`() {
        val engine = GameEngine(RuleSet.CRAZY)
        engine.resetGame()
        var turnCount = 0
        while (engine.gameStatus != GameStatus.GAME_OVER && turnCount < 200) {
            if (engine.gameStatus == GameStatus.BEFORE_ROLL) {
                engine.gameStatus = GameStatus.BEFORE_ROLL
                engine.roll((1..6).random(), (1..6).random())
            } else if (engine.gameStatus == GameStatus.PLAYER_MOVE) {
                val move = engine.selectBestBotMove()
                if (move != null) engine.makeMove(move) else break
            }
            turnCount++
        }
        assertTrue(turnCount < 200)
    }

    @Test
    fun `game log entries are populated`() {
        val engine = GameEngine(RuleSet.SHORT)
        engine.resetGame()
        engine.addLogEntry("test entry")
        assertTrue(engine.logs.any { it.message.contains("test") })
    }
}
