package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDicePanelTest {

    @Test
    fun `DiceState default values`() {
        val state = DiceState()
        assertEquals(1, state.dice1)
        assertEquals(2, state.dice2)
        assertEquals(false, state.isRolling)
        assertEquals("White", state.currentPlayer)
    }

    @Test
    fun `DiceState with custom values`() {
        val state = DiceState(dice1 = 4, dice2 = 5, isRolling = true, currentPlayer = "Black")
        assertEquals(4, state.dice1)
        assertEquals(5, state.dice2)
        assertEquals(true, state.isRolling)
        assertEquals("Black", state.currentPlayer)
    }

    @Test
    fun `GameControlsState default values`() {
        val state = GameControlsState()
        assertEquals(false, state.canUndo)
        assertEquals(false, state.canRedo)
        assertEquals(true, state.canRoll)
        assertEquals(false, state.gameOver)
    }

    @Test
    fun `GameScoreState default values`() {
        val state = GameScoreState()
        assertEquals(0, state.playerScore)
        assertEquals(0, state.opponentScore)
        assertEquals("Playing", state.gamePhase)
    }
}
