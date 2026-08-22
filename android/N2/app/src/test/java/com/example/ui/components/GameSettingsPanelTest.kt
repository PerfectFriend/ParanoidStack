package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSettingsPanelTest {

    @Test
    fun `GameSettingsState default values`() {
        val state = GameSettingsState()
        assertFalse(state.isDarkMode)
        assertTrue(state.isSoundEnabled)
        assertFalse(state.isBotOpponentEnabled)
        assertEquals("default", state.themeId)
        assertEquals(1, state.aiDifficulty)
    }

    @Test
    fun `GameSettingsState with custom values`() {
        val state = GameSettingsState(isDarkMode = true, isBotOpponentEnabled = true, themeId = "hacker", aiDifficulty = 3)
        assertTrue(state.isDarkMode)
        assertTrue(state.isBotOpponentEnabled)
        assertEquals("hacker", state.themeId)
        assertEquals(3, state.aiDifficulty)
    }
}
