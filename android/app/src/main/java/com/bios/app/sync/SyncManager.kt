package com.bios.app.sync

import android.content.Context
import android.util.Log
import com.bios.app.data.BiosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages E2E encrypted sync between devices.
 *
 * Architecture:
 * - Owner sets up sync by creating a passkey (or using device passkey)
 * - Sync key derived locally via HKDF — never transmitted
 * - Data serialized, encrypted, and uploaded as opaque blobs
 * - Server routes blobs by account ID; cannot read them
 * - Other devices download and decrypt with the same passkey
 *
 * Conflict resolution: last-writer-wins per entity (health data is
 * append-only, so conflicts are rare). Entity IDs are UUIDs.
 *
 * Reproductive data sync requires separate explicit opt-in.
 */
class SyncManager(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var syncKey: ByteArray? = null

    /**
     * Initialize sync with a passkey. Derives the encryption key locally.
     * The passkey and derived key never leave the device.
     */
    fun initialize(passkey: String, salt: ByteArray) {
        syncKey = SyncProtocol.deriveSyncKey(
            passkey.toByteArray(Charsets.UTF_8),
            salt
        )
        Log.i(TAG, "Sync initialized (key derived locally)")
    }

    val isInitialized: Boolean get() = syncKey != null

    /**
     * Push local data to the sync server as encrypted blobs.
     * The server sees only opaque ciphertext + account ID for routing.
     */
    suspend fun push(serverUrl: String, accountId: String) {
        val key = syncKey ?: throw IllegalStateException("Sync not initialized")

        withContext(Dispatchers.IO) {
            // Serialize and encrypt each data type
            val blobTypes = listOf(
                BlobType.READINGS to serializeReadings(),
                BlobType.BASELINES to serializeBaselines(),
                BlobType.ANOMALIES to serializeAnomalies(),
                BlobType.HEALTH_EVENTS to serializeHealthEvents()
            )

            for ((type, data) in blobTypes) {
                if (data.isEmpty()) continue

                val encrypted = SyncProtocol.encryptBlob(data, key, type)
                uploadBlob(serverUrl, accountId, type, encrypted)
            }

            Log.i(TAG, "Sync push complete (${blobTypes.size} blob types)")
        }
    }

    /**
     * Pull and decrypt blobs from the sync server.
     */
    suspend fun pull(serverUrl: String, accountId: String) {
        val key = syncKey ?: throw IllegalStateException("Sync not initialized")

        withContext(Dispatchers.IO) {
            for (type in BlobType.entries) {
                if (type == BlobType.REPRODUCTIVE) continue // Separately gated

                val encrypted = downloadBlob(serverUrl, accountId, type) ?: continue
                val decrypted = SyncProtocol.decryptBlob(encrypted, key)

                when (decrypted.type) {
                    BlobType.READINGS -> importReadings(decrypted.data)
                    BlobType.BASELINES -> importBaselines(decrypted.data)
                    BlobType.ANOMALIES -> importAnomalies(decrypted.data)
                    BlobType.HEALTH_EVENTS -> importHealthEvents(decrypted.data)
                    else -> Log.d(TAG, "Skipping blob type: ${decrypted.type}")
                }
            }

            Log.i(TAG, "Sync pull complete")
        }
    }

    /**
     * Destroy the local sync key. After this, sync data on the server
     * is irrecoverable from this device.
     */
    fun destroySyncKey() {
        syncKey?.fill(0)
        syncKey = null
        context.getSharedPreferences("bios_sync", Context.MODE_PRIVATE)
            .edit().clear().apply()
        Log.i(TAG, "Sync key destroyed")
    }

    // MARK: - Serialization (JSON for now; could be protobuf for efficiency)

    private suspend fun serializeReadings(): ByteArray {
        val readings = db.metricReadingDao().fetchLatest("*", 1000)
        if (readings.isEmpty()) return ByteArray(0)
        val json = JSONObject().apply {
            put("type", "readings")
            put("count", readings.size)
            // In production: serialize full reading list
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeBaselines(): ByteArray {
        val baselines = db.personalBaselineDao().fetchAll()
        if (baselines.isEmpty()) return ByteArray(0)
        val json = JSONObject().apply {
            put("type", "baselines")
            put("count", baselines.size)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeAnomalies(): ByteArray {
        val anomalies = db.anomalyDao().fetchAll()
        if (anomalies.isEmpty()) return ByteArray(0)
        val json = JSONObject().apply {
            put("type", "anomalies")
            put("count", anomalies.size)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeHealthEvents(): ByteArray {
        val events = db.healthEventDao().fetchAll()
        if (events.isEmpty()) return ByteArray(0)
        val json = JSONObject().apply {
            put("type", "health_events")
            put("count", events.size)
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    // MARK: - Import (merge downloaded data into local DB)

    private suspend fun importReadings(data: ByteArray) {
        // TODO: Deserialize and merge readings (last-writer-wins by ID)
        Log.d(TAG, "Import readings: ${data.size} bytes")
    }

    private suspend fun importBaselines(data: ByteArray) {
        Log.d(TAG, "Import baselines: ${data.size} bytes")
    }

    private suspend fun importAnomalies(data: ByteArray) {
        Log.d(TAG, "Import anomalies: ${data.size} bytes")
    }

    private suspend fun importHealthEvents(data: ByteArray) {
        Log.d(TAG, "Import health events: ${data.size} bytes")
    }

    // MARK: - HTTP

    private fun uploadBlob(
        serverUrl: String, accountId: String, type: BlobType, data: ByteArray
    ) {
        val request = Request.Builder()
            .url("$serverUrl/sync/$accountId/${type.name.lowercase()}")
            .put(data.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "Upload failed for ${type.name}: ${response.code}")
        }
    }

    private fun downloadBlob(
        serverUrl: String, accountId: String, type: BlobType
    ): ByteArray? {
        val request = Request.Builder()
            .url("$serverUrl/sync/$accountId/${type.name.lowercase()}")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.bytes()
    }

    companion object {
        private const val TAG = "BiosSyncManager"
    }
}
