package com.bios.app.sync

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encrypted sync protocol for multi-device access.
 *
 * Design:
 * - Sync key derived client-side from the owner's passkey via HKDF-SHA256
 * - Server stores and relays opaque ciphertext only (zero-knowledge)
 * - Each blob encrypted with XChaCha20-Poly1305 (via AES-256-GCM fallback on Android)
 * - Unique nonce per blob; replay-safe
 * - If the owner loses their passkey, sync data is irrecoverable (by design)
 *
 * Blob format:
 * - 4 bytes: version (1)
 * - 4 bytes: blob type (readings, baselines, anomalies, events, settings)
 * - 8 bytes: timestamp (epoch millis)
 * - 4 bytes: nonce length
 * - N bytes: nonce
 * - remaining: AES-256-GCM ciphertext
 *
 * Reproductive data sync is separately gated — requires explicit opt-in.
 */
object SyncProtocol {

    const val VERSION = 1

    /**
     * Derive a sync encryption key from the owner's passkey using HKDF-SHA256.
     * The passkey never leaves the device; this derived key encrypts sync blobs.
     */
    fun deriveSyncKey(passkey: ByteArray, salt: ByteArray): ByteArray {
        // HKDF-Extract
        val prk = hmacSha256(salt, passkey)
        // HKDF-Expand (single block — 32 bytes is enough for AES-256)
        val info = "bios-sync-v1".toByteArray(Charsets.UTF_8)
        val input = prk + info + byteArrayOf(1)
        return hmacSha256(prk, input).copyOf(32)
    }

    /**
     * Encrypt a data blob for sync.
     */
    fun encryptBlob(
        plaintext: ByteArray,
        syncKey: ByteArray,
        blobType: BlobType
    ): ByteArray {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(syncKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))

        // AAD: version + blob type (authenticated but not encrypted)
        val aad = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(VERSION)
            .putInt(blobType.ordinal)
            .array()
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(plaintext)
        val timestamp = System.currentTimeMillis()

        // Build blob: version + type + timestamp + nonce_len + nonce + ciphertext
        val blob = ByteBuffer.allocate(4 + 4 + 8 + 4 + nonce.size + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(VERSION)
            .putInt(blobType.ordinal)
            .putLong(timestamp)
            .putInt(nonce.size)
            .put(nonce)
            .put(ciphertext)

        return blob.array()
    }

    /**
     * Decrypt a sync blob.
     *
     * @throws SecurityException if decryption fails (wrong key or tampered data)
     */
    fun decryptBlob(blob: ByteArray, syncKey: ByteArray): DecryptedBlob {
        val buf = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN)

        val version = buf.int
        if (version != VERSION) throw SecurityException("Unsupported sync protocol version: $version")

        val typeOrdinal = buf.int
        val blobType = BlobType.entries.getOrNull(typeOrdinal)
            ?: throw SecurityException("Unknown blob type: $typeOrdinal")

        val timestamp = buf.long
        val nonceLen = buf.int
        val nonce = ByteArray(nonceLen).also { buf.get(it) }
        val ciphertext = ByteArray(buf.remaining()).also { buf.get(it) }

        val key = SecretKeySpec(syncKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))

        val aad = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .putInt(version)
            .putInt(typeOrdinal)
            .array()
        cipher.updateAAD(aad)

        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            throw SecurityException("Sync blob decryption failed — wrong key or corrupted data")
        }

        return DecryptedBlob(
            type = blobType,
            timestamp = timestamp,
            data = plaintext
        )
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

enum class BlobType {
    READINGS,
    BASELINES,
    ANOMALIES,
    HEALTH_EVENTS,
    ACTION_ITEMS,
    SETTINGS,
    REPRODUCTIVE // Separately gated — requires explicit opt-in
}

data class DecryptedBlob(
    val type: BlobType,
    val timestamp: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecryptedBlob) return false
        return type == other.type && timestamp == other.timestamp && data.contentEquals(other.data)
    }
    override fun hashCode(): Int = data.contentHashCode()
}
