package com.nexuschat.app.services

import android.util.Log
import kotlinx.coroutines.*
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Obfs4Transport private constructor(
    private val onStatusChange: (Boolean, String) -> Unit
) {
    companion object {
        private const val TAG = "NexusChat/Obfs4"
        private const val NODE_ID_LEN = 20
        private const val PUBKEY_LEN = 32
        private const val KEY_LEN = 32
        private const val IV_LEN = 16
        private const val LOCAL_LISTEN_PORT = 9443
        @Volatile private var instance: Obfs4Transport? = null
        fun getInstance(onStatus: (Boolean, String) -> Unit): Obfs4Transport =
            instance ?: synchronized(this) {
                instance ?: Obfs4Transport(onStatus).also { instance = it }
            }
    }

    data class Obfs4Config(
        val bridgeAddress: String = "",
        val bridgePort: Int = 443,
        val nodeId: String = "",
        val bridgePubkey: String = "",
        val iatMode: Int = 0,
        val localPort: Int = LOCAL_LISTEN_PORT
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rng = SecureRandom()
    private var config = Obfs4Config()
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var activeConnections = 0
    private val nodeIdBytes = ByteArray(NODE_ID_LEN)
    private val bridgePubkeyBytes = ByteArray(PUBKEY_LEN)
    private var ourPrivKey: X25519PrivateKeyParameters? = null
    private var ourPubKey: X25519PublicKeyParameters? = null

    fun start(config: Obfs4Config) {
        this.config = config
        scope.launch {
            try {
                decodeBridgeKeyMaterial()
                generateOurKeypair()
                serverSocket = ServerSocket(config.localPort)
                isRunning = true
                onStatusChange(true, "Obfs4 listening on :${config.localPort}")
                Log.i(TAG, "Obfs4 transport started on :${config.localPort}")
                acceptLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Obfs4 start failed: ${e.message}")
                onStatusChange(false, e.message ?: "start failed")
            }
        }
    }

    private fun decodeBridgeKeyMaterial() {
        if (config.nodeId.length >= NODE_ID_LEN * 2) {
            val hex = config.nodeId.substring(0, NODE_ID_LEN * 2)
            for (i in 0 until NODE_ID_LEN) {
                nodeIdBytes[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16).toByte()
            }
        } else {
            rng.nextBytes(nodeIdBytes)
        }
        if (config.bridgePubkey.length >= PUBKEY_LEN * 2) {
            val hex = config.bridgePubkey.substring(0, PUBKEY_LEN * 2)
            for (i in 0 until PUBKEY_LEN) {
                bridgePubkeyBytes[i] = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16).toByte()
            }
        } else {
            rng.nextBytes(bridgePubkeyBytes)
        }
    }

    private fun generateOurKeypair() {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(rng))
        val kp = gen.generateKeyPair()
        ourPrivKey = kp.private as X25519PrivateKeyParameters
        ourPubKey = kp.public as X25519PublicKeyParameters
    }

    private suspend fun acceptLoop() = withContext(Dispatchers.IO) {
        while (isRunning) {
            try {
                val client = serverSocket!!.accept()
                activeConnections++
                scope.launch { handleObfs4Connection(client) }
            } catch (e: Exception) { if (isRunning) Log.w(TAG, "Accept error: ${e.message}") }
        }
    }

    private suspend fun handleObfs4Connection(client: Socket) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obfs4 connection from ${client.inetAddress}")
        try {
            val handshakeOk = performObfs4Handshake(client)
            if (handshakeOk) {
                val bridge = Socket()
                bridge.connect(InetSocketAddress(config.bridgeAddress, config.bridgePort), 10000)
                val t1 = launch { obfs4Pipe(client.inputStream, bridge.outputStream, true) }
                val t2 = launch { obfs4Pipe(bridge.inputStream, client.outputStream, false) }
                t1.join(); t2.join()
                bridge.close()
            }
            Unit
        } catch (e: Exception) {
            Log.w(TAG, "Obfs4 session: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
            activeConnections--
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int) {
        var offset = 0
        while (offset < len) {
            val read = input.read(buf, offset, len - offset)
            if (read < 0) throw java.io.EOFException()
            offset += read
        }
    }

    private fun performObfs4Handshake(socket: Socket): Boolean {
        try {
            val clientNodeId = ByteArray(NODE_ID_LEN)
            val clientPubBytes = ByteArray(PUBKEY_LEN)
            val padLenBuf = ByteArray(2)
            readFully(socket.inputStream, clientNodeId, NODE_ID_LEN)
            readFully(socket.inputStream, clientPubBytes, PUBKEY_LEN)
            readFully(socket.inputStream, padLenBuf, 2)
            val padLen = ((padLenBuf[0].toInt() and 0xFF) shl 8) or (padLenBuf[1].toInt() and 0xFF)
            if (padLen > 8192) return false
            if (padLen > 0) {
                val padding = ByteArray(padLen)
                readFully(socket.inputStream, padding, padLen)
            }

            val priv = ourPrivKey ?: return false
            val pub = ourPubKey ?: return false
            val clientPub = X25519PublicKeyParameters(clientPubBytes, 0)
            val sharedSecret = ByteArray(32)
            priv.generateSecret(clientPub, sharedSecret, 0)

            val sendKey = deriveKey(sharedSecret, "obfs4_send".toByteArray())
            val recvKey = deriveKey(sharedSecret, "obfs4_recv".toByteArray())

            val serverPubBytes = ByteArray(PUBKEY_LEN)
            pub.encode(serverPubBytes, 0)

            val responsePadLen = rng.nextInt(128) + 16
            socket.outputStream.write(serverPubBytes)
            socket.outputStream.write(byteArrayOf(
                (responsePadLen shr 8).toByte(),
                responsePadLen.toByte()
            ))
            val responsePad = ByteArray(responsePadLen)
            rng.nextBytes(responsePad)
            socket.outputStream.write(responsePad)
            socket.outputStream.flush()

            initSessionCiphers(sendKey, recvKey)
            Log.d(TAG, "Obfs4 handshake complete (shared secret derived)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Obfs4 handshake error: ${e.message}")
            return false
        }
    }

    private fun deriveKey(secret: ByteArray, label: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(label)
        digest.update(secret)
        return digest.digest().copyOf(KEY_LEN)
    }

    private var sendCipher: Cipher? = null
    private var recvCipher: Cipher? = null

    private fun initSessionCiphers(sendKey: ByteArray, recvKey: ByteArray) {
        val sendIv = ByteArray(IV_LEN)
        val recvIv = ByteArray(IV_LEN)
        rng.nextBytes(sendIv)
        rng.nextBytes(recvIv)
        sendCipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "AES"), IvParameterSpec(sendIv))
        }
        recvCipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(recvKey, "AES"), IvParameterSpec(recvIv))
        }
    }

    private fun obfs4Pipe(input: InputStream, output: OutputStream, isClient: Boolean) {
        try {
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                val processed = if (isClient) {
                    sendCipher?.update(buf.copyOf(read)) ?: buf.copyOf(read)
                } else {
                    recvCipher?.update(buf.copyOf(read)) ?: buf.copyOf(read)
                }
                output.write(processed)
                output.flush()
            }
        } catch (_: Exception) { Log.w(TAG, "Obfs4 pipe error") }
    }

    fun getActiveConnections(): Int = activeConnections

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        Log.i(TAG, "Obfs4 transport stopped")
    }

    fun destroy() {
        stop()
        scope.cancel()
        instance = null
    }
}
