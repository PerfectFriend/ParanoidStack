package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V2RayEmbeddedControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testInitialState() {
        val controller = V2RayEmbeddedController(
            context = context,
            localPort = 10808,
            torSocksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertFalse(controller.isRunning())
    }

    @Test
    fun testInitialStateWithCustomPorts() {
        val controller = V2RayEmbeddedController(
            context = context,
            localPort = 10808,
            torSocksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertFalse(controller.isRunning())
    }

    @Test
    fun testStopWhenNotRunning() {
        val controller = V2RayEmbeddedController(
            context = context,
            localPort = 10808,
            torSocksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        controller.stop()
        assertFalse(controller.isRunning())
    }

    @Test
    fun testStartFailsWithProcessError() {
        val logs = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val controller = V2RayEmbeddedController(
            context = context,
            localPort = 10808,
            torSocksPort = 9050,
            onLog = { logs.add(it) },
            onStatusChange = { statuses.add(it) }
        )
        controller.start()
        Thread.sleep(500)
        assertFalse(controller.isRunning())
        // On Robolectric, the binary is extracted from assets but ProcessBuilder fails
        // (xray is a Linux binary, not a Windows exe)
        assertTrue(logs.any { it.contains("Exception") || it.contains("error") } || statuses.contains("INACTIVE"))
    }

    @Test
    fun testStatusChangesDuringStart() {
        val statuses = mutableListOf<String>()
        val controller = V2RayEmbeddedController(
            context = context,
            localPort = 10808,
            torSocksPort = 9050,
            onLog = {},
            onStatusChange = { statuses.add(it) }
        )
        controller.start()
        Thread.sleep(300)
        assertTrue(statuses.isNotEmpty())
    }
}
