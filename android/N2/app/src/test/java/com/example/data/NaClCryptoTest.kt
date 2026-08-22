package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPair

class NaClCryptoTest {

    @Test
    fun testGenerateKeyPair() {
        val kp = NaClCrypto.generateKeyPair()
        assertNotNull(kp)
        assertNotNull(kp.public)
        assertNotNull(kp.private)
        assertTrue(kp.public.encoded.isNotEmpty())
        assertTrue(kp.private.encoded.isNotEmpty())
    }

    @Test
    fun testDH() {
        val alice = NaClCrypto.generateKeyPair()
        val bob = NaClCrypto.generateKeyPair()
        val shared1 = NaClCrypto.dh(alice.private, bob.public)
        val shared2 = NaClCrypto.dh(bob.private, alice.public)
        assertArrayEquals("DH shared secrets must match", shared1, shared2)
        assertTrue(shared1.isNotEmpty())
    }

    @Test
    fun testCryptoBoxRoundtrip() {
        val alice = NaClCrypto.generateKeyPair()
        val bob = NaClCrypto.generateKeyPair()
        val msg = "Hello SimpleX!".encodeToByteArray()
        val nonce = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }

        val ct = NaClCrypto.cryptoBox(msg, nonce, bob.public.encoded, alice.private.encoded)
        assertNotNull(ct)
        assertTrue(ct.size > msg.size)

        val decrypted = NaClCrypto.cryptoBoxOpen(ct, nonce, alice.public.encoded, bob.private.encoded)
        assertArrayEquals("decrypted must match original", msg, decrypted)
    }

    @Test
    fun testCryptoBoxAfterNm() {
        val subKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val msg = "Test afterNm".encodeToByteArray()
        val nonce = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }

        val ct = NaClCrypto.cryptoBoxAfterNm(msg, nonce, subKey)
        val decrypted = NaClCrypto.cryptoBoxOpenAfterNm(ct, nonce, subKey)
        assertArrayEquals(msg, decrypted)
    }

    @Test(expected = RuntimeException::class)
    fun testTamperedCiphertext() {
        val subKey = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val msg = "Test tamper".encodeToByteArray()
        val nonce = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }

        val ct = NaClCrypto.cryptoBoxAfterNm(msg, nonce, subKey)
        ct[0] = (ct[0].toInt() xor 0xFF).toByte()
        NaClCrypto.cryptoBoxOpenAfterNm(ct, nonce, subKey)
    }
}
