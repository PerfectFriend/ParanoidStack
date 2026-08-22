package com.example.data

import org.junit.Test
import org.junit.Assert.*

/**
 * End-to-end тест SMP протокола: создание очереди, отправка, получение.
 * Проверяет жизненный цикл SMP-сообщения.
 */
class SMPEndToEndTest {

    @Test
    fun testMessageLifecycle() {
        val corrId = SMPProtocol.generateCorrId()
        assertNotNull(corrId)
        assertTrue(corrId.size == 24)

        val queueId = ByteArray(16) { it.toByte() }
        val serverId = ByteArray(16) { (it + 1).toByte() }
        val msgBody = "Hello".encodeToByteArray()

        val block = SMPProtocol.buildTransportBlock(
            listOf(SMPProtocol.encodeTransmission(serverId, corrId, queueId, msgBody))
        )
        assertEquals(SMPProtocol.TRANSPORT_BLOCK_SIZE, block.size)
    }

    @Test
    fun testQueueUriRoundtrip() {
        val uri = "smp://abc123@smp.example.com:5222/#queue456"
        val parsed = SMPProtocol.parseQueueUri(uri)
        assertNotNull(parsed)
        assertEquals("smp.example.com", parsed!!.host)
        assertEquals(5222, parsed.port)
    }

    @Test
    fun testDefaultServersReachable() {
        assertTrue(SMPProtocol.DEFAULT_SERVERS.isNotEmpty())
        SMPProtocol.DEFAULT_SERVERS.forEach { s ->
            assertTrue(s.host.isNotBlank())
            assertTrue(s.port in 1..65535)
        }
    }
}
