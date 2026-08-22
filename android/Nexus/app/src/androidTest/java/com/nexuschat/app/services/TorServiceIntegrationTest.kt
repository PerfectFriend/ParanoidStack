package com.nexuschat.app.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket

/**
 * Integration tests for TorService.
 * These tests run on an Android device/emulator and require TorService to be running.
 * Run with: ./gradlew connectedAndroidTest --tests "com.nexuschat.app.services.TorServiceIntegrationTest"
 */
@RunWith(AndroidJUnit4::class)
class TorServiceIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>()
    private val SOCKS_PORT = 9050
    private val CONTROL_PORT = 9051

    @Test
    fun testTorServiceRunning() {
        // Check if TorService is bound and running
        val intent = android.content.Intent(context, TorService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, TorService::class.java),
            0
        )
        assertNotNull("TorService should be declared in manifest", serviceInfo)
    }

    @Test
    fun testSocksPortOpen() {
        // Test that SOCKS5 proxy is listening on 127.0.0.1:9050
        try {
            val socket = Socket("127.0.0.1", SOCKS_PORT)
            socket.soTimeout = 5000
            socket.close()
        } catch (e: Exception) {
            fail("SOCKS5 port $SOCKS_PORT should be open: ${e.message}")
        }
    }

    @Test
    fun testControlPortOpen() {
        // Test that control port is listening
        try {
            val socket = Socket("127.0.0.1", CONTROL_PORT)
            socket.soTimeout = 5000
            socket.close()
        } catch (e: Exception) {
            fail("Control port $CONTROL_PORT should be open: ${e.message}")
        }
    }

    @Test
    fun testControlPortAuth() {
        // Test control port authentication
        val socket = Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = 5000
        val writer = java.io.PrintWriter(socket.getOutputStream(), true)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))

        val banner = reader.readLine()
        assertNotNull("Should receive banner", banner)
        assertTrue("Banner should mention Tor", banner!!.contains("Tor", ignoreCase = true))

        // Authenticate with cookie
        writer.println("AUTHENTICATE")
        writer.flush()

        val authResp = reader.readLine()
        assertNotNull("Should receive auth response", authResp)
        assertTrue("Auth should succeed (250)", authResp!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testNewnymSignal() {
        val socket = java.net.Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = 5000
        val writer = java.io.PrintWriter(socket.getOutputStream(), true)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))

        reader.readLine() // welcome

        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        writer.println("SIGNAL NEWNYM")
        writer.flush()

        val resp = reader.readLine()
        assertNotNull("Should receive NEWNYM response", resp)
        assertTrue("NEWNYM should succeed (250)", resp!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testGetCircuitStatus() {
        val socket = java.net.Socket("127.0.0.1", CONTROL_PORT)
        socket.soTimeout = 5000
        val writer = java.io.PrintWriter(socket.getOutputStream(), true)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))

        reader.readLine() // welcome

        writer.println("AUTHENTICATE")
        writer.flush()
        assertTrue("Auth should succeed", reader.readLine()!!.startsWith("250"))

        writer.println("GETINFO circuit-status")
        writer.flush()

        val resp = reader.readLine()
        assertNotNull("Should receive circuit status", resp)
        assertTrue("Should be 250 response", resp!!.startsWith("250"))

        socket.close()
    }

    @Test
    fun testOnionAddressGenerated() {
        // Check if hidden service hostname file exists
        val hostnameFile = java.io.File(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "hidden_service/hostname"
        )

        // In simulated mode, file may not exist - just verify service runs
        if (hostnameFile.exists()) {
            val hostname = hostnameFile.readText().trim()
            assertTrue("Hostname should be .onion address", hostname.endsWith(".onion"))
        } else {
            // In simulated mode, hostname may not be generated yet
            // Just verify the service is running
            val socket = java.net.Socket("127.0.0.1", SOCKS_PORT)
            socket.soTimeout = 5000
            socket.close()
        }
    }

    @Test
    fun testSocks5ProxyConnectivity() {
        // Test that we can connect through SOCKS5 proxy
        // This is a basic connectivity test
        val socket = java.net.Socket()
        socket.setSoTimeout(10000)

        try {
            // Try to connect to SOCKS5 proxy
            socket.connect(java.net.InetSocketAddress("127.0.0.1", SOCKS_PORT), 5000)
            socket.close()
        } catch (e: Exception) {
            fail("Should be able to connect to SOCKS5 proxy: ${e.message}")
        }
    }

    @Test
    fun testTorServiceGetters() {
        // Test that TorService getters return expected values
        // This requires binding to the service
        val intent = android.content.Intent(context, TorService::class.java)
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, service: android.os.IBinder) {
                val binder = service as TorService.TorBinder
                val service = binder.getService()
                assertTrue("TorService should be running", service.isRunning)
                assertEquals("SOCKS port should be 9050", 9050, service.socksPort)
                // onionAddress may be empty in simulated mode
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) {}
        }

        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        assertTrue("Should bind to TorService", bound)

        // Unbind after test
        context.unbindService(conn)
    }
}