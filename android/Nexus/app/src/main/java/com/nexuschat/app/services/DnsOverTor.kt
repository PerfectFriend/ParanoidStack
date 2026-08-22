package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class DnsOverTor private constructor() {
    companion object {
        private const val TAG = "NexusChat/DNS"
        private const val DNS_PORT = 5400
        private const val DNS_SERVER = "1.1.1.1"
        private const val DNS_SERVER_ALT = "8.8.8.8"
        const val LOCAL_DNS_PORT = 5354
        @Volatile private var instance: DnsOverTor? = null
        fun getInstance(): DnsOverTor =
            instance ?: synchronized(this) {
                instance ?: DnsOverTor().also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private val dnsCache = ConcurrentHashMap<String, DnsEntry>()
    private var serverSocket: java.net.DatagramSocket? = null
    private var isRunning = false

    data class DnsEntry(
        val ip: String,
        val timestamp: Long = System.currentTimeMillis(),
        val ttl: Long = 300_000
    ) {
        val isExpired: Boolean get() = System.currentTimeMillis() - timestamp > ttl
    }

    fun start() {
        if (isRunning) return
        try {
            serverSocket = DatagramSocket(LOCAL_DNS_PORT, InetAddress.getByName("127.0.0.1"))
            isRunning = true
            scope.launch { dnsLoop() }
            Log.i(TAG, "DNS-over-Tor started on 127.0.0.1:$LOCAL_DNS_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "DNS server start failed: ${e.message}")
        }
    }

    private suspend fun dnsLoop() = withContext(Dispatchers.IO) {
        val buf = ByteArray(512)
        while (isRunning) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                serverSocket?.receive(packet)
                scope.launch { handleDnsQuery(packet) }
            } catch (e: Exception) { if (isRunning) Log.w(TAG, "DNS recv: ${e.message}") }
        }
    }

    private suspend fun handleDnsQuery(query: DatagramPacket) = withContext(Dispatchers.IO) {
        try {
            val domain = parseDomainName(query.data, 12)
            if (domain != null) {
                val cached = dnsCache[domain]
                if (cached != null && !cached.isExpired) {
                    sendDnsResponse(query, cached.ip)
                    return@withContext
                }
                val ip = resolveViaTor(domain)
                if (ip != null) {
                    dnsCache[domain] = DnsEntry(ip)
                    sendDnsResponse(query, ip)
                }
            }
        } catch (e: Exception) { Log.w(TAG, "DNS query error: ${e.message}") }
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

    private suspend fun resolveViaTor(domain: String): String? = withContext(Dispatchers.IO) {
        val torSocks = java.net.Socket()
        try {
            val dnsReq = buildDnsRequest(domain)
            torSocks.connect(InetSocketAddress("127.0.0.1", TorService.SOCKS_PORT), 5000)
            torSocks.soTimeout = 10000
            val torOut = torSocks.getOutputStream()
            val torIn = torSocks.getInputStream()

            torOut.write(byteArrayOf(0x05, 0x01, 0x00))
            torOut.flush()
            val authResp = ByteArray(2)
            if (torIn.read(authResp) != 2 || (authResp[1].toInt() and 0xFF) != 0x00) {
                throw RuntimeException("SOCKS5 auth negotiation failed")
            }

            val addrBytes = InetAddress.getByName(DNS_SERVER).address
            val req = ByteArray(10) { 0 }
            req[0] = 0x05; req[1] = 0x01; req[2] = 0x00; req[3] = 0x01
            req[4] = addrBytes[0]; req[5] = addrBytes[1]; req[6] = addrBytes[2]; req[7] = addrBytes[3]
            req[8] = 0x00; req[9] = 53
            torOut.write(req)
            torOut.flush()
            val connResp = ByteArray(10)
            if (torIn.read(connResp) != 10 || (connResp[1].toInt() and 0xFF) != 0x00) {
                throw RuntimeException("SOCKS5 connect to $DNS_SERVER:53 failed")
            }

            val dnsPacket = dnsReq
            val dnsLen = dnsPacket.size
            torOut.write(byteArrayOf((dnsLen shr 8).toByte(), dnsLen.toByte()))
            torOut.write(dnsPacket)
            torOut.flush()

            val lenHeader = ByteArray(2)
            if (torIn.read(lenHeader) != 2) throw RuntimeException("No DNS response length from Tor")
            val respLen = ((lenHeader[0].toInt() and 0xFF) shl 8) or (lenHeader[1].toInt() and 0xFF)
            val respBuf = ByteArray(respLen)
            var totalRead = 0
            while (totalRead < respLen) {
                val read = torIn.read(respBuf, totalRead, respLen - totalRead)
                if (read < 0) throw RuntimeException("Tor DNS response truncated")
                totalRead += read
            }

            val ip = parseDnsResponse(respBuf)
            if (ip != null) Log.d(TAG, "DNS resolved via Tor: $domain -> $ip")
            ip
        } catch (e: Exception) {
            Log.w(TAG, "DNS resolve failed: $domain - ${e.message}")
            null
        } finally {
            try { torSocks.close() } catch (_: Exception) {}
        }
    }

    fun resolveDirect(domain: String): String? {
        val dnsSocket = DatagramSocket()
        try {
            val dnsReq = buildDnsRequest(domain)
            dnsSocket.connect(InetSocketAddress(DNS_SERVER, 53))
            dnsSocket.send(DatagramPacket(dnsReq, dnsReq.size))
            val resp = ByteArray(512)
            val packet = DatagramPacket(resp, resp.size)
            dnsSocket.soTimeout = 5000
            dnsSocket.receive(packet)
            val ip = parseDnsResponse(packet.data)
            if (ip != null) Log.d(TAG, "DNS resolved directly: $domain -> $ip")
            return ip
        } catch (e: Exception) {
            Log.w(TAG, "Direct DNS failed: ${e.message}")
            return null
        } finally {
            try { dnsSocket.close() } catch (_: Exception) {}
        }
    }

    internal fun buildDnsRequest(domain: String): ByteArray {
        val id = rng.nextInt(65535)
        val header = ByteArray(12)
        header[0] = (id shr 8).toByte(); header[1] = id.toByte()
        header[2] = 0x01; header[3] = 0x00
        header[4] = 0x00; header[5] = 0x01
        header[6] = 0x00; header[7] = 0x00
        header[8] = 0x00; header[9] = 0x00
        header[10] = 0x00; header[11] = 0x00
        val nameParts = domain.split(".")
        val nameBytes = nameParts.flatMap { part ->
            listOf(part.length.toByte()) + part.toByteArray().toList()
        }
        val question = nameBytes.toByteArray() + byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01)
        return header + question
    }

    private fun parseDnsResponse(data: ByteArray): String? {
        return try {
            var pos = 12
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) { pos++; break }
                if (len and 0xC0 == 0xC0) { pos += 2; break }
                pos += len + 1
            }
            pos += 4
            pos += 4
            pos += 4
            if (pos + 1 >= data.size) return null
            val rdLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            if (rdLen == 4 && pos + 3 < data.size) {
                return "${data[pos].toInt() and 0xFF}.${data[pos+1].toInt() and 0xFF}.${data[pos+2].toInt() and 0xFF}.${data[pos+3].toInt() and 0xFF}"
            }
            null
        } catch (e: Exception) { null }
    }

    private fun sendDnsResponse(query: DatagramPacket, ip: String) {
        try {
            val data = query.data.copyOf()
            val ipBytes = InetAddress.getByName(ip).address
            var pos = 12
            while (pos < data.size) {
                val len = data[pos].toInt() and 0xFF
                if (len == 0) { pos++; break }
                if (len and 0xC0 == 0xC0) { pos += 2; break }
                pos += len + 1
            }
            val answer = byteArrayOf(
                0xC0.toByte(), 0x0C,
                0x00, 0x01, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x3C.toByte(),
                0x00, 0x04,
                ipBytes[0], ipBytes[1], ipBytes[2], ipBytes[3]
            )
            val header = data.copyOfRange(0, 12)
            header[2] = (header[2].toInt() or 0x80).toByte()
            header[7] = 0x01
            val questionEnd = (pos + 4).coerceAtMost(data.size)
            val response = header + data.copyOfRange(12, questionEnd) + answer
            val pkt = DatagramPacket(response, response.size, query.address, query.port)
            serverSocket?.send(pkt)
        } catch (e: Exception) { Log.w(TAG, "DNS response error: ${e.message}") }
    }

    suspend fun resolve(domain: String): String? {
        val cached = dnsCache[domain]
        if (cached != null && !cached.isExpired) return cached.ip
        val result = resolveViaTor(domain)
        if (result != null) dnsCache[domain] = DnsEntry(result)
        return result
    }

    fun clearCache() { dnsCache.clear() }

    fun getCacheStats(): Map<String, Any> = mapOf(
        "size" to dnsCache.size,
        "entries" to dnsCache.entries.take(20).map { "${it.key} -> ${it.value.ip}" }
    )

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
