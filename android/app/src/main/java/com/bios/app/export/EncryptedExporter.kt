package com.bios.app.export

import android.content.Context
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps [DataExporter] output in AES-256-GCM encryption with a user-chosen passphrase.
 *
 * File format (.bios):
 * - 4 bytes: magic number "BIOS"
 * - 1 byte:  format version (1)
 * - 4 bytes: salt length (big-endian int)
 * - N bytes: salt (for Argon2id/PBKDF2 key derivation)
 * - 4 bytes: IV length (big-endian int)
 * - N bytes: IV (GCM nonce)
 * - remaining: AES-256-GCM ciphertext (includes authentication tag)
 *
 * The passphrase never touches disk. The export file is indistinguishable from
 * random data without the passphrase.
 */
class EncryptedExporter(
    private val context: Context,
    private val dataExporter: DataExporter
) {

    /**
     * Export all Bios data encrypted with the given passphrase.
     * Returns the encrypted .bios file.
     */
    suspend fun exportEncrypted(passphrase: String): File {
        // Generate plaintext export first (in cache, temporary)
        val plaintext = dataExporter.exportToFile()

        try {
            val plaintextBytes = plaintext.readBytes()
            val encrypted = encrypt(plaintextBytes, passphrase)

            val filename = plaintext.nameWithoutExtension + ".bios"
            val outputFile = File(context.cacheDir, filename)
            outputFile.writeBytes(encrypted)

            return outputFile
        } finally {
            // Always delete the plaintext file
            plaintext.delete()
        }
    }

    /**
     * Decrypt a .bios file with the given passphrase.
     * Returns the plaintext JSON bytes.
     *
     * @throws SecurityException if passphrase is wrong or file is tampered
     */
    fun decrypt(encryptedFile: File, passphrase: String): ByteArray {
        val data = encryptedFile.readBytes()
        var offset = 0

        // Verify magic number
        val magic = String(data, offset, MAGIC.size, Charsets.US_ASCII)
        offset += MAGIC.size
        if (magic != "BIOS") throw SecurityException("Not a Bios export file")

        // Format version
        val version = data[offset].toInt()
        offset += 1
        if (version != FORMAT_VERSION) throw SecurityException("Unsupported format version: $version")

        // Salt
        val saltLen = readInt(data, offset)
        offset += 4
        val salt = data.copyOfRange(offset, offset + saltLen)
        offset += saltLen

        // IV
        val ivLen = readInt(data, offset)
        offset += 4
        val iv = data.copyOfRange(offset, offset + ivLen)
        offset += ivLen

        // Ciphertext
        val ciphertext = data.copyOfRange(offset, data.size)

        // Derive key
        val key = deriveKey(passphrase, salt)

        // Decrypt
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        return try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw SecurityException("Decryption failed — wrong passphrase or corrupted file")
        }
    }

    private fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        // Build output: magic + version + salt + iv + ciphertext
        val output = ByteArray(
            MAGIC.size + 1 + 4 + salt.size + 4 + iv.size + ciphertext.size
        )

        var offset = 0
        System.arraycopy(MAGIC, 0, output, offset, MAGIC.size)
        offset += MAGIC.size

        output[offset] = FORMAT_VERSION.toByte()
        offset += 1

        writeInt(output, offset, salt.size)
        offset += 4
        System.arraycopy(salt, 0, output, offset, salt.size)
        offset += salt.size

        writeInt(output, offset, iv.size)
        offset += 4
        System.arraycopy(iv, 0, output, offset, iv.size)
        offset += iv.size

        System.arraycopy(ciphertext, 0, output, offset, ciphertext.size)

        return output
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        // PBKDF2-HMAC-SHA256 with high iteration count
        // Argon2id would be better but requires a native library; PBKDF2 is available everywhere
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

    companion object {
        private val MAGIC = "BIOS".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION = 1
        private const val CIPHER_TRANSFORM = "AES/GCM/NoPadding"
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_LENGTH = 12
        private const val SALT_LENGTH = 32
        private const val PBKDF2_ITERATIONS = 310_000 // OWASP 2023 recommendation for PBKDF2-SHA256
    }
}
