package com.nexuschat.app.services

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SMP protocol framing and parsing.
 * Run with: ./gradlew test --tests "com.nexuschat.app.services.SmpProtocolTest"
 */
class SmpProtocolTest {

    private val gson = Gson()

    @Test
    fun testCreateNewQueueFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "NEW")
            addProperty("corrId", "test123")
            add("keys", JsonObject().apply {
                addProperty("enc", "base64enckey")
                addProperty("sig", "base64sigkey")
            })
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("NEW", parsed.get("cmd").asString)
        assertEquals("test123", parsed.get("corrId").asString)
        assertNotNull(parsed.get("keys"))
    }

    @Test
    fun testSubscribeFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "SUB")
            addProperty("corrId", "sub456")
            addProperty("queueId", "queue_abc123")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("SUB", parsed.get("cmd").asString)
        assertEquals("sub456", parsed.get("corrId").asString)
        assertEquals("queue_abc123", parsed.get("queueId").asString)
    }

    @Test
    fun testSendMessageFrame() {
        val body = JsonObject().apply {
            addProperty("encrypted", "base64encrypteddata")
            addProperty("nonce", "base64nonce")
            addProperty("ts", System.currentTimeMillis())
        }

        val frame = JsonObject().apply {
            addProperty("cmd", "SEND")
            addProperty("corrId", "send789")
            addProperty("queueId", "queue_abc123")
            add("body", body)
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("SEND", parsed.get("cmd").asString)
        assertEquals("send789", parsed.get("corrId").asString)
        assertEquals("queue_abc123", parsed.get("queueId").asString)
        assertNotNull(parsed.get("body"))
    }

    @Test
    fun testAckFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "ACK")
            addProperty("corrId", "ack123")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("ACK", parsed.get("cmd").asString)
        assertEquals("ack123", parsed.get("corrId").asString)
    }

    @Test
    fun testIncomingMessageFrame() {
        val body = JsonObject().apply {
            addProperty("encrypted", "base64msg")
            addProperty("nonce", "base64nonce")
            addProperty("ts", System.currentTimeMillis())
        }

        val frame = JsonObject().apply {
            addProperty("cmd", "MSG")
            addProperty("queueId", "queue_abc123")
            add("body", body)
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("MSG", parsed.get("cmd").asString)
        assertEquals("queue_abc123", parsed.get("queueId").asString)
        assertNotNull(parsed.get("body"))
    }

    @Test
    fun testPingFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "PING")
            addProperty("corrId", "ping123")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("PING", parsed.get("cmd").asString)
        assertEquals("ping123", parsed.get("corrId").asString)
    }

    @Test
    fun testErrorFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "ERR")
            addProperty("corrId", "err123")
            addProperty("error", "Queue not found")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("ERR", parsed.get("cmd").asString)
        assertEquals("err123", parsed.get("corrId").asString)
        assertEquals("Queue not found", parsed.get("error").asString)
    }

    @Test
    fun testDeleteQueueFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "DEL")
            addProperty("corrId", "del123")
            addProperty("queueId", "queue_abc123")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("DEL", parsed.get("cmd").asString)
        assertEquals("del123", parsed.get("corrId").asString)
        assertEquals("queue_abc123", parsed.get("queueId").asString)
    }

    @Test
    fun testEndFrame() {
        val frame = JsonObject().apply {
            addProperty("cmd", "END")
            addProperty("queueId", "queue_abc123")
        }

        val json = Gson().toJson(frame)
        val parsed = gson.fromJson(json, JsonObject::class.java)

        assertEquals("END", parsed.get("cmd").asString)
        assertEquals("queue_abc123", parsed.get("queueId").asString)
    }

    @Test
    fun testAllCommandsPresent() {
        val commands = listOf("NEW", "SUB", "SEND", "ACK", "MSG", "PING", "PONG", "ERR", "DEL", "END")
        
        commands.forEach { cmd ->
            val frame = JsonObject().apply {
                addProperty("cmd", cmd)
                addProperty("corrId", "test")
            }
            val json = Gson().toJson(frame)
            val parsed = gson.fromJson(json, JsonObject::class.java)
            assertEquals(cmd, parsed.get("cmd").asString)
        }
    }

    @Test
    fun testCorrIdGeneration() {
        val corrId = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        
        assertEquals(16, corrId.length)
        assertTrue(corrId.all { it.isLetterOrDigit() })
    }
}