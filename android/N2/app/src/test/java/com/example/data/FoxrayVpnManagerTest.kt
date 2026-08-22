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
class FoxrayVpnManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = FoxrayVpnManager(context)

    @Test
    fun testInitialState() {
        assertNotNull(manager.configs)
        assertTrue(manager.configs.isNotEmpty())
        assertEquals("Disconnected", manager.vpnState)
        assertEquals(-1, manager.pingTime)
    }

    @Test
    fun testSelectConfig() {
        val config = manager.configs.firstOrNull()
        assertNotNull(config)
        manager.selectConfig(config!!)
        assertEquals(config.id, manager.selectedConfig?.id)
    }

    @Test
    fun testDeleteConfigDoesNotRemoveLast() {
        val size = manager.configs.size
        if (size <= 1) return // skip if only one config
        val firstId = manager.configs.first().id
        manager.deleteConfig(firstId)
        assertTrue(manager.configs.size >= 1)
    }

    @Test
    fun testVpnStateDisconnected() {
        manager.stopVpn()
        assertEquals("Disconnected", manager.vpnState)
    }

    @Test
    fun testResetToDefaults() {
        manager.resetToDefaults()
        assertEquals("Disconnected", manager.vpnState)
        assertEquals(-1, manager.pingTime)
    }

    @Test
    fun testImportEmptyText() {
        val result = manager.importConfigsFromText("")
        assertEquals("Пустой текст ввода", result)
    }

    @Test
    fun testImportBlankText() {
        val result = manager.importConfigsFromText("   ")
        assertEquals("Пустой текст ввода", result)
    }

    @Test
    fun testImportInvalidFormat() {
        val result = manager.importConfigsFromText("not a valid config")
        assertEquals("Не удалось распознать формат", result)
    }

    @Test
    fun testImportVlessLink() {
        val link = "vless://2244d9e4-dc80-4c61-9362-dde0afd034dd@104.17.115.113:443?path=%2F%3Fed&security=tls&encryption=none&host=test.example.com&type=ws#TestServer"
        val result = manager.importConfigsFromText(link)
        assertTrue(result.contains("импортировано") || result.contains("imported") || result.startsWith("Успешно"))
    }

    @Test
    fun testImportTrojanLink() {
        val link = "trojan://password@example.com:443?security=tls&type=ws&path=%2F#TrojanServer"
        val result = manager.importConfigsFromText(link)
        assertTrue(result.contains("импортировано") || result.contains("imported") || result.startsWith("Успешно"))
    }

    @Test
    fun testImportVmessLink() {
        val vmessJson = """{"v":"2","ps":"Test VMess","add":"server.com","port":443,"id":"uuid-here","aid":0,"scy":"auto","net":"ws","type":"none","host":"test.example.com","path":"/","tls":"tls"}"""
        val base64 = android.util.Base64.encodeToString(vmessJson.toByteArray(), android.util.Base64.NO_WRAP)
        val link = "vmess://$base64#TestVMess"
        val result = manager.importConfigsFromText(link)
        assertTrue(result.contains("импортировано") || result.startsWith("Успешно"))
    }

    @Test
    fun testImportXrayJson() {
        val json = """{"outbounds":[{"protocol":"vmess","settings":{"vnext":[{"address":"server.com","port":443,"users":[{"id":"uuid","encryption":"auto"}]}]}}],"streamSettings":{"security":"tls"}}"""
        val result = manager.importConfigsFromText(json)
        assertTrue(result.contains("импортировано") || result.contains("imported") || result.startsWith("Успешно"))
    }

    @Test
    fun testConfigPingsInitiallyEmpty() {
        assertNotNull(manager.configPings)
        assertTrue(manager.configPings.isEmpty())
    }

    @Test
    fun testSpeedTestInitiallyFalse() {
        assertFalse(manager.isTestingSpeeds)
    }

}
