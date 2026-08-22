package com.example.data

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SimpleXCryptoTest {

    @Test
    fun testKeyPairGeneration() {
        val kp = SimpleXCrypto.generateKeyPair()
        assertNotNull(kp)
        assertTrue(kp.publicKey.isNotEmpty())
        assertTrue(kp.privateKey.isNotEmpty())
    }

    @Test
    fun testEncryptDecryptAesGcm() {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val plaintext = "Secret message for testing".encodeToByteArray()

        val encrypted = SimpleXCrypto.encryptAesGcm(plaintext, key)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > plaintext.size) // has IV + GCM tag

        val decrypted = SimpleXCrypto.decryptAesGcm(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testSharedSecret() {
        val alice = SimpleXCrypto.generateKeyPair()
        val bob = SimpleXCrypto.generateKeyPair()

        val secret1 = SimpleXCrypto.computeSharedSecret(alice.privateKey, bob.publicKey)
        val secret2 = SimpleXCrypto.computeSharedSecret(bob.privateKey, alice.publicKey)

        assertArrayEquals("Shared secrets must match", secret1, secret2)
    }

    @Test
    fun testDeriveKey() {
        val passphrase = "test-passphrase"
        val salt = "test-salt".encodeToByteArray()
        val key = SimpleXCrypto.deriveKey(passphrase, salt)
        assertTrue(key.isNotEmpty())
        assertEquals(32, key.size)
        // Same inputs produce same key
        val key2 = SimpleXCrypto.deriveKey(passphrase, salt)
        assertArrayEquals(key, key2)
    }

    @Test
    fun testHkdfDerive() {
        val ikm = "input key material".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val info = "info".encodeToByteArray()
        val derived = SimpleXCrypto.hkdfDerive(ikm, salt, info, 32)
        assertTrue(derived.isNotEmpty())
        assertEquals(32, derived.size)
    }

    @Test
    fun testEncryptStorageRoundtrip() {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val data = "Storage data for testing".encodeToByteArray()

        val encrypted = SimpleXCrypto.encryptStorage(data, key)
        assertNotNull(encrypted)

        val decrypted = SimpleXCrypto.decryptStorage(encrypted, key)
        assertArrayEquals(data, decrypted)
    }
}
