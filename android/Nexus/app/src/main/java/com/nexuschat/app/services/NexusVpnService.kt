package com.nexuschat.app.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexuschat.app.MainActivity
import com.nexuschat.app.NexusChatApp
import com.nexuschat.app.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

class NexusVpnService : VpnService() {
    companion object {
        private const val TAG = "NexusChat/VpnSvc"
        private const val VPN_MTU = 1500
        private const val NOTIF_ID = 1005
        private var vpnInterface: ParcelFileDescriptor? = null
        private var tunnelThread: Thread? = null
        @Volatile var isRunning = false
            private set
        private val tcpConnections = ConcurrentHashMap<ConnKey, TcpRelay>()
        private var nextConnId = 1L
        private val torSocksHost = "127.0.0.1"
        private val torSocksPort = 9050
        private data class ConnKey(val srcAddr: Int, val srcPort: Int, val dstAddr: Int, val dstPort: Int)
    }

    data class TcpRelay(
        val connId: Long,
        val srcAddr: Int, val srcPort: Int,
        val dstAddr: Int, val dstPort: Int,
        val socksSocket: Socket,
        val outStream: java.io.OutputStream,
        val inStream: java.io.InputStream,
        val relayThread: Thread
    )

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Bridge VPN starting..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "start"
        when (action) { "start" -> startVpn(); "stop" -> stopVpn() }
        return START_STICKY
    }

    override fun onRevoke() { stopVpn() }

    private fun startVpn(): Boolean {
        if (isRunning) return true
        return try {
            val builder = Builder()
            builder.setMtu(VPN_MTU)
            builder.addAddress("10.111.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("1.1.1.1")
            builder.setSession("NexusChat-Bridge")
            builder.setBlocking(true)
            builder.setConfigureIntent(
                PendingIntent.getActivity(this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE)
            )
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN establish returned null")
                updateNotification("VPN denied")
                return false
            }
            isRunning = true
            tunnelThread = Thread { tunnelLoop() }.apply { isDaemon = true; start() }
            updateNotification("Bridge VPN active")
            Log.i(TAG, "Bridge VPN established — routing all traffic through Tor")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VPN start failed: ${e.message}")
            updateNotification("VPN failed")
            false
        }
    }

    private fun tunnelLoop() {
        val input = FileInputStream(vpnInterface?.fileDescriptor)
        val output = FileOutputStream(vpnInterface?.fileDescriptor)
        val packet = ByteArray(VPN_MTU)
        while (isRunning) {
            try {
                val len = input.read(packet)
                if (len <= 0) continue
                routePacket(packet, len, output)
            } catch (e: Exception) {
                if (isRunning) Log.w(TAG, "Tunnel I/O: ${e.message}")
            }
        }
    }

    private fun routePacket(packet: ByteArray, len: Int, output: FileOutputStream) {
        val buf = ByteBuffer.wrap(packet, 0, len).order(ByteOrder.BIG_ENDIAN)
        val versionAndIhl = buf.get().toInt() and 0xFF
        if (versionAndIhl shr 4 != 4) return
        val ihl = (versionAndIhl and 0x0F) * 4
        val totalLen = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
        buf.position(2)
        buf.get() ; buf.get()
        val flagsAndFrag = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
        if (flagsAndFrag and 0x3FFF != 0) return
        val ttl = buf.get().toInt() and 0xFF
        val protocol = buf.get().toInt() and 0xFF
        buf.position(ihl)
        val srcAddr = buf.getInt()
        val dstAddr = buf.getInt()

        if (protocol == 6) handleTcpPacket(packet, len, ihl, srcAddr, dstAddr, output, totalLen)
    }

    private fun handleTcpPacket(packet: ByteArray, len: Int, ihl: Int,
                                srcAddr: Int, dstAddr: Int,
                                output: FileOutputStream, totalLen: Int) {
        val buf = ByteBuffer.wrap(packet, ihl, len - ihl).order(ByteOrder.BIG_ENDIAN)
        val srcPort = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
        val dstPort = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
        val seqNum = buf.getInt()
        val ackNum = buf.getInt()
        val dataOffsetAndFlags = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
        val dataOffset = ((dataOffsetAndFlags shr 8) and 0xF0) shr 2
        val flags = dataOffsetAndFlags and 0x3F
        val fin = (flags and 0x01) != 0
        val syn = (flags and 0x02) != 0
        val rst = (flags and 0x04) != 0
        val payloadStart = ihl + dataOffset
        val payloadLen = len - payloadStart

        val key = ConnKey(srcAddr, srcPort, dstAddr, dstPort)
        val existing = tcpConnections[key]

        if (syn && !rst && existing == null && payloadLen == 0) {
            establishSocksRelay(srcAddr, srcPort, dstAddr, dstPort, seqNum, ackNum, output)
            return
        }
        if (existing != null) {
            if (payloadLen > 0) {
                try {
                    existing.outStream.write(packet, payloadStart, payloadLen)
                    existing.outStream.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "TCP relay write error: ${e.message}")
                    closeRelay(key, existing)
                }
            }
            if (fin || rst) closeRelay(key, existing)
        }
    }

    private fun establishSocksRelay(srcAddr: Int, srcPort: Int,
                                    dstAddr: Int, dstPort: Int,
                                    seqNum: Int, ackNum: Int,
                                    output: FileOutputStream) {
        val connId: Long
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(torSocksHost, torSocksPort), 10000)
            sock.soTimeout = 30000

            val bos = sock.getOutputStream()
            val bis = sock.getInputStream()

            bos.write(byteArrayOf(0x05, 0x01, 0x00))
            val handshake = ByteArray(2)
            if (bis.read(handshake) != 2 || handshake[1] != 0.toByte()) {
                sock.close(); return
            }

            val dstBytes = ByteBuffer.allocate(4).putInt(dstAddr).array()
            val ipStr = "${dstBytes[0].toInt() and 0xFF}.${dstBytes[1].toInt() and 0xFF}.${dstBytes[2].toInt() and 0xFF}.${dstBytes[3].toInt() and 0xFF}"
            val hostBytes = ipStr.toByteArray()
            val socksReq = byteArrayOf(0x05, 0x01, 0x00, 0x03, hostBytes.size.toByte()) + hostBytes +
                    byteArrayOf((dstPort shr 8).toByte(), dstPort.toByte())
            bos.write(socksReq)
            bos.flush()
            val resp = ByteArray(4)
            if (bis.read(resp) != 4 || resp[1].toInt() != 0) {
                sock.close(); return
            }
            val bindLen = when (resp[3].toInt() and 0xFF) {
                1 -> 4; 4 -> 16; 3 -> bis.read() + 0; else -> 0
            }
            var skipped = 0
            while (skipped < bindLen) { val n = bis.read(ByteArray(bindLen - skipped)); if (n < 0) break; skipped += n }
            bis.read(ByteArray(2))

            connId = nextConnId++
            val relay = TcpRelay(connId, srcAddr, srcPort, dstAddr, dstPort, sock, bos, bis,
                Thread.currentThread())

            tcpConnections[ConnKey(srcAddr, srcPort, dstAddr, dstPort)] = relay

            val replyThread = Thread {
                val tcpBuf = ByteArray(VPN_MTU - 40)
                var mySeq = ackNum
                var myAck = seqNum + 1
                try {
                    while (isRunning && tcpConnections.containsKey(ConnKey(srcAddr, srcPort, dstAddr, dstPort))) {
                        val rn = bis.read(tcpBuf)
                        if (rn < 0) break
                        val wrapped = buildTcpPacket(dstAddr, srcAddr, dstPort, srcPort,
                            myAck, mySeq, tcpBuf, 0, rn, 0x10.toByte(), output)
                        mySeq += rn
                        output.write(wrapped)
                        output.flush()
                    }
                } catch (_: Exception) {}
            }.apply { isDaemon = true; start() }
        } catch (e: Exception) {
            Log.w(TAG, "SOCKS5 establish failed for $dstAddr:$dstPort: ${e.message}")
        }
    }



    private fun buildTcpPacket(sAddr: Int, dAddr: Int, sPort: Int, dPort: Int,
                               seq: Int, ack: Int, payload: ByteArray,
                               payloadOff: Int, payloadLen: Int, tcpFlags: Byte,
                               output: FileOutputStream): ByteArray {
        val tcpHdrLen = 20
        val pktLen = 20 + tcpHdrLen + payloadLen
        val pkt = ByteArray(pktLen)
        val bb = ByteBuffer.wrap(pkt).order(ByteOrder.BIG_ENDIAN)
        bb.put(0x45.toByte())
        bb.put(0x00.toByte())
        bb.putShort(pktLen.toShort())
        bb.putInt(0)
        bb.put(0x40.toByte())
        bb.put(0x06.toByte())
        bb.putShort(0)
        bb.putInt(sAddr)
        bb.putInt(dAddr)
        bb.putShort(sPort.toShort())
        bb.putShort(dPort.toShort())
        bb.putInt(seq)
        bb.putInt(ack)
        bb.put(((tcpHdrLen / 4) shl 4).toByte())
        bb.put(tcpFlags)
        bb.putShort(0xFFFF.toShort())
        bb.putShort(0)
        bb.putShort(0)
        if (payloadLen > 0) bb.put(payload, payloadOff, payloadLen)
        val pseudoHdr = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        pseudoHdr.putInt(sAddr); pseudoHdr.putInt(dAddr)
        pseudoHdr.put(0x00.toByte()); pseudoHdr.put(0x06.toByte())
        pseudoHdr.putShort((tcpHdrLen + payloadLen).toShort())
        val csum = computeChecksum(pseudoHdr.array(), bb.array(), 20, tcpHdrLen + payloadLen)
        pkt[16] = (csum shr 8).toByte()
        pkt[17] = (csum and 0xFF).toByte()
        val ipCsum = computeChecksum(pkt, 20)
        pkt[10] = (ipCsum shr 8).toByte()
        pkt[11] = (ipCsum and 0xFF).toByte()
        return pkt
    }

    private fun computeChecksum(buf: ByteArray, len: Int): Int {
        var sum = 0L
        var i = 0
        while (i < len - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < len) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.toInt() and 0xFFFF
    }

    private fun computeChecksum(header: ByteArray, data: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        val bb = ByteBuffer.allocate(header.size + len).order(ByteOrder.BIG_ENDIAN)
        bb.put(header); bb.put(data, off, len)
        val buf = bb.array()
        var i = 0
        while (i < buf.size - 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < buf.size) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv().toInt()) and 0xFFFF
    }

    private fun closeRelay(key: ConnKey, relay: TcpRelay) {
        tcpConnections.remove(key)
        try { relay.socksSocket.close() } catch (_: Exception) {}
    }

    private fun stopVpn() {
        isRunning = false
        tcpConnections.forEach { (_, r) -> try { r.socksSocket.close() } catch (_: Exception) {} }
        tcpConnections.clear()
        tunnelThread?.interrupt(); tunnelThread = null
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        updateNotification("Bridge VPN stopped")
        Log.i(TAG, "Bridge VPN stopped")
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NexusChatApp.CH_TOR)
            .setContentTitle("NexusChat Bridge")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setContentIntent(pi)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
