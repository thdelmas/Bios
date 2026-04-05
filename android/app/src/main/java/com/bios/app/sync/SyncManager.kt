package com.bios.app.sync

import android.content.Context
import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.model.Anomaly
import com.bios.app.model.HealthEvent
import com.bios.app.model.MetricReading
import com.bios.app.model.PersonalBaseline
import com.bios.app.platform.IpfsClient
import com.bios.app.sync.p2p.P2PDiscovery
import com.bios.app.sync.p2p.WillowSyncAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
    private val ipfs = IpfsClient()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var syncKey: ByteArray? = null

    /** Iroh/Willow P2P adapter — set via initializeP2P() when Iroh is available. */
    private var willowAdapter: WillowSyncAdapter? = null
    private var p2pDiscovery: P2PDiscovery? = null

    /**
     * Initialize P2P sync with an active Iroh node.
     * Called from BiosApplication after the Iroh node starts.
     */
    fun initializeP2P(adapter: WillowSyncAdapter, discovery: P2PDiscovery) {
        willowAdapter = adapter
        p2pDiscovery = discovery
        Log.i(TAG, "P2P sync initialized")
    }

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
     * Push local data as encrypted blobs.
     *
     * On LETHE (with IPFS): each blob is added to IPFS and its CID stored
     * in a manifest published to PubSub. No server involved.
     * On stock Android: falls back to HTTP PUT to a sync server.
     */
    suspend fun push(serverUrl: String, accountId: String) {
        val key = syncKey ?: throw IllegalStateException("Sync not initialized")

        withContext(Dispatchers.IO) {
            // Priority 1: Iroh P2P delta sync (direct between devices)
            val pairedDevices = p2pDiscovery?.getPairedDevices() ?: emptyList()
            if (willowAdapter != null && pairedDevices.isNotEmpty()) {
                try {
                    for (device in pairedDevices) {
                        willowAdapter!!.syncAll(device.documentId, key)
                    }
                    Log.i(TAG, "P2P sync push complete (${pairedDevices.size} peers)")
                    return@withContext
                } catch (e: Exception) {
                    Log.w(TAG, "P2P sync failed, falling back to IPFS/HTTP", e)
                }
            }

            // Priority 2: IPFS (LETHE) or Priority 3: HTTP (fallback)
            val blobTypes = listOf(
                BlobType.READINGS to serializeReadings(),
                BlobType.BASELINES to serializeBaselines(),
                BlobType.ANOMALIES to serializeAnomalies(),
                BlobType.HEALTH_EVENTS to serializeHealthEvents()
            )

            if (ipfs.isAvailable()) {
                val manifest = JSONObject()
                for ((type, data) in blobTypes) {
                    if (data.isEmpty()) continue
                    val encrypted = SyncProtocol.encryptBlob(data, key, type)
                    val cid = ipfs.add(encrypted)
                    if (cid != null) manifest.put(type.name.lowercase(), cid)
                }
                // Publish manifest so other devices can discover the CIDs
                val msg = JSONObject().apply {
                    put("v", 1)
                    put("account", accountId)
                    put("blobs", manifest)
                    put("ts", System.currentTimeMillis())
                }
                ipfs.pubsubPublish(SYNC_TOPIC, msg.toString().toByteArray(Charsets.UTF_8))
                Log.i(TAG, "IPFS sync push: ${manifest.length()} blobs published")
            } else {
                for ((type, data) in blobTypes) {
                    if (data.isEmpty()) continue
                    val encrypted = SyncProtocol.encryptBlob(data, key, type)
                    uploadBlob(serverUrl, accountId, type, encrypted)
                }
                Log.i(TAG, "HTTP sync push complete")
            }
        }
    }

    /**
     * Pull and decrypt blobs.
     *
     * On LETHE (with IPFS): listens for a sync manifest on PubSub,
     * then fetches each blob by CID. Content-addressed = tamper-proof.
     * On stock Android: falls back to HTTP GET from server.
     */
    suspend fun pull(serverUrl: String, accountId: String) {
        val key = syncKey ?: throw IllegalStateException("Sync not initialized")

        withContext(Dispatchers.IO) {
            // Priority 1: Iroh P2P delta sync
            val pairedDevices = p2pDiscovery?.getPairedDevices() ?: emptyList()
            if (willowAdapter != null && pairedDevices.isNotEmpty()) {
                try {
                    for (device in pairedDevices) {
                        willowAdapter!!.syncAll(device.documentId, key)
                    }
                    Log.i(TAG, "P2P sync pull complete (${pairedDevices.size} peers)")
                    return@withContext
                } catch (e: Exception) {
                    Log.w(TAG, "P2P sync failed, falling back to IPFS/HTTP", e)
                }
            }

            // Priority 2: IPFS, Priority 3: HTTP
            if (ipfs.isAvailable()) {
                val msg = ipfs.pubsubNext(SYNC_TOPIC)
                if (msg != null) {
                    val manifest = JSONObject(String(msg, Charsets.UTF_8))
                    if (manifest.optString("account") != accountId) return@withContext
                    val blobs = manifest.optJSONObject("blobs") ?: return@withContext
                    for (type in BlobType.entries) {
                        if (type == BlobType.REPRODUCTIVE) continue
                        val cid = blobs.optString(type.name.lowercase(), "")
                        if (cid.isBlank()) continue
                        val encrypted = ipfs.cat(cid) ?: continue
                        val decrypted = SyncProtocol.decryptBlob(encrypted, key)
                        importDecrypted(decrypted)
                    }
                }
                Log.i(TAG, "IPFS sync pull complete")
            } else {
                for (type in BlobType.entries) {
                    if (type == BlobType.REPRODUCTIVE) continue
                    val encrypted = downloadBlob(serverUrl, accountId, type) ?: continue
                    val decrypted = SyncProtocol.decryptBlob(encrypted, key)
                    importDecrypted(decrypted)
                }
                Log.i(TAG, "HTTP sync pull complete")
            }
        }
    }

    private suspend fun importDecrypted(decrypted: DecryptedBlob) {
        when (decrypted.type) {
            BlobType.READINGS -> importReadings(decrypted.data)
            BlobType.BASELINES -> importBaselines(decrypted.data)
            BlobType.ANOMALIES -> importAnomalies(decrypted.data)
            BlobType.HEALTH_EVENTS -> importHealthEvents(decrypted.data)
            else -> Log.d(TAG, "Skipping blob type: ${decrypted.type}")
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

    // MARK: - Serialization (JSON — each entity type as a JSONArray)

    private suspend fun serializeReadings(): ByteArray {
        val readings = db.metricReadingDao().fetchLatest("*", 1000)
        if (readings.isEmpty()) return ByteArray(0)
        val arr = JSONArray()
        for (r in readings) {
            arr.put(JSONObject().apply {
                put("id", r.id); put("metricType", r.metricType)
                put("value", r.value); put("timestamp", r.timestamp)
                put("durationSec", r.durationSec ?: JSONObject.NULL)
                put("sourceId", r.sourceId); put("confidence", r.confidence)
                put("isPrimary", r.isPrimary); put("createdAt", r.createdAt)
            })
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeBaselines(): ByteArray {
        val baselines = db.personalBaselineDao().fetchAll()
        if (baselines.isEmpty()) return ByteArray(0)
        val arr = JSONArray()
        for (b in baselines) {
            arr.put(JSONObject().apply {
                put("id", b.id); put("metricType", b.metricType)
                put("context", b.context); put("windowDays", b.windowDays)
                put("computedAt", b.computedAt); put("mean", b.mean)
                put("stdDev", b.stdDev); put("p5", b.p5); put("p95", b.p95)
                put("trend", b.trend); put("trendSlope", b.trendSlope)
            })
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeAnomalies(): ByteArray {
        val anomalies = db.anomalyDao().fetchAll()
        if (anomalies.isEmpty()) return ByteArray(0)
        val arr = JSONArray()
        for (a in anomalies) {
            arr.put(JSONObject().apply {
                put("id", a.id); put("detectedAt", a.detectedAt)
                put("metricTypes", a.metricTypes)
                put("deviationScores", a.deviationScores)
                put("combinedScore", a.combinedScore)
                put("patternId", a.patternId ?: JSONObject.NULL)
                put("severity", a.severity); put("title", a.title)
                put("explanation", a.explanation)
                put("suggestedAction", a.suggestedAction ?: JSONObject.NULL)
                put("acknowledged", a.acknowledged)
                put("acknowledgedAt", a.acknowledgedAt ?: JSONObject.NULL)
                put("feedbackAt", a.feedbackAt ?: JSONObject.NULL)
                put("feltSick", a.feltSick ?: JSONObject.NULL)
                put("visitedDoctor", a.visitedDoctor ?: JSONObject.NULL)
                put("diagnosis", a.diagnosis ?: JSONObject.NULL)
                put("symptoms", a.symptoms ?: JSONObject.NULL)
                put("notes", a.notes ?: JSONObject.NULL)
                put("outcomeAccurate", a.outcomeAccurate ?: JSONObject.NULL)
            })
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun serializeHealthEvents(): ByteArray {
        val events = db.healthEventDao().fetchAll()
        if (events.isEmpty()) return ByteArray(0)
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().apply {
                put("id", e.id); put("type", e.type); put("status", e.status)
                put("title", e.title)
                put("description", e.description ?: JSONObject.NULL)
                put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                put("anomalyId", e.anomalyId ?: JSONObject.NULL)
                put("parentEventId", e.parentEventId ?: JSONObject.NULL)
            })
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    // MARK: - Import (merge downloaded data — last-writer-wins via REPLACE)

    private suspend fun importReadings(data: ByteArray) {
        val arr = JSONArray(String(data, Charsets.UTF_8))
        val readings = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MetricReading(
                id = o.getString("id"),
                metricType = o.getString("metricType"),
                value = o.getDouble("value"),
                timestamp = o.getLong("timestamp"),
                durationSec = if (o.isNull("durationSec")) null else o.getInt("durationSec"),
                sourceId = o.getString("sourceId"),
                confidence = o.getInt("confidence"),
                isPrimary = o.getBoolean("isPrimary"),
                createdAt = o.getLong("createdAt")
            )
        }
        db.metricReadingDao().insertAll(readings)
        Log.d(TAG, "Imported ${readings.size} readings")
    }

    private suspend fun importBaselines(data: ByteArray) {
        val arr = JSONArray(String(data, Charsets.UTF_8))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            db.personalBaselineDao().upsert(PersonalBaseline(
                id = o.getString("id"),
                metricType = o.getString("metricType"),
                context = o.getString("context"),
                windowDays = o.getInt("windowDays"),
                computedAt = o.getLong("computedAt"),
                mean = o.getDouble("mean"),
                stdDev = o.getDouble("stdDev"),
                p5 = o.getDouble("p5"),
                p95 = o.getDouble("p95"),
                trend = o.getString("trend"),
                trendSlope = o.getDouble("trendSlope")
            ))
        }
        Log.d(TAG, "Imported ${arr.length()} baselines")
    }

    private suspend fun importAnomalies(data: ByteArray) {
        val arr = JSONArray(String(data, Charsets.UTF_8))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            db.anomalyDao().insert(Anomaly(
                id = o.getString("id"),
                detectedAt = o.getLong("detectedAt"),
                metricTypes = o.getString("metricTypes"),
                deviationScores = o.getString("deviationScores"),
                combinedScore = o.getDouble("combinedScore"),
                patternId = if (o.isNull("patternId")) null else o.getString("patternId"),
                severity = o.getInt("severity"),
                title = o.getString("title"),
                explanation = o.getString("explanation"),
                suggestedAction = if (o.isNull("suggestedAction")) null else o.getString("suggestedAction"),
                acknowledged = o.getBoolean("acknowledged"),
                acknowledgedAt = if (o.isNull("acknowledgedAt")) null else o.getLong("acknowledgedAt"),
                feedbackAt = if (o.isNull("feedbackAt")) null else o.getLong("feedbackAt"),
                feltSick = if (o.isNull("feltSick")) null else o.getBoolean("feltSick"),
                visitedDoctor = if (o.isNull("visitedDoctor")) null else o.getBoolean("visitedDoctor"),
                diagnosis = if (o.isNull("diagnosis")) null else o.getString("diagnosis"),
                symptoms = if (o.isNull("symptoms")) null else o.getString("symptoms"),
                notes = if (o.isNull("notes")) null else o.getString("notes"),
                outcomeAccurate = if (o.isNull("outcomeAccurate")) null else o.getBoolean("outcomeAccurate")
            ))
        }
        Log.d(TAG, "Imported ${arr.length()} anomalies")
    }

    private suspend fun importHealthEvents(data: ByteArray) {
        val arr = JSONArray(String(data, Charsets.UTF_8))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            db.healthEventDao().insert(HealthEvent(
                id = o.getString("id"),
                type = o.getString("type"),
                status = o.getString("status"),
                title = o.getString("title"),
                description = if (o.isNull("description")) null else o.getString("description"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt"),
                anomalyId = if (o.isNull("anomalyId")) null else o.getString("anomalyId"),
                parentEventId = if (o.isNull("parentEventId")) null else o.getString("parentEventId")
            ))
        }
        Log.d(TAG, "Imported ${arr.length()} health events")
    }

    // MARK: - HTTP

    private fun uploadBlob(
        serverUrl: String, accountId: String, type: BlobType, data: ByteArray
    ) {
        val request = Request.Builder()
            .url("$serverUrl/sync/$accountId/${type.name.lowercase()}")
            .put(data.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
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

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.bytes()
    }

    companion object {
        private const val TAG = "BiosSyncManager"
        const val SYNC_TOPIC = "bios-sync"
    }
}
