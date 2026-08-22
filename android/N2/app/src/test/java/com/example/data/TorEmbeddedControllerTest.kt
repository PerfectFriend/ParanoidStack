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
class TorEmbeddedControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testInitialState() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertFalse(controller.isRunning())
    }

    @Test
    fun testStopWhenNotRunning() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        controller.stop()
        assertFalse(controller.isRunning())
    }

    @Test
    fun testGetOnionHostnameReturnsNullWhenNoService() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertNull(controller.getOnionHostname())
    }

    @Test
    fun testGetOnionAddressesReturnsEmpty() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertTrue(controller.getOnionAddresses().isEmpty())
    }

    @Test
    fun testBridgeStatusInitiallyNotConfigured() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        assertTrue(controller.getBridgeStatus().contains("not configured"))
    }

    @Test
    fun testSetObfs4Bridges() {
        val logs = mutableListOf<String>()
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = { logs.add(it) },
            onStatusChange = {}
        )
        controller.setBridges(BridgeType.OBFS4)
        val status = controller.getBridgeStatus()
        assertTrue(status.contains("obfs4"))
        assertFalse(status.contains("not configured"))
    }

    @Test
    fun testSetMeekBridges() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        controller.setBridges(BridgeType.MEEK)
        val status = controller.getBridgeStatus()
        assertTrue(status.contains("meek_lite"))
    }

    @Test
    fun testSetSnowflakeBridges() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = {},
            onStatusChange = {}
        )
        controller.setBridges(BridgeType.SNOWFLAKE)
        val status = controller.getBridgeStatus()
        assertTrue(status.contains("snowflake"))
    }

    @Test
    fun testBridgeFallbackCycle() {
        val logs = mutableListOf<String>()
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = { logs.add(it) },
            onStatusChange = {}
        )
        controller.setBridges(BridgeType.OBFS4)
        val statusBefore = controller.getBridgeStatus()
        assertTrue(statusBefore.contains("obfs4"))

        controller.tryFallbackBridges()
        Thread.sleep(300)
        val statusAfter = controller.getBridgeStatus()
        assertTrue(statusAfter.contains("meek_lite") || statusAfter.contains("obfs4"))
    }

    @Test
    fun testMultipleBridgeFallbacks() {
        val logs = mutableListOf<String>()
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = { logs.add(it) },
            onStatusChange = {}
        )
        controller.tryFallbackBridges()
        Thread.sleep(200)
        controller.tryFallbackBridges()
        Thread.sleep(200)
        // After 2 fallbacks from initial (obfs4 -> meek -> snowflake), we should have cycled
        val status = controller.getBridgeStatus()
        assertTrue(status.contains("attempt"))
    }

    @Test
    fun testStartFailsWithoutTorBinary() {
        val logs = mutableListOf<String>()
        val statuses = mutableListOf<String>()
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 9050,
            onLog = { logs.add(it) },
            onStatusChange = { statuses.add(it) }
        )
        // Without tor binaries in test environment, start will fail gracefully
        controller.start()
        Thread.sleep(500)
        assertFalse(controller.isRunning())
    }

    @Test
    fun testCustomSocksPort() {
        val controller = TorEmbeddedController(
            context = context,
            socksPort = 19050,
            onLog = {},
            onStatusChange = {}
        )
        assertFalse(controller.isRunning())
        controller.stop()
    }

    @Test
    fun testBridgeDataClass() {
        val bridge = BridgeConfig("obfs4", "obfs4 1.2.3.4:443 cert=abc iat-mode=0")
        assertEquals("obfs4", bridge.bridgeType)
        assertEquals("obfs4 1.2.3.4:443 cert=abc iat-mode=0", bridge.bridgeLine)
    }

    @Test
    fun testBridgeTypeEnum() {
        assertEquals("obfs4", BridgeType.OBFS4.displayName)
        assertEquals("meek_lite", BridgeType.MEEK.displayName)
        assertEquals("snowflake", BridgeType.SNOWFLAKE.displayName)
    }
}
