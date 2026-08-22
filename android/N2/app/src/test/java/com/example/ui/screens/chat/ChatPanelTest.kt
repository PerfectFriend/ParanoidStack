package com.example.ui.screens.chat

import com.example.ui.viewmodel.ChatMessage
import org.junit.Test
import org.junit.Assert.*

class ChatPanelTest {
    
    @Test
    fun testChatMessageDataClass() {
        val msg = ChatMessage(
            id = "test_1",
            text = "Hello",
            isOutgoing = true,
            timestamp = 1000L
        )
        assertEquals("test_1", msg.id)
        assertEquals("Hello", msg.text)
        assertTrue(msg.isOutgoing)
        assertFalse(msg.isDeleted)
    }
    
    @Test
    fun testChatMessageDefaults() {
        val msg = ChatMessage(
            id = "test_2",
            text = "Test",
            isOutgoing = false
        )
        assertFalse(msg.isDeleted)
        assertTrue(msg.timestamp > 0)
    }
}
