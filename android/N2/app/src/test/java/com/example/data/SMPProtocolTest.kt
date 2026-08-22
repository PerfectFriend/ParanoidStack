package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SMPProtocolTest {
    
    @Test
    fun testCorrIdGeneration() {
        val id1 = SMPProtocol.generateCorrId()
        val id2 = SMPProtocol.generateCorrId()
        assertNotNull(id1)
        assertNotNull(id2)
        assertFalse(id1.contentEquals(id2))
        assertTrue(id1.size >= 16)
    }
    
    @Test
    fun testParseQueueUri() {
        val uriStr = "smp://abc123def456@smp.example.com:443/#xyz789"
        val parsed = SMPProtocol.parseQueueUri(uriStr)
        assertNotNull(parsed)
        assertEquals("abc123def456", parsed!!.serverIdentity)
        assertEquals("smp.example.com", parsed.host)
        assertEquals(443, parsed.port)
    }
    
    @Test
    fun testParseSimpleQueueUri() {
        val uriStr = "smp://abc123@smp.example.com:5222/queue456"
        val parsed = SMPProtocol.parseQueueUri(uriStr)
        assertNotNull(parsed)
        assertEquals("smp.example.com", parsed!!.host)
    }
    
    @Test
    fun testTransportBlockSize() {
        val msg = "test".encodeToByteArray()
        val t = SMPProtocol.encodeTransmission(ByteArray(0), SMPProtocol.generateCorrId(), ByteArray(0), msg)
        val block = SMPProtocol.buildTransportBlock(listOf(t))
        assertEquals(SMPProtocol.TRANSPORT_BLOCK_SIZE, block.size)
    }
    
    @Test
    fun testDefaultServers() {
        assertTrue(SMPProtocol.DEFAULT_SERVERS.isNotEmpty())
        SMPProtocol.DEFAULT_SERVERS.forEach { server ->
            assertTrue(server.host.isNotEmpty())
            assertTrue(server.port > 0)
        }
    }
}
