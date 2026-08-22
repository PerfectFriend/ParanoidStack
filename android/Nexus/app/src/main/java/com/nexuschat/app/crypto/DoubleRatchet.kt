package com.nexuschat.app.crypto

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DoubleRatchet private constructor(
    private val sk: ByteArray,
    private val dhr: ByteArray?,
    private val dhs: ByteArray,
    private var dhsPriv: ByteArray,
    private var dhrPriv: ByteArray?,
    private var rk: ByteArray,
    private var ck: ByteArray,
    private var n: Int,
    private var pn: Int,
    private var pns: MutableMap<Int, ByteArray>
) {
    companion object {
        private const val TAG = "NexusChat/Ratchet"
        private const val AES_KEY_SIZE = 32
        private const val AES_IV_SIZE = 12
        private const val AEAD_TAG_SIZE = 16
        private const val DH_OUTPUT_SIZE = 32
        private const val RATCHET_KEY_SIZE = 32
        private const val CHAIN_KEY_SIZE = 32
        private const val MAX_SKIP = 100

        @Volatile private var instance: DoubleRatchet? = null

        fun initialize(sharedSecret: ByteArray): DoubleRatchet {
            val (pub, priv) = generateDH()
            val rk = kdfRk(sharedSecret, pub)
            val ck = kdfCk(rk, ByteArray(32))
            return DoubleRatchet(
                sk = sharedSecret,
                dhr = null,
                dhs = pub,
                dhsPriv = priv,
                dhrPriv = null,
                rk = rk,
                ck = ck,
                n = 0,
                pn = 0,
                pns = mutableMapOf()
            )
        }

        fun fromState(state: RatchetState): DoubleRatchet {
            return DoubleRatchet(
                sk = state.sk,
                dhr = state.dhr,
                dhs = state.dhs,
                dhsPriv = state.dhsPriv,
                dhrPriv = state.dhrPriv,
                rk = state.rk,
                ck = state.ck,
                n = state.n,
                pn = state.pn,
                pns = state.pns.toMutableMap()
            )
        }

        data class RatchetState(
            val sk: ByteArray,
            val dhr: ByteArray?,
            val dhs: ByteArray,
            val dhsPriv: ByteArray,
            val dhrPriv: ByteArray?,
            val rk: ByteArray,
            val ck: ByteArray,
            val n: Int,
            val pn: Int,
            val pns: Map<Int, ByteArray>
        )

        private fun kdfRk(rk: ByteArray, dhOut: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(rk, "HmacSHA256"))
            mac.update(dhOut)
            return mac.doFinal().copyOf(RATCHET_KEY_SIZE)
        }

        private fun kdfCk(ck: ByteArray, dhOut: ByteArray = ByteArray(32)): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ck, "HmacSHA256"))
            mac.update(byteArrayOf(0x01))
            mac.update(dhOut)
            return mac.doFinal().copyOf(CHAIN_KEY_SIZE)
        }

        private fun aeadEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, ad: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
            cipher.updateAAD(ad)
            return cipher.doFinal(plaintext)
        }

        private fun aeadDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, ad: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce))
            cipher.updateAAD(ad)
            return cipher.doFinal(ciphertext)
        }

        private fun dh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
            try {
                val privParams = org.bouncycastle.crypto.params.X25519PrivateKeyParameters(privateKey, 0)
                val pubParams = org.bouncycastle.crypto.params.X25519PublicKeyParameters(publicKey, 0)
                val shared = ByteArray(32)
                privParams.generateSecret(pubParams, shared, 0)
                return shared
            } catch (e: Exception) {
                Log.e(TAG, "DH failed: ${e.message}")
                return ByteArray(32) { 0 }
            }
        }

        private fun concat(a: ByteArray, b: ByteArray): ByteArray = a + b

        private fun generateDH(): Pair<ByteArray, ByteArray> {
            val rng = SecureRandom()
            val keyGen = org.bouncycastle.crypto.generators.X25519KeyPairGenerator()
            keyGen.init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(rng))
            val kp = keyGen.generateKeyPair()
            val priv = (kp.private as org.bouncycastle.crypto.params.X25519PrivateKeyParameters).encoded
            val pub = (kp.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters).encoded
            return Pair(pub, priv)
        }
    }

    fun ratchetEncrypt(plaintext: ByteArray, ad: ByteArray = byteArrayOf()): Message {
        val mk = kdfCk(ck)
        val header = Header(dhs, pn, n)
        val nonce = ByteArray(AES_IV_SIZE).also { it[0] = (n shr 24).toByte(); it[1] = (n shr 16).toByte(); it[2] = (n shr 8).toByte(); it[3] = n.toByte() }
        val ciphertext = aeadEncrypt(mk, nonce, plaintext, concat(ad, header.encode()))
        n++
        return Message(header, ciphertext)
    }

    fun ratchetDecrypt(msg: Message, ad: ByteArray = byteArrayOf()): ByteArray? {
        return try {
            val header = msg.header
            if (dhr != null && header.dh != null) {
                val dhOut = dh(dhsPriv, header.dh)
                val (rkNew, ckNew) = kdfRkAndCk(rk, dhOut)
                rk = rkNew; ck = ckNew
                dhrPriv = dhsPriv.copyOf()
                dhsPriv = ByteArray(32).also { SecureRandom().nextBytes(it) }
                pn = n; n = 0
            }
            var mk: ByteArray
            if (header.n < n) {
                val ckTemp = skipMessageKeys(header.pn)
                mk = kdfCk(ckTemp ?: return null)
            } else {
                mk = kdfCk(ck)
                ck = kdfCk(ck)
                n++
            }
            val nonce = ByteArray(AES_IV_SIZE).also { it[0] = (header.n shr 24).toByte(); it[1] = (header.n shr 16).toByte(); it[2] = (header.n shr 8).toByte(); it[3] = header.n.toByte() }
            aeadDecrypt(mk, nonce, msg.ciphertext, concat(ad, header.encode()))
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed: ${e.message}")
            null
        }
    }

    private fun skipMessageKeys(until: Int): ByteArray? {
        var ckTemp = ck
        for (i in n until until) {
            val mk = kdfCk(ckTemp)
            if (i == until - 1) return mk
            ckTemp = kdfCk(ckTemp)
        }
        return null
    }

    private fun kdfRkAndCk(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(rk, "HmacSHA256"))
        mac.update(dhOut)
        val rkNew = mac.doFinal().copyOf(RATCHET_KEY_SIZE)
        mac.init(SecretKeySpec(rkNew, "HmacSHA256"))
        mac.update(byteArrayOf(0x01))
        val ckNew = mac.doFinal().copyOf(CHAIN_KEY_SIZE)
        return Pair(rkNew, ckNew)
    }

    fun getState(): RatchetState = RatchetState(sk, dhr, dhs, dhsPriv, dhrPriv, rk, ck, n, pn, pns.toMap())

    data class Header(val dh: ByteArray?, val pn: Int, val n: Int) {
        fun encode(): ByteArray {
            val dhLen = if (dh != null) 32 else 0
            val buf = ByteArray(1 + dhLen + 4 + 4)
            buf[0] = if (dh != null) 1 else 0
            if (dh != null) System.arraycopy(dh, 0, buf, 1, 32)
            buf[1 + dhLen] = (pn shr 24).toByte()
            buf[2 + dhLen] = (pn shr 16).toByte()
            buf[3 + dhLen] = (pn shr 8).toByte()
            buf[4 + dhLen] = pn.toByte()
            buf[5 + dhLen] = (n shr 24).toByte()
            buf[6 + dhLen] = (n shr 16).toByte()
            buf[7 + dhLen] = (n shr 8).toByte()
            buf[8 + dhLen] = n.toByte()
            return buf
        }
    }

    data class Message(val header: Header, val ciphertext: ByteArray)

    fun getPublicKey(): ByteArray = dhs
    fun getSessionKey(): ByteArray = sk
}
