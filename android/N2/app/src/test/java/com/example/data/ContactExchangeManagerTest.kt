package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactExchangeManagerTest {
    private val manager = ContactExchangeManager()
    
    @Test
    fun testGenerateInviteCode() {
        val code = manager.generateInviteCode("Alice", "dGVzdGtleQ==", "smp.example.com", "queue123")
        assertNotNull(code)
        assertTrue(code.contains("Alice"))
    }
    
    @Test
    fun testParseInviteCode() {
        val code = manager.generateInviteCode("Bob", "a2V5", "smp.com", "q1")
        val parsed = manager.parseInviteCode(code)
        assertNotNull(parsed)
        assertEquals("Bob", parsed!!.displayName)
        assertEquals("smp.com", parsed.smpServer)
    }
    
    @Test
    fun testParseInvalidCode() {
        assertNull(manager.parseInviteCode("invalid json"))
    }
    
    @Test
    fun testGenerateShareLink() {
        assertEquals("simplex://contact/Alice@smp.example.com/q123",
            manager.generateShareLink("Alice", "smp.example.com", "q123"))
    }
    
    @Test
    fun testContactInviteRoundtrip() {
        val invite = ContactExchangeManager.ContactInvite("Test", "key==", "host.com", "q")
        val json = invite.toJson()
        val parsed = ContactExchangeManager.ContactInvite.fromJson(json)
        assertNotNull(parsed)
        assertEquals("Test", parsed!!.displayName)
    }
}
