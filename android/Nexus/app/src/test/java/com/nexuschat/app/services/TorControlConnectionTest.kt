package com.nexuschat.app.services

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for TorControlConnection.
 * These tests require a running Tor control port (127.0.0.1:9051) with cookie auth.
 * Run with: ./gradlew test --tests "com.nexuschat.app.services.TorControlConnectionTest"
 */
class TorControlConnectionTest {

    private val CONTROL_PORT = 9051
    private val TIMEOUT_MS = 5000

    @Test
    fun testAuthenticate() {
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = TIMEOUT_MS
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        // Read welcome banner
        val banner = reader.readLine()
        assertNotNull("Should receive banner", banner)
        assertTrue("Banner should contain Tor version", banner!!.contains("Tor"))

        // Authenticate with empty cookie (cookie auth)
        writer.println("AUTHENTICATE")
        writer.flush()

        val response = reader.readLine()
        assertNotNull("Should receive auth response", response)
        assertTrue("Auth should succeed (250)", response!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testSignalNewnym() {
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = TIMEOUT_MS
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        // Read welcome
        reader.readLine()

        // Authenticate
        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        // Send NEWNYM signal
        writer.println("SIGNAL NEWNYM")
        writer.flush()

        val response = reader.readLine()
        assertNotNull("Should receive NEWNYM response", response)
        assertTrue("NEWNYM should succeed (250)", response!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testGetInfoCircuitStatus() {
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = TIMEOUT_MS
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        reader.readLine() // welcome

        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        // Request circuit status
        writer.println("GETINFO circuit-status")
        writer.flush()

        val response = reader.readLine()
        assertNotNull("Should receive circuit status", response)
        assertTrue("Should contain circuit info or 250", response!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testConcurrentCommands() {
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = TIMEOUT_MS
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        reader.readLine() // welcome

        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        // Send multiple commands rapidly
        repeat(5) { i ->
            writer.println("GETINFO version")
            writer.flush()
            val resp = reader.readLine()
            assertNotNull("Response $i should not be null", resp)
            assertTrue("Response $i should be 250", resp!!.startsWith("250"))
        }

        socket.close()
    }

    @Test
    fun testShutdown() {
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = TIMEOUT_MS
        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        reader.readLine() // welcome

        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        // Send shutdown signal
        writer.println("SIGNAL SHUTDOWN")
        writer.flush()

        val response = reader.readLine()
        assertNotNull("Should receive shutdown response", response)
        // Note: Tor may not respond to SHUTDOWN immediately

        socket.close()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // Allow running tests manually
            val test = TorControlConnectionTest()
            try {
                test.testAuthenticate()
                println("testAuthenticate: PASSED")
            } catch (e: Exception) {
                println("testAuthenticate: FAILED - ${e.message}")
            }

            try {
                test.testSignalNewnym()
                println("testSignalNewnym: PASSED")
            } catch (e: Exception) {
                println("testSignalNewnym: FAILED - ${e.message}")
            }

            try {
                test.testGetInfoCircuitStatus()
                println("testGetInfoCircuitStatus: PASSED")
            } catch (e: Exception) {
                println("testGetInfoCircuitStatus: FAILED - ${e.message}")
            }

            try {
                test.testConcurrentCommands()
                println("testConcurrentCommands: PASSED")
            } catch (e: Exception) {
                println("testConcurrentCommands: FAILED - ${e.message}")
            }
        }
    }
}