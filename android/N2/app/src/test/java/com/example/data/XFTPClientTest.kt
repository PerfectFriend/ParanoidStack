package com.example.data

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XFTPClientTest {

    @Test
    fun testXFTPServerDataClass() {
        val server = XFTPServer(
            serverIdentity = "abc123def456",
            host = "xftp.example.com",
            port = 443
        )
        assertEquals("abc123def456", server.serverIdentity)
        assertEquals("xftp.example.com", server.host)
        assertEquals(443, server.port)
    }

    @Test
    fun testXFTPServerDefaultPort() {
        val server = XFTPServer(
            serverIdentity = "id123",
            host = "xftp.example.com"
        )
        assertEquals(443, server.port)
    }

    @Test
    fun testChunkInfo() {
        val digest = "abcdef1234567890".encodeToByteArray()
        val info = XFTPClient.ChunkInfo(1024, digest)
        assertEquals(1024, info.size)
        assertArrayEquals(digest, info.digest)
        assertTrue(info.digestB64.isNotEmpty())
    }

    @Test
    fun testChunkUploadResult() {
        val senderId = "sender123".encodeToByteArray()
        val recipientIds = listOf("recv1".encodeToByteArray(), "recv2".encodeToByteArray())
        val result = XFTPClient.ChunkUploadResult(senderId, recipientIds)
        assertArrayEquals(senderId, result.senderId)
        assertEquals(2, result.recipientIds.size)
    }

    @Test
    fun testClientConnectFailsWithBadUrl() {
        val server = XFTPServer(
            serverIdentity = "dGVzdA==",
            host = "192.0.2.1",
            port = 1
        )
        val client = XFTPClient(server)
        assertFalse(client.isConnected)
        assertFalse(client.connect())
    }

    @Test
    fun testSendPingDoesNotThrow() {
        // SendPing attempts to send on potentially null output stream
        // but should not throw because sendRaw catches exceptions
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        client.sendPing()
    }

    @Test
    fun testRegisterChunkNoConnection() {
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        val result = client.registerChunk(
            sndKey = "key".encodeToByteArray(),
            rcvKeys = listOf("rcv".encodeToByteArray()),
            size = 100,
            digest = "digest".encodeToByteArray()
        )
        assertNull(result)
    }

    @Test
    fun testMultipleServers() {
        val s1 = XFTPServer("id1", "s1.example.com", 443)
        val s2 = XFTPServer("id2", "s2.example.com", 5223)
        assertNotEquals(s1, s2)
    }

    @Test
    fun testUploadChunkNoConnection() {
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        assertFalse(client.uploadChunk("id".encodeToByteArray(), "data".encodeToByteArray()))
    }

    @Test
    fun testDownloadChunkNoConnection() {
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        assertNull(client.downloadChunk("id".encodeToByteArray(), "dh".encodeToByteArray()))
    }

    @Test
    fun testDeleteChunkNoConnection() {
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        assertFalse(client.deleteChunk("id".encodeToByteArray()))
    }

    @Test
    fun testAckChunkNoConnection() {
        val server = XFTPServer("dGVzdA==", "localhost", 1)
        val client = XFTPClient(server)
        assertFalse(client.ackChunk("id".encodeToByteArray()))
    }
}
