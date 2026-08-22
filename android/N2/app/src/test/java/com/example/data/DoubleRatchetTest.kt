package com.example.data

import org.junit.Assert.*
import org.junit.Test

class DoubleRatchetTest {

    @Test
    fun testX3DHInitiateAndReceive() {
        val aliceIdentity = NaClCrypto.generateKeyPair()
        val aliceSignedPreKey = NaClCrypto.generateKeyPair()
        val aliceOneTime = NaClCrypto.generateKeyPair()
        val bobIdentity = NaClCrypto.generateKeyPair()
        val bobEphemeral = NaClCrypto.generateKeyPair()

        val x3dhAlice = X3DHState(
            identityKeyPair = aliceIdentity,
            signedPreKeyPair = aliceSignedPreKey,
            oneTimePreKeys = listOf(aliceOneTime),
            sessionKeys = mutableMapOf()
        )

        val bobSignedPreKey = NaClCrypto.generateKeyPair()
        val aliceSk = x3dhAlice.initiateSession(
            peerIdentityKey = bobIdentity.public.encoded,
            peerSignedPreKey = bobSignedPreKey.public.encoded,
            peerOneTimePreKey = bobEphemeral.public.encoded,
            ourIdentityKey = aliceIdentity,
            ourEphemeralKey = aliceSignedPreKey,
            peerIdentityPublicKey = bobIdentity.public.encoded
        )

        val x3dhBob = X3DHState(
            identityKeyPair = bobIdentity,
            signedPreKeyPair = bobIdentity,
            oneTimePreKeys = emptyList(),
            sessionKeys = mutableMapOf()
        )

        val bobSk = x3dhBob.receiveSession(
            peerIdentityKey = aliceIdentity.public.encoded,
            peerEphemeralKey = aliceSignedPreKey.public.encoded,
            ourSignedPreKey = bobSignedPreKey,
            ourOneTimePreKey = bobEphemeral,
            ourIdentityKey = bobIdentity
        )

        assertArrayEquals("X3DH shared secrets must match", aliceSk, bobSk)
        assertTrue(aliceSk.isNotEmpty())
    }

    @Test
    fun testDoubleRatchetRoundtrip() {
        val sharedSecret = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val bobKeyPair = NaClCrypto.generateKeyPair()

        // Alice initiates ratchet with Bob's public key
        val aliceState = DoubleRatchet.ratchetInit(sharedSecret, bobKeyPair.public.encoded)
        assertNotNull(aliceState)

        val msg = "Hi".encodeToByteArray()
        val ad = "ad".encodeToByteArray()

        // Alice encrypts -> message contains her new DH public key
        val (aliceStateAfter, ratchetMsg) = DoubleRatchet.ratchetEncrypt(aliceState, msg, ad)

        // Bob creates a receiving state using his own keypair
        // Set dhRatchetPublicKey to something different from msg.dhPublicKey
        // so the DH ratchet triggers in ratchetDecrypt
        val dummyKey = NaClCrypto.generateKeyPair().public.encoded
        val bobState = RatchetState(
            dhRatchetKeyPair = bobKeyPair,
            dhRatchetPublicKey = dummyKey,
            rootKey = sharedSecret,
            chainKeySending = ByteArray(0),
            chainKeyReceiving = ByteArray(0)
        )

        // Bob decrypts: DH(bob_priv, alice_dh_pub) == DH(alice_dh_priv, bob_pub)
        // Both produce the same shared secret, leading to matching keys
        val (bobStateAfter, decrypted) = DoubleRatchet.ratchetDecrypt(bobState, ratchetMsg, ad)
        assertArrayEquals("Decrypted must match original", msg, decrypted)
    }
}
