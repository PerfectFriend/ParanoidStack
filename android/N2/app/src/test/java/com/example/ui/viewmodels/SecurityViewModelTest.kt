package com.example.ui.viewmodels

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for [SecurityViewModel] — Duress PIN, seed phrase, key derivation.
 */
@RunWith(RobolectricTestRunner::class)
class SecurityViewModelTest {

    private val vm = SecurityViewModel(RuntimeEnvironment.getApplication())

    @Test
    fun testGetDerivedKeyProducesConsistentOutput() {
        val seed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val key1 = vm.getDerivedKey(seed)
        val key2 = vm.getDerivedKey(seed)
        assertEquals("Deterministic key derivation", key1, key2)
        assertTrue("Key should be non-empty", key1.isNotEmpty())
    }

    @Test
    fun testDifferentSeedsProduceDifferentKeys() {
        val key1 = vm.getDerivedKey("seed one two three")
        val key2 = vm.getDerivedKey("seed one two four")
        assertNotEquals("Different seeds → different keys", key1, key2)
    }

    @Test
    fun testGetDerivedKeyHandlesEmptySeed() {
        val key = vm.getDerivedKey("")
        assertNotNull("Empty seed should still produce a key", key)
        assertTrue("Empty seed key should be non-empty", key.isNotEmpty())
    }

    @Test
    fun testGetDerivedKeyIsCaseInsensitive() {
        val upper = vm.getDerivedKey("HELLO WORLD")
        val lower = vm.getDerivedKey("hello world")
        assertEquals("Key derivation should be case-insensitive (lowercased)", upper, lower)
    }
}
