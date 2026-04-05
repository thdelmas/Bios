package com.bios.app

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Tests Ed25519 signature verification logic used in ModelUpdateManager.
 * Uses Bouncy Castle directly since we can't instantiate ModelUpdateManager
 * without Android context — but we verify the same crypto path.
 */
class ModelUpdateManagerTest {

    @Test
    fun `valid signature is accepted`() {
        val keyPair = generateKeyPair()
        val data = "test model data".toByteArray()
        val signature = sign(data, keyPair.first)
        assertTrue(verify(data, signature, keyPair.second))
    }

    @Test
    fun `tampered data is rejected`() {
        val keyPair = generateKeyPair()
        val data = "test model data".toByteArray()
        val signature = sign(data, keyPair.first)
        val tampered = "tampered model data".toByteArray()
        assertFalse(verify(tampered, signature, keyPair.second))
    }

    @Test
    fun `wrong key is rejected`() {
        val keyPair1 = generateKeyPair()
        val keyPair2 = generateKeyPair()
        val data = "test model data".toByteArray()
        val signature = sign(data, keyPair1.first)
        assertFalse(verify(data, signature, keyPair2.second))
    }

    @Test
    fun `truncated signature is rejected`() {
        val keyPair = generateKeyPair()
        val data = "test model data".toByteArray()
        val signature = sign(data, keyPair.first)
        val truncated = signature.copyOf(32)
        assertFalse(verify(data, truncated, keyPair.second))
    }

    private fun generateKeyPair(): Pair<Ed25519PrivateKeyParameters, ByteArray> {
        val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
        val publicKey = privateKey.generatePublicKey().encoded
        return privateKey to publicKey
    }

    private fun sign(data: ByteArray, privateKey: Ed25519PrivateKeyParameters): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    private fun verify(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val pubKey = org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(publicKeyBytes, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pubKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }
}
