package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class ChainProxy private constructor() {
    companion object {
        private const val TAG = "NexusChat/ChainProxy"
        const val CHAIN_LOCAL_PORT = 14499
        const val SOCKS5_VER = 5
        const val CMD_CONNECT = 1
        const val ATYP_IPV4 = 1
        const val ATYP_DOMAIN = 3
        const val ATYP_IPV6 = 4
        const val REP_SUCCESS = 0
        @Volatile private var instance: ChainProxy? = null
        fun getInstance(): ChainProxy =
            instance ?: synchronized(this) {
                instance ?: ChainProxy().also { instance = it }
            }
    }

    enum class NodeType { TOR_SOCKS5, V2RAY_SOCKS5, DIRECT, HTTP_PROXY, SNOWFLAKE }

    data class ProxyChain(val nodes: List<ProxyNode>, val name: String = "default")
    data class ProxyNode(
        val type: NodeType,
        val host: String = "127.0.0.1",
        val port: Int = 9050,
        val username: String = "",
        val password: String = ""
    )

    data class TargetAddr(val host: String, val port: Int)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("chain-proxy"))
    private val rng = SecureRandom()
    private val chains = mutableListOf<ProxyChain>()
    private var activeChain: ProxyChain? = null
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newCachedThreadPool { Thread(it, "chain-worker") }

    private val torOnly = ProxyChain(listOf(
        ProxyNode(NodeType.TOR_SOCKS5, port = TorService.SOCKS_PORT)
    ), "tor-only")

    private val v2rayOnly = ProxyChain(listOf(
        ProxyNode(NodeType.V2RAY_SOCKS5, port = V2RayService.SOCKS5_PORT)
    ), "v2ray-only")

    private val torOverV2Ray = ProxyChain(listOf(
        ProxyNode(NodeType.V2RAY_SOCKS5, port = V2RayService.SOCKS5_PORT),
        ProxyNode(NodeType.TOR_SOCKS5, port = TorService.SOCKS_PORT),
    ), "tor-over-v2ray")

    private val v2rayOverTor = ProxyChain(listOf(
        ProxyNode(NodeType.TOR_SOCKS5, port = TorService.SOCKS_PORT),
        ProxyNode(NodeType.V2RAY_SOCKS5, port = V2RayService.SOCKS5_PORT),
    ), "v2ray-over-tor")

    private val snowflakeTor = ProxyChain(listOf(
        ProxyNode(NodeType.SNOWFLAKE, port = 9900),
        ProxyNode(NodeType.TOR_SOCKS5, port = TorService.SOCKS_PORT),
    ), "snowflake-tor")

    val chainsAvailable: List<ProxyChain> get() = chains.toList()
    val currentChain: ProxyChain? get() = activeChain

    fun start() {
        chains.clear()
        chains.addAll(listOf(torOnly, v2rayOnly, torOverV2Ray, v2rayOverTor, snowflakeTor))
        activeChain = chains.firstOrNull()
        startLocalProxy()
        Log.i(TAG, "ChainProxy started: ${activeChain?.name}")
    }

    private fun startLocalProxy() {
        try {
            serverSocket = ServerSocket(CHAIN_LOCAL_PORT)
            isRunning = true
            Thread({ acceptLoop() }, "chain-accept").apply { isDaemon = true; start() }
            Log.i(TAG, "Chain proxy SOCKS5 on 127.0.0.1:$CHAIN_LOCAL_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start chain proxy: ${e.message}")
        }
    }

    private fun acceptLoop() {
        while (isRunning) {
            try {
                val srv = serverSocket ?: break
                val client = srv.accept()
                executor.submit { handleConnection(client) }
            } catch (e: Exception) { if (isRunning) Log.w(TAG, "Accept error: ${e.message}"); break }
        }
    }

    fun setActiveChain(name: String): Boolean {
        val chain = chains.find { it.name == name } ?: return false
        activeChain = chain
        Log.i(TAG, "Active chain switched to: ${chain.name}")
        return true
    }

    fun addCustomChain(nodes: List<ProxyNode>, name: String) {
        chains.add(ProxyChain(nodes, name))
        Log.i(TAG, "Custom chain added: $name (${nodes.size} hops)")
    }

    private fun handleConnection(client: Socket) {
        try {
            client.soTimeout = 15000
            val target = parseSocks5Connect(client)
            if (target == null) {
                sendSocks5Reply(client, 1.toByte())
                return
            }
            val chain = activeChain ?: run {
                sendSocks5Reply(client, 1.toByte())
                return
            }
            val remote = connectThroughChain(chain, target)
            if (remote != null) {
                sendSocks5Reply(client, REP_SUCCESS.toByte())
                val t1 = Thread { pipe(client.inputStream, remote.outputStream) }
                val t2 = Thread { pipe(remote.inputStream, client.outputStream) }
                t1.start(); t2.start()
                t1.join(60000); t2.join(60000)
            } else {
                sendSocks5Reply(client, 5.toByte())
            }
        } catch (e: Exception) {
            Log.d(TAG, "Chain connection: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun parseSocks5Connect(client: Socket): TargetAddr? {
        try {
            val ins = client.inputStream
            val greeting = ByteArray(2)
            if (ins.read(greeting) != 2 || (greeting[0].toInt() and 0xFF) != SOCKS5_VER) return null
            val nMethods = greeting[1].toInt() and 0xFF
            val methods = ByteArray(nMethods)
            if (ins.read(methods) != nMethods) return null
            client.outputStream.write(byteArrayOf(SOCKS5_VER.toByte(), 0))
            client.outputStream.flush()
            val request = ByteArray(4)
            if (ins.read(request) != 4 || (request[0].toInt() and 0xFF) != SOCKS5_VER || (request[1].toInt() and 0xFF) != CMD_CONNECT) return null
            val atyp = request[3].toInt() and 0xFF
            val host: String
            when (atyp) {
                ATYP_IPV4 -> {
                    val ip = ByteArray(4)
                    if (ins.read(ip) != 4) return null
                    host = InetAddress.getByAddress(ip).hostAddress
                }
                ATYP_DOMAIN -> {
                    val len = ins.read()
                    if (len <= 0) return null
                    val domain = ByteArray(len)
                    if (ins.read(domain) != len) return null
                    host = String(domain)
                }
                ATYP_IPV6 -> {
                    val ip = ByteArray(16)
                    if (ins.read(ip) != 16) return null
                    host = InetAddress.getByAddress(ip).hostAddress
                }
                else -> return null
            }
            val portHi = ins.read()
            val portLo = ins.read()
            val port = ((portHi and 0xFF) shl 8) or (portLo and 0xFF)
            return TargetAddr(host, port)
        } catch (e: Exception) {
            Log.w(TAG, "SOCKS5 parse error: ${e.message}")
            return null
        }
    }

    private fun sendSocks5Reply(client: Socket, rep: Byte) {
        try {
            client.outputStream.write(byteArrayOf(SOCKS5_VER.toByte(), rep, 0, ATYP_IPV4.toByte(), 0, 0, 0, 0, 0, 0))
            client.outputStream.flush()
        } catch (_: Exception) {}
    }

    private fun connectThroughChain(chain: ProxyChain, target: TargetAddr): Socket? {
        if (chain.nodes.isEmpty()) return null
        if (chain.nodes.size == 1) {
            return socks5Connect(chain.nodes[0], target.host, target.port)
        }
        var current: Socket? = null
        try {
            for (i in chain.nodes.indices) {
                val node = chain.nodes[i]
                val isLast = i == chain.nodes.size - 1
                val nextNode = if (!isLast) chain.nodes[i + 1] else null
                val connectHost: String
                val connectPort: Int
                if (isLast) {
                    connectHost = target.host
                    connectPort = target.port
                } else {
                    connectHost = nextNode!!.host
                    connectPort = nextNode.port
                }
                current = sockConnect(node, connectHost, connectPort, current)
                if (current == null) return null
            }
            return current
        } catch (e: Exception) {
            Log.e(TAG, "Chain routing failed: ${e.message}")
            try { current?.close() } catch (_: Exception) {}
            return null
        }
    }

    private fun sockConnect(node: ProxyNode, host: String, port: Int, existing: Socket?): Socket? {
        return when (node.type) {
            NodeType.TOR_SOCKS5, NodeType.V2RAY_SOCKS5, NodeType.SNOWFLAKE -> {
                socks5Connect(node, host, port, existing)
            }
            NodeType.DIRECT -> {
                val s = existing ?: Socket()
                if (existing == null) {
                    s.connect(InetSocketAddress(host, port), 5000)
                    s.soTimeout = 15000
                }
                s
            }
            NodeType.HTTP_PROXY -> {
                httpProxyConnect(node, host, port, existing)
            }
        }
    }

    private fun socks5Connect(node: ProxyNode, host: String, port: Int, existing: Socket? = null): Socket? {
        return try {
            val s = existing ?: run {
                val sock = Socket()
                sock.connect(InetSocketAddress(node.host, node.port), 5000)
                sock.soTimeout = 15000
                sock
            }
            val ins = s.inputStream
            val outs = s.outputStream
            if (existing == null) {
                outs.write(byteArrayOf(SOCKS5_VER.toByte(), 1, 0))
                outs.flush()
                val resp = ByteArray(2)
                if (ins.read(resp) != 2 || (resp[0].toInt() and 0xFF) != SOCKS5_VER || (resp[1].toInt() and 0xFF) != 0) {
                    if (existing == null) s.close()
                    return null
                }
            }
            val hostBytes = host.toByteArray()
            val request = when {
                host.length <= 255 -> byteArrayOf(SOCKS5_VER.toByte(), CMD_CONNECT.toByte(), 0, ATYP_DOMAIN.toByte(), hostBytes.size.toByte()) + hostBytes
                else -> {
                    val ip = InetAddress.getByName(host).address
                    byteArrayOf(SOCKS5_VER.toByte(), CMD_CONNECT.toByte(), 0, ATYP_IPV4.toByte()) + ip
                }
            } + byteArrayOf((port shr 8).toByte(), port.toByte())
            outs.write(request)
            outs.flush()
            val resp2 = ByteArray(4)
            if (ins.read(resp2) != 4 || (resp2[1].toInt() and 0xFF) != REP_SUCCESS) {
                if (existing == null) s.close()
                return null
            }
            val bindAtyp = resp2[3].toInt() and 0xFF
            val bindLen = when (bindAtyp) { ATYP_IPV4 -> 4; ATYP_IPV6 -> 16; ATYP_DOMAIN -> ins.read(); else -> 0 }
            if (bindLen > 0) ins.skip(bindLen.toLong())
            ins.skip(2)
            s
        } catch (e: Exception) {
            Log.w(TAG, "SOCKS5 connect to $host:$port failed: ${e.message}")
            null
        }
    }

    private fun httpProxyConnect(node: ProxyNode, host: String, port: Int, existing: Socket? = null): Socket? {
        return try {
            val s = existing ?: run {
                val sock = Socket()
                sock.connect(InetSocketAddress(node.host, node.port), 5000)
                sock.soTimeout = 15000
                sock
            }
            val auth = if (node.username.isNotEmpty()) {
                val creds = java.util.Base64.getEncoder().encodeToString("${node.username}:${node.password}".toByteArray())
                "Proxy-Authorization: Basic $creds\r\n"
            } else ""
            val connect = "CONNECT $host:$port HTTP/1.1\r\n${auth}Host: $host\r\n\r\n"
            s.outputStream.write(connect.toByteArray())
            s.outputStream.flush()
            val resp = s.inputStream.bufferedReader().readLine()
            if (resp?.contains("200") == true) s else { if (existing == null) s.close(); null }
        } catch (e: Exception) { null }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        try {
            val buf = ByteArray(16384)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                output.flush()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
        Log.i(TAG, "ChainProxy stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
