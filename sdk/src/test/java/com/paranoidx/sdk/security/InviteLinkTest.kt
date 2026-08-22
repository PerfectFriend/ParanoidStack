package com.paranoidx.sdk.security

import org.junit.Assert.*
import org.junit.Test

class InviteLinkTest {

    private val testPubKey = ByteArray(32).also { it[0] = 0x01; it[31] = 0x42 }
    private val testServer = "smp.example.com:5273"
    private val testQueueId = "abcd1234"
    private val testName = "Backgammon Player"

    @Test
    fun `create invite link`() {
        val link = InviteLink(testPubKey, testServer, testQueueId, testName)
        assertArrayEquals(testPubKey, link.pubKey)
        assertEquals(testServer, link.serverAddress)
        assertEquals(testQueueId, link.queueId)
        assertEquals(testName, link.displayName)
    }

    @Test
    fun `default display name`() {
        val link = InviteLink(testPubKey, testServer, testQueueId)
        assertEquals("SimpleX Contact", link.displayName)
    }

    @Test
    fun `toUri produces valid simplex URI`() {
        val link = InviteLink(testPubKey, testServer, testQueueId, testName)
        val uri = link.toUri()
        assertTrue("Should start with simplex://", uri.startsWith("simplex://"))
        assertTrue("Should contain @", uri.contains("@"))
        assertTrue("Should contain ?name=", uri.contains("?name="))
        assertTrue("Should contain server", uri.contains(testServer))
        assertTrue("Should contain queueId", uri.contains(testQueueId))
    }

    @Test
    fun `round-trip toUri fromUri`() {
        val original = InviteLink(testPubKey, testServer, testQueueId, testName)
        val uri = original.toUri()
        val parsed = InviteLink.fromUri(uri)
        assertNotNull("Parsed link should not be null", parsed)
        assertEquals(original, parsed)
        assertEquals(original.displayName, parsed!!.displayName)
    }

    @Test
    fun `fromUri with special name characters`() {
        val name = "Player & Friend!"
        val link = InviteLink(testPubKey, testServer, testQueueId, name)
        val uri = link.toUri()
        val parsed = InviteLink.fromUri(uri)
        assertNotNull(parsed)
        assertEquals(name, parsed!!.displayName)
    }

    @Test
    fun `fromUri without name falls back to default`() {
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(testPubKey)
        val uri = "simplex://$b64@$testServer/$testQueueId"
        val parsed = InviteLink.fromUri(uri)
        assertNotNull(parsed)
        assertEquals("SimpleX Contact", parsed!!.displayName)
    }

    @Test
    fun `fromUri invalid formats return null`() {
        assertNull("No scheme", InviteLink.fromUri("not-a-link"))
        assertNull("No @", InviteLink.fromUri("simplex://justkey"))
        assertNull("No /", InviteLink.fromUri("simplex://key@server"))
        assertNull("Empty", InviteLink.fromUri(""))
        assertNull("Bad base64", InviteLink.fromUri("simplex://!!!@server/qid"))
    }

    @Test
    fun `equality based on content`() {
        val a = InviteLink(testPubKey, testServer, testQueueId, "Player1")
        val b = InviteLink(testPubKey, testServer, testQueueId, "Player2")
        assertEquals("Same content = equal", a, b)
        assertEquals("Hash codes equal", a.hashCode(), b.hashCode())
    }

    @Test
    fun `different pubKey not equal`() {
        val key2 = ByteArray(32).also { it[0] = 0xFF }
        val a = InviteLink(testPubKey, testServer, testQueueId)
        val b = InviteLink(key2, testServer, testQueueId)
        assertNotEquals(a, b)
    }

    @Test
    fun `toUri uses URL-safe Base64 without padding`() {
        val link = InviteLink(testPubKey, testServer, testQueueId)
        val uri = link.toUri()
        // No padding '=' characters in URL-safe base64
        assertFalse("No Base64 padding", uri.contains("="))
        // No '/' or '+' (URL-safe uses - and _)
        assertFalse("No unsafe chars in key section", uri.split("@")[0].contains("/"))
    }

    @Test
    fun `fromUri handles encoded name`() {
        val link = InviteLink(testPubKey, testServer, testQueueId, "Max & Co.")
        val uri = link.toUri()
        val back = InviteLink.fromUri(uri)
        assertEquals("Max & Co.", back!!.displayName)
    }

    @Test
    fun `toUri no double-encoding`() {
        val link = InviteLink(testPubKey, testServer, testQueueId, "Simple Name")
        val uri = link.toUri()
        // The name "Simple Name" should appear directly (no spaces encoded as + or %20)
        assertTrue(uri.contains("?name=Simple+Name") || uri.contains("?name=Simple%20Name"))
    }
}
