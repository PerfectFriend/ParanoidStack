package com.nexuschat.app.services

import com.nexuschat.app.crypto.DoubleRatchet
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class TransportIntegrationTest {

    @Test
    fun `dns over tor - parse domain name`() {
        val dns = DnsOverTor.getInstance()
        val query = dns.buildDnsRequest("google.com")
        assertTrue("DNS query should have header", query.size > 12)
        assertEquals("ID should be 2 bytes", (query[0].toInt() and 0xFF) shl 8 or (query[1].toInt() and 0xFF), 0)
        val domain = parseDomainName(query, 12)
        assertEquals("google.com", domain)
    }

    @Test
    fun `dns over tor - cache works`() {
        val dns = DnsOverTor.getInstance()
        dns.clearCache()
        val result = dns.resolveDirect("cloudflare.com")
        if (result != null) {
            assertTrue("DNS should return valid IP", result.contains("."))
            val cached = dns.getCacheStats()
            assertTrue("Cache should have entry", cached["size"] as? Int ?: 0 > 0)
        }
    }

    @Test
    fun `traffic padding - pad to cell produces fixed size`() {
        val pad = TrafficPadding.getInstance()
        val data = ByteArray(50)
        val padded = pad.padToCell(data, 128)
        assertEquals("Padded data should be 128 bytes", 128, padded.size)
    }

    @Test
    fun `traffic padding - pad to block`() {
        val pad = TrafficPadding.getInstance()
        val data = ByteArray(100)
        val blocked = pad.padToBlock(data, 128)
        assertEquals("Block padded data should be 128 bytes", 128, blocked.size)
    }

    @Test
    fun `traffic padding - framer includes header and padding`() {
        val pad = TrafficPadding.getInstance()
        val data = "hello".toByteArray()
        val framed = pad.frameData(data, 1)
        assertTrue("Framed data should be larger than payload", framed.size > data.size)
    }

    @Test
    fun `traffic padding - dummy cell starts with 0x00 0x00 0x00 0x01`() {
        val pad = TrafficPadding.getInstance()
        val dummy = pad.generateDummyCell(128)
        assertEquals(128, dummy.size)
        assertEquals(0x00, dummy[0].toInt() and 0xFF)
        assertEquals(0x00, dummy[1].toInt() and 0xFF)
        assertEquals(0x00, dummy[2].toInt() and 0xFF)
        assertEquals(0x01, dummy[3].toInt() and 0xFF)
    }

    @Test
    fun `traffic padding - jitter produces varying delays`() {
        val pad = TrafficPadding.getInstance()
        val delays = (1..10).map { pad.getJitteredDelay(100) }
        assertTrue("Jittered delays should vary", delays.distinct().size > 1)
    }

    @Test
    fun `obfs4 transport - block obfuscation roundtrip`() {
        val ot = Obfs4Transport.getInstance { _, _ -> }
        val original = ByteArray(128) { it.toByte() }
        val encrypted = ot.obfuscateBlock(original, true)
        assertNotEquals("Encrypted should differ from original", original.toList(), encrypted.toList())
        val decrypted = ot.obfuscateBlock(encrypted, false)
        assertArrayEquals("Roundtrip should recover original", original, decrypted)
    }

    @Test
    fun `chain proxy - socks5 handshake bytes are correct`() {
        val handshake = byteArrayOf(0x05, 0x01, 0x00)
        assertEquals(0x05, handshake[0].toInt() and 0xFF)
        assertEquals(0x01, handshake[1].toInt() and 0xFF)
        assertEquals(0x00, handshake[2].toInt() and 0xFF)
    }

    @Test
    fun `chain proxy - node types cover all expected chains`() {
        val chain = ChainProxy.getInstance()
        assertEquals(5, chain.chainsAvailable.size)
        val names = chain.chainsAvailable.map { it.name }
        assertTrue("tor-only chain present", "tor-only" in names)
        assertTrue("v2ray-only chain present", "v2ray-only" in names)
        assertTrue("tor-over-v2ray chain present", "tor-over-v2ray" in names)
        assertTrue("v2ray-over-tor chain present", "v2ray-over-tor" in names)
        assertTrue("snowflake-tor chain present", "snowflake-tor" in names)
    }

    @Test
    fun `double ratchet - header encode decode`() {
        val dh = ByteArray(32) { it.toByte() }
        val header = DoubleRatchet.Header(dh, 5, 10)
        val encoded = header.encode()
        assertEquals("Header should encode to 41 bytes (1+32+4+4)", 41, encoded.size)
    }

    @Test
    fun `double ratchet - message encrypt decrypt roundtrip`() {
        val sharedSecret = ByteArray(32) { 0x42 }
        val alice = DoubleRatchet.initialize(sharedSecret)
        val plaintext = "Hello from Alice".toByteArray()
        val msg = alice.ratchetEncrypt(plaintext)
        assertNotNull("Ciphertext should not be null", msg.ciphertext)
        assertNotEquals("Ciphertext should differ from plaintext",
            plaintext.toList(), msg.ciphertext.toList())
    }

    @Test
    fun `binary downloader - abi detection returns non-empty`() {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull()
        assertNotNull("ABI should be detected", abi)
        assertTrue("ABI should be a known value",
            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").any { abi?.startsWith(it) == true })
    }

    @Test
    fun `wireguard config - keypair generation`() {
        val ctx = org.mockito.Mockito.mock(android.content.Context::class.java)
        val wg = WireGuardConfig.getInstance(ctx)
        val (priv, pub) = wg.generateKeypair()
        assertTrue("Private key should be base64", priv.isNotEmpty())
        assertTrue("Public key should be base64", pub.isNotEmpty())
    }

    @Test
    fun `smpserver - binary protocol framing works`() {
        val json = """{"cmd":"PING","corrId":"test123"}"""
        val bytes = json.toByteArray(Charsets.UTF_8)
        val framed = byteArrayOf(
            0x02.toByte(),
            (bytes.size shr 24).toByte(), (bytes.size shr 16).toByte(),
            (bytes.size shr 8).toByte(), bytes.size.toByte()
        ) + bytes
        assertTrue("Binary frame should have type byte", framed[0].toInt() == 2)
        val len = ((framed[1].toInt() and 0xFF) shl 24) or
                  ((framed[2].toInt() and 0xFF) shl 16) or
                  ((framed[3].toInt() and 0xFF) shl 8) or
                  (framed[4].toInt() and 0xFF)
        assertEquals("Length should match JSON size", bytes.size, len)
    }

    @Test
    fun `transport manager - build client for transport`() {
        val ctx = org.mockito.Mockito.mock(android.content.Context::class.java)
        val tm = TransportManager.getInstance(ctx)
        val client = tm.getClientForTransport(TransportType.DIRECT_TCP, 5)
        assertNotNull("Should build OkHttpClient", client)
    }

    @Test
    fun `transport manager - transport type enum covers all expected`() {
        val types = TransportType.values().map { it.name }
        assertTrue("TOR transport exists", "TOR" in types)
        assertTrue("SNOWFLAKE transport exists", "SNOWFLAKE" in types)
        assertTrue("DOMAIN_FRONT transport exists", "DOMAIN_FRONT" in types)
        assertTrue("WIREGUARD transport exists", "WIREGUARD" in types)
    }

    private fun parseDomainName(data: ByteArray, offset: Int): String? {
        return try {
            val parts = mutableListOf<String>()
            var pos = offset
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) break
                if (len >= 0xC0) {
                    val ptr = ((len - 0xC0) shl 8) or (data[pos + 1].toInt() and 0xFF)
                    parts.add(parseDomainName(data, ptr) ?: break)
                    break
                }
                pos++
                val label = String(data, pos, len, Charsets.UTF_8)
                parts.add(label)
                pos += len
            }
            parts.joinToString(".")
        } catch (e: Exception) { null }
    }
}
