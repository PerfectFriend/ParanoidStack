package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GameBoardPanelTest {

    @Test
    fun `GameBoardState default values`() {
        val state = GameBoardState()
        assertEquals(24, state.points.size)
        assertEquals(0, state.points.sum())
        assertEquals(0, state.barPlayer)
        assertEquals(0, state.barOpponent)
        assertEquals(true, state.boardOrientation)
    }

    @Test
    fun `GameBoardState with custom points`() {
        val points = List(24) { if (it % 2 == 0) 2 else 1 }
        val state = GameBoardState(points = points, barPlayer = 1, homePlayer = 3)
        assertEquals(2, state.points[0])
        assertEquals(1, state.points[1])
        assertEquals(1, state.barPlayer)
        assertEquals(3, state.homePlayer)
    }
}
