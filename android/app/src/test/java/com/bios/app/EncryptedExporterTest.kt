package com.bios.app

import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tests for EncryptedExporter crypto logic.
 *
 * The encrypt/decrypt methods are private, so we mirror the file-format logic
 * here to verify the crypto roundtrip, key derivation, and format validation
 * without needing an Android context.
 */
class EncryptedExporterTest {

    // Constants mirrored from EncryptedExporter
    private val MAGIC = "BIOS".toByteArray(Charsets.US_ASCII)
    private val FORMAT_VERSION = 1
    private val CIPHER_TRANSFORM = "AES/GCM/NoPadding"
    private val KEY_LENGTH_BITS = 256
    private val GCM_TAG_BITS = 128
    private val GCM_IV_LENGTH = 12
    private val SALT_LENGTH = 32
    private val PBKDF2_ITERATIONS = 310_000

    // --- Encrypt / Decrypt roundtrip ---

    @Test
    fun `encrypt then decrypt recovers original plaintext`() {
        val plaintext = "health data: resting HR 62 bpm".toByteArray()
        val passphrase = "strong-passphrase-2024"

        val encrypted = encrypt(plaintext, passphrase)
        val decrypted = decrypt(encrypted, passphrase)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `roundtrip works with empty plaintext`() {
        val plaintext = ByteArray(0)
        val passphrase = "p@ss"

        val encrypted = encrypt(plaintext, passphrase)
        val decrypted = decrypt(encrypted, passphrase)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `roundtrip works with large payload`() {
        val plaintext = ByteArray(100_000) { (it % 256).toByte() }
        val passphrase = "large-file-test"

        val encrypted = encrypt(plaintext, passphrase)
        val decrypted = decrypt(encrypted, passphrase)

        assertArrayEquals(plaintext, decrypted)
    }

    // --- File format validation ---

    @Test
    fun `encrypted output starts with BIOS magic`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        val magic = String(encrypted, 0, 4, Charsets.US_ASCII)
        assertEquals("BIOS", magic)
    }

    @Test
    fun `encrypted output has version byte after magic`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        assertEquals(FORMAT_VERSION, encrypted[4].toInt())
    }

    @Test
    fun `encrypted output contains salt of correct length`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        val saltLen = readInt(encrypted, 5)
        assertEquals(SALT_LENGTH, saltLen)
    }

    @Test
    fun `encrypted output contains IV of correct length`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        val saltLen = readInt(encrypted, 5)
        val ivLen = readInt(encrypted, 5 + 4 + saltLen)
        assertEquals(GCM_IV_LENGTH, ivLen)
    }

    @Test
    fun `encrypted output is larger than plaintext due to overhead`() {
        val plaintext = "test".toByteArray()
        val encrypted = encrypt(plaintext, "pass")
        // Overhead: 4 (magic) + 1 (version) + 4 + 32 (salt) + 4 + 12 (IV) + 16 (GCM tag) = 73
        assertTrue(encrypted.size > plaintext.size + 70)
    }

    // --- Wrong passphrase ---

    @Test(expected = SecurityException::class)
    fun `decrypt with wrong passphrase throws SecurityException`() {
        val encrypted = encrypt("secret data".toByteArray(), "correct-pass")
        decrypt(encrypted, "wrong-pass")
    }

    // --- Tampered data ---

    @Test(expected = SecurityException::class)
    fun `decrypt with bad magic throws SecurityException`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        encrypted[0] = 'X'.code.toByte()
        decrypt(encrypted, "pass")
    }

    @Test(expected = SecurityException::class)
    fun `decrypt with bad version throws SecurityException`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        encrypted[4] = 99.toByte()
        decrypt(encrypted, "pass")
    }

    @Test(expected = SecurityException::class)
    fun `decrypt with flipped ciphertext bit throws SecurityException`() {
        val encrypted = encrypt("test".toByteArray(), "pass")
        // Flip a bit in the ciphertext region (past header + salt + IV)
        val ciphertextOffset = 5 + 4 + SALT_LENGTH + 4 + GCM_IV_LENGTH
        encrypted[ciphertextOffset] = (encrypted[ciphertextOffset].toInt() xor 0x01).toByte()
        decrypt(encrypted, "pass")
    }

    // --- Key derivation ---

    @Test
    fun `same passphrase and salt produce same key`() {
        val salt = ByteArray(SALT_LENGTH) { 42 }
        val key1 = deriveKey("passphrase", salt)
        val key2 = deriveKey("passphrase", salt)
        assertArrayEquals(key1.encoded, key2.encoded)
    }

    @Test
    fun `different salts produce different keys`() {
        val salt1 = ByteArray(SALT_LENGTH) { 1 }
        val salt2 = ByteArray(SALT_LENGTH) { 2 }
        val key1 = deriveKey("passphrase", salt1)
        val key2 = deriveKey("passphrase", salt2)
        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    @Test
    fun `derived key is 256 bits`() {
        val salt = ByteArray(SALT_LENGTH) { 0 }
        val key = deriveKey("test", salt)
        assertEquals(32, key.encoded.size) // 256 bits = 32 bytes
    }

    // --- Two encryptions of same plaintext differ (random salt + IV) ---

    @Test
    fun `two encryptions of same data produce different ciphertext`() {
        val plaintext = "identical data".toByteArray()
        val pass = "same-pass"
        val enc1 = encrypt(plaintext, pass)
        val enc2 = encrypt(plaintext, pass)
        assertFalse(enc1.contentEquals(enc2))
    }

    // --- writeInt / readInt roundtrip ---

    @Test
    fun `writeInt and readInt roundtrip`() {
        val values = intArrayOf(0, 1, 255, 256, 65535, 16777216, Int.MAX_VALUE)
        for (v in values) {
            val buf = ByteArray(4)
            writeInt(buf, 0, v)
            assertEquals(v, readInt(buf, 0))
        }
    }

    // --- Mirror methods from EncryptedExporter ---

    private fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val output = ByteArray(MAGIC.size + 1 + 4 + salt.size + 4 + iv.size + ciphertext.size)
        var offset = 0
        System.arraycopy(MAGIC, 0, output, offset, MAGIC.size); offset += MAGIC.size
        output[offset] = FORMAT_VERSION.toByte(); offset += 1
        writeInt(output, offset, salt.size); offset += 4
        System.arraycopy(salt, 0, output, offset, salt.size); offset += salt.size
        writeInt(output, offset, iv.size); offset += 4
        System.arraycopy(iv, 0, output, offset, iv.size); offset += iv.size
        System.arraycopy(ciphertext, 0, output, offset, ciphertext.size)
        return output
    }

    private fun decrypt(data: ByteArray, passphrase: String): ByteArray {
        var offset = 0
        val magic = String(data, offset, MAGIC.size, Charsets.US_ASCII); offset += MAGIC.size
        if (magic != "BIOS") throw SecurityException("Not a Bios export file")

        val version = data[offset].toInt(); offset += 1
        if (version != FORMAT_VERSION) throw SecurityException("Unsupported format version: $version")

        val saltLen = readInt(data, offset); offset += 4
        val salt = data.copyOfRange(offset, offset + saltLen); offset += saltLen
        val ivLen = readInt(data, offset); offset += 4
        val iv = data.copyOfRange(offset, offset + ivLen); offset += ivLen
        val ciphertext = data.copyOfRange(offset, data.size)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw SecurityException("Decryption failed — wrong passphrase or corrupted file")
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun writeInt(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value shr 24).toByte()
        array[offset + 1] = (value shr 16).toByte()
        array[offset + 2] = (value shr 8).toByte()
        array[offset + 3] = value.toByte()
    }

    private fun readInt(array: ByteArray, offset: Int): Int {
        return ((array[offset].toInt() and 0xFF) shl 24) or
            ((array[offset + 1].toInt() and 0xFF) shl 16) or
            ((array[offset + 2].toInt() and 0xFF) shl 8) or
            (array[offset + 3].toInt() and 0xFF)
    }
}
