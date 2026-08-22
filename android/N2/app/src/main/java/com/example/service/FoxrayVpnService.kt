package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class FoxrayVpnService : VpnService(), Runnable {

    private var vpnThread: Thread? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunInput: InputStream? = null
    private var tunOutput: OutputStream? = null

    @Volatile
    var totalBytesRx: Long = 0
        private set
    @Volatile
    var totalBytesTx: Long = 0
        private set
    @Volatile
    var isConnected: Boolean = false
        private set

    /** Pool of persistent SOCKS5 connections keyed by destination */
    private val connectionPool = ConcurrentHashMap<String, SocksConnection>()

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.DISCONNECT"
        const val CHANNEL_ID = "foxray_vpn_channel"
        const val NOTIFICATION_ID = 4050

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        private var instance: FoxrayVpnService? = null

        fun getStats(): Pair<Long, Long> {
            val svc = instance ?: return Pair(0L, 0L)
            return Pair(svc.totalBytesRx, svc.totalBytesTx)
        }

        fun isActive(): Boolean {
            return instance?.isConnected == true
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ACTION_DISCONNECT == intent?.action) {
            stopVpn()
            return START_NOT_STICKY
        }
        isServiceRunning = true
        startVpn()
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        instance = null
        super.onDestroy()
    }

    private fun startVpn() {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        stopVpnThread()
        isConnected = true
        vpnThread = Thread(this, "FoxrayVpnThread").apply { start() }
    }

    private fun stopVpn() {
        isServiceRunning = false
        isConnected = false
        stopVpnThread()
        stopForeground(STOP_FOREGROUND_REMOVE)
        connectionPool.values.forEach { it.close() }
        connectionPool.clear()
        closeVpnInterface()
    }

    private fun stopVpnThread() {
        vpnThread?.interrupt()
        vpnThread = null
    }

    private fun closeVpnInterface() {
        try { tunInput?.close() } catch (_: Exception) { Log.w("FoxrayVpnService", "close error") }
        try { tunOutput?.close() } catch (_: Exception) { Log.w("FoxrayVpnService", "close error") }
        try { vpnInterface?.close() } catch (_: Exception) { Log.w("FoxrayVpnService", "close error") }
        vpnInterface = null
        tunInput = null
        tunOutput = null
    }

    override fun run() {
        try {
            val isPrepared = prepare(this) == null
            if (!isPrepared) {
                Log.w("FoxrayVpnService", "VPN not authorized!")
                while (!Thread.currentThread().isInterrupted && isServiceRunning) {
                    Thread.sleep(1500)
                }
                return
            }

            // Route ALL IPv4 + IPv6 traffic through the VPN
            val builder = Builder()
                .setSession("FoxrayVpn")
                .setMtu(1500)
                .addAddress("10.8.0.2", 24)
                .addAddress("fd00:1:2:3::2", 120)
                .addDnsServer("8.8.8.8")
                // Split 0.0.0.0/0 into two routes for Android compatibility
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
                .addRoute("::", 0)

            vpnInterface = builder.establish()
            val fd = vpnInterface ?: run {
                Log.e("FoxrayVpnService", "Failed to establish TUN")
                return
            }

            tunInput = ParcelFileDescriptor.AutoCloseInputStream(fd)
            tunOutput = ParcelFileDescriptor.AutoCloseOutputStream(fd)
            val buffer = ByteArray(32767)
            val socksProxyHost = "127.0.0.1"
            val socksProxyPort = 10808

            Log.i("FoxrayVpnService", "TUN established, routing all IPv4 traffic through SOCKS5")
            isConnected = true

            while (!Thread.currentThread().isInterrupted && isServiceRunning) {
                val bytesRead = tunInput?.read(buffer) ?: -1
                if (bytesRead <= 0) {
                    if (bytesRead < 0) break  // TUN interface closed — stop reading
                    else continue  // bytesRead == 0 — wait for data
                }
                totalBytesRx += bytesRead
                forwardPacket(buffer, bytesRead, socksProxyHost, socksProxyPort)
            }
        } catch (e: Exception) {
            Log.e("FoxrayVpnService", "VPN Error", e)
        } finally {
            isConnected = false
            connectionPool.values.forEach { it.close() }
            connectionPool.clear()
            closeVpnInterface()
            isServiceRunning = false
        }
    }

    /** Holds a persistent SOCKS5 connection with its reader thread */
    private class SocksConnection(
        val socket: Socket,
        private val tunOutput: OutputStream?,
        private val onClosed: (String) -> Unit,
        private val key: String
    ) {
        @Volatile
        var closed = false

        fun startReader() {
            thread(name = "SocksRead-$key") {
                try {
                    val buf = ByteArray(65536)
                    val input = socket.getInputStream()
                    while (!closed && !socket.isClosed) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        tunOutput?.write(buf, 0, n)
                        tunOutput?.flush()
                    }
                } catch (_: java.lang.Exception) { Log.w("FoxrayVpnService", "ignored exception") } finally {
                    close()
                    onClosed(key)
                }
            }
        }

        fun close() {
            closed = true
            try { socket.close() } catch (_: java.lang.Exception) { Log.w("FoxrayVpnService", "ignored exception") }
        }
    }

    private fun forwardPacket(packet: ByteArray, length: Int, proxyHost: String, proxyPort: Int) {
        if (length < 20) return
        val versionAndIhl = packet[0].toInt() and 0xFF
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || ihl > length) return
        val protocol = packet[9].toInt() and 0xFF

        val dstIp = buildString {
            append(packet[16].toInt() and 0xFF)
            append('.')
            append(packet[17].toInt() and 0xFF)
            append('.')
            append(packet[18].toInt() and 0xFF)
            append('.')
            append(packet[19].toInt() and 0xFF)
        }
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        val tcpFlags = packet[ihl + 13].toInt() and 0xFF

        // TCP flags: SYN=0x02, FIN=0x01, RST=0x04, ACK=0x10
        val isSYN = (tcpFlags and 0x02) != 0
        val isFIN = (tcpFlags and 0x01) != 0
        val isRST = (tcpFlags and 0x04) != 0

        val tcpDataOffset = ihl + (((packet[ihl + 12].toInt() and 0xF0) ushr 4) * 4)
        val payloadLen = length - tcpDataOffset

        val connKey = "$dstIp:$dstPort"

        when {
            protocol == 6 -> handleTCP(connKey, dstIp, dstPort, packet, tcpDataOffset, payloadLen,
                isSYN, isFIN, isRST, proxyHost, proxyPort)
            protocol == 17 -> handleUDP(packet, length, ihl, dstIp, dstPort, proxyHost, proxyPort)
            // ICMP and other protocols silently dropped (TCP-only proxy limitation)
        }
    }

    /** Forward UDP packet directly — no SOCKS5 UDP ASSOCIATE, just log */
    private fun handleUDP(
        packet: ByteArray, length: Int, ihl: Int,
        dstIp: String, dstPort: Int, proxyHost: String, proxyPort: Int
    ) {
        // UDP through SOCKS5 requires UDP ASSOCIATE; for now, just count and drop
        // Full UDP support needs a separate relay tunnel
        totalBytesRx += length
    }

    private fun handleTCP(
        connKey: String, dstIp: String, dstPort: Int,
        packet: ByteArray, tcpDataOffset: Int, payloadLen: Int,
        isSYN: Boolean, isFIN: Boolean, isRST: Boolean,
        proxyHost: String, proxyPort: Int
    ) {
        if (isRST || (isFIN && payloadLen <= 0)) {
            connectionPool.remove(connKey)?.close()
            return
        }

        if (isSYN || !connectionPool.containsKey(connKey)) {
            // Close existing connection if any
            connectionPool.remove(connKey)?.close()

            if (payloadLen <= 0 && !isSYN) return

            thread(name = "VpnConn-$connKey") {
                try {
                    val socks = Socket()
                    socks.connect(InetSocketAddress(proxyHost, proxyPort), 5000)
                    socks.soTimeout = 30000

                    // SOCKS5 handshake
                    socks.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                    val handshakeResp = ByteArray(2)
                    if (socks.getInputStream().read(handshakeResp) < 2) {
                        socks.close(); return@thread
                    }

                    // SOCKS5 CONNECT with IPv4 address type (0x01) for IP addresses
                    val ipParts = dstIp.split('.').map { it.toInt() and 0xFF }
                    socks.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00, 0x01))
                    socks.getOutputStream().write(byteArrayOf(
                        ipParts[0].toByte(), ipParts[1].toByte(),
                        ipParts[2].toByte(), ipParts[3].toByte()
                    ))
                    socks.getOutputStream().write((dstPort shr 8) and 0xFF)
                    socks.getOutputStream().write(dstPort and 0xFF)

                    val connResp = ByteArray(4)
                    if (socks.getInputStream().read(connResp) < 4 || connResp[1] != 0x00.toByte()) {
                        val errCode = connResp[1].toInt() and 0xFF
                        Log.w("FoxrayVpnService", "SOCKS5 error $errCode for $connKey")
                        socks.close(); return@thread
                    }

                    skipSocksBoundAddress(socks.getInputStream(), connResp[3])

                    // Register in pool and start reader
                    val conn = SocksConnection(socks, tunOutput, { connectionPool.remove(it) }, connKey)
                    connectionPool[connKey] = conn
                    conn.startReader()

                    // Send initial payload
                    if (payloadLen > 0) {
                        socks.getOutputStream().write(packet, tcpDataOffset, payloadLen)
                        socks.getOutputStream().flush()
                        totalBytesTx += payloadLen
                    }
                } catch (e: Exception) {
                    Log.w("FoxrayVpnService", "Conn error for $connKey", e)
                    connectionPool.remove(connKey)?.close()
                }
            }
        } else {
            // Reuse existing connection
            val conn = connectionPool[connKey]
            if (conn != null && !conn.socket.isClosed) {
                try {
                    if (payloadLen > 0) {
                        conn.socket.getOutputStream().write(packet, tcpDataOffset, payloadLen)
                        conn.socket.getOutputStream().flush()
                        totalBytesTx += payloadLen
                    }
                } catch (e: Exception) {
                    Log.w("FoxrayVpnService", "Write error for $connKey, removing", e)
                    connectionPool.remove(connKey)?.close()
                }
            }
        }
    }

    private fun skipSocksBoundAddress(input: InputStream, addrType: Byte) {
        when (addrType.toInt() and 0xFF) {
            1 -> input.read(ByteArray(6))
            3 -> { val len = input.read(); if (len > 0) input.read(ByteArray(len + 2)) }
            4 -> input.read(ByteArray(18))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Foxray VPN", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java))?.createNotificationChannel(ch)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = if (intent != null) {
            PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foxray Core VPN")
            .setContentText("VLESS/VMess profile active")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
