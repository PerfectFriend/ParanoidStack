package com.paranoidx.demo.network

import com.paranoidx.sdk.protocol.SmpCbor
import com.paranoidx.sdk.protocol.SmpProtocol
import com.paranoidx.sdk.security.InviteLink
import com.paranoidx.sdk.security.SmpKeyManager

/**
 * P2P transport abstraction for backgammon moves.
 */
interface GameTransport {
    suspend fun connect(serverAddress: String): Boolean
    suspend fun send(data: ByteArray)
    suspend fun receive(): ByteArray?
    fun disconnect()
}

/**
 * In-memory local transport for testing P2P on same device.
 */
class LocalGameTransport : GameTransport {
    private val outbox = mutableListOf<ByteArray>()
    private val receivedFromPartner = mutableListOf<ByteArray>()
    private var connected = false

    fun linkPartner(other: LocalGameTransport) {
        // partner reads from our outbox
    }

    fun pushToPartner(item: ByteArray) {
        receivedFromPartner.clear()
    }

    override suspend fun connect(serverAddress: String): Boolean {
        connected = true
        return true
    }

    override suspend fun send(data: ByteArray) {
        outbox.add(data)
    }

    override suspend fun receive(): ByteArray? {
        if (receivedFromPartner.isEmpty()) return null
        return receivedFromPartner.removeFirstOrNull()
    }

    override fun disconnect() { connected = false }
}

/**
 * Game-specific P2P manager wrapping SMP protocol.
 */
class GameP2PManager(
    val transport: GameTransport = LocalGameTransport()
) {
    var identity: SmpKeyManager.SmpIdentity? = null
        private set
    var inviteLink: InviteLink? = null
        private set
    var isConnected = false
        private set

    fun createIdentity(): SmpKeyManager.SmpIdentity? {
        identity = SmpKeyManager.generateIdentityKey()
        return identity
    }

    fun createInvite(displayName: String = "Backgammon Player"): InviteLink? {
        val id = identity ?: return null
        inviteLink = InviteLink(
            pubKey = id.pubKey,
            serverAddress = "smp.example.com:5273",
            queueId = SmpProtocol.generateQueueId(id.pubKey),
            displayName = displayName
        )
        return inviteLink
    }

    suspend fun acceptInvite(link: InviteLink): Boolean {
        val ok = transport.connect(link.serverAddress)
        isConnected = ok
        return ok
    }

    suspend fun sendMove(from: Int, to: Int, diceUsed: Int) {
        val payloadMap = listOf(
            SmpCbor.encodeIntKey(0) to SmpCbor.encodeInt(from),
            SmpCbor.encodeIntKey(1) to SmpCbor.encodeInt(to),
            SmpCbor.encodeIntKey(2) to SmpCbor.encodeInt(diceUsed)
        )
        val body = SmpCbor.encodeMap(payloadMap)
        val frame = SmpProtocol.encodeFrame(SmpProtocol.Cmd.SEND, body)
        transport.send(frame)
    }

    suspend fun receiveMove(): Triple<Int, Int, Int>? {
        val data = transport.receive() ?: return null
        val frame = SmpProtocol.decodeFrame(data) ?: return null
        if (frame.commandCode != SmpProtocol.Cmd.SEND) return null
        try {
            val (cbor, _) = SmpCbor.decode(frame.body)
            val pairs = cbor.asMap()
            val from = (pairs.firstOrNull { (k, _) -> (k as? Int) == 0 }?.second as? SmpCbor.CborValue)?.asInt() ?: 0
            val to = (pairs.firstOrNull { (k, _) -> (k as? Int) == 1 }?.second as? SmpCbor.CborValue)?.asInt() ?: 0
            val dice = (pairs.firstOrNull { (k, _) -> (k as? Int) == 2 }?.second as? SmpCbor.CborValue)?.asInt() ?: 0
            return Triple(from, to, dice)
        } catch (_: Exception) { return null }
    }

    fun disconnect() {
        transport.disconnect()
        isConnected = false
    }
}
