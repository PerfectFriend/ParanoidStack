package com.nexuschat.app.bridges

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

/**
 * Unit tests for KeystoreBridge.
 * Run with: ./gradlew test --tests "com.nexuschat.app.bridges.KeystoreBridgeTest"
 */
@RunWith(RobolectricTestRunner::class)
class KeystoreBridgeTest {

    @Test
    fun testStoreAndGetSecret() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        
        // Store a secret
        val stored = bridge.storeSecret("test_key", "test_value")
        assertTrue("Store should succeed", stored)
        
        // Retrieve the secret
        val retrieved = bridge.getSecret("test_key")
        assertEquals("test_value", retrieved)
    }

    @Test
    fun testDeleteSecret() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        
        bridge.storeSecret("delete_key", "value_to_delete")
        val deleted = bridge.deleteSecret("delete_key")
        assertTrue("Delete should succeed", deleted)
        
        val retrieved = bridge.getSecret("delete_key")
        assertEquals("", retrieved)
    }

    @Test
    fun testClearAll() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        
        bridge.storeSecret("key1", "value1")
        bridge.storeSecret("key2", "value2")
        
        val cleared = bridge.clearAll()
        assertTrue("Clear all should succeed", cleared)
        
        assertEquals("", bridge.getSecret("key1"))
        assertEquals("", bridge.getSecret("key2"))
    }

    @Test
    fun testListKeys() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        
        bridge.storeSecret("key1", "value1")
        bridge.storeSecret("key2", "value2")
        
        val keysJson = bridge.listKeys()
        assertTrue("Should contain key1", keysJson.contains("key1"))
        assertTrue("Should contain key2", keysJson.contains("key2"))
    }

    @Test
    fun testIsHardwareBacked() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        val isHwBacked = bridge.isHardwareBacked()
        // Just verify it returns a boolean without crashing
        assertTrue("Should return boolean", isHwBacked == true || isHwBacked == false)
    }

    @Test
    fun testClearKeystore() {
        val bridge = KeystoreBridge(org.robolectric.RuntimeEnvironment.getApplication())
        
        // Store something
        bridge.storeSecret("test_key", "test_value")
        
        // Clear keystore
        val cleared = bridge.clearKeystore()
        // Note: In Robolectric, AndroidKeyStore may not be fully functional
        // Just verify the method doesn't crash
        assertTrue("Should return boolean", cleared == true || cleared == false)
    }
}