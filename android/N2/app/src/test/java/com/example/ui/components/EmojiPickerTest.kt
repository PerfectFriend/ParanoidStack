package com.example.ui.components

import org.junit.Test
import org.junit.Assert.*

class EmojiPickerTest {
    
    @Test
    fun testEmojiCategoriesNotEmpty() {
        // Verify the predefined emoji lists have content
        assertTrue(smileys.isNotEmpty())
        assertTrue(gestures.isNotEmpty())
        assertTrue(food.isNotEmpty())
        assertTrue(animals.isNotEmpty())
        assertTrue(travel.isNotEmpty())
        assertTrue(symbols.isNotEmpty())
    }
    
    @Test
    fun testEmojiCategoryDataClass() {
        val category = EmojiCategory("Test", listOf("😀", "😃"))
        assertEquals("Test", category.name)
        assertEquals(2, category.emojiList.size)
    }
}
