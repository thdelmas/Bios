package com.bios.app.sync.p2p

import android.content.Context
import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.model.*
import com.bios.app.sync.BlobType
import com.bios.app.sync.SyncProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges the existing SyncManager with Iroh/Willow for delta sync.
 *
 * Each BlobType maps to a key prefix in the shared Willow document.
 * Entries use the entity UUID as the key, so Willow's built-in
 * deduplication handles repeat syncs naturally.
 *
 * Delta tracking: only entities created/modified after the last sync
 * timestamp are pushed. The timestamp per blob type per document is
 * stored in SharedPreferences.
 *
 * All data is encrypted before writing to Iroh docs — the Willow
 * namespace stores only AES-256-GCM ciphertext. Even a compromised
 * peer cannot read the data without the owner's sync key.
 */
class WillowSyncAdapter(
    private val context: Context,
    private val db: BiosDatabase,
    private val irohNode: IrohNode
) {
    private val readingDao = db.metricReadingDao()
    private val baselineDao = db.personalBaselineDao()
    private val anomalyDao = db.anomalyDao()
    private val healthEventDao = db.healthEventDao()

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Sync all entity types (push + pull) with a paired document.
     * Reproductive data is separately gated and only synced if opted in.
     */
    suspend fun syncAll(
        documentId: String,
        syncKey: ByteArray,
        includeReproductive: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val blobTypes = BlobType.entries.filter {
            it != BlobType.REPRODUCTIVE || includeReproductive
        }

        for (type in blobTypes) {
            try {
                pushDelta(documentId, type, syncKey)
                pullDelta(documentId, type, syncKey)
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for ${type.name}", e)
            }
        }
    }

    /**
     * Push entities created after last sync as encrypted entries.
     */
    suspend fun pushDelta(
        documentId: String,
        blobType: BlobType,
        syncKey: ByteArray
    ) = withContext(Dispatchers.IO) {
        val sinceMillis = getLastSyncTimestamp(documentId, blobType)
        val entities = fetchEntitiesSince(blobType, sinceMillis)

        if (entities.isEmpty()) {
            Log.d(TAG, "No new ${blobType.name} entities to push")
            return@withContext
        }

        var pushed = 0
        for ((entityId, json) in entities) {
            val plaintext = json.toByteArray(Charsets.UTF_8)
            val encrypted = SyncProtocol.encryptBlob(plaintext, syncKey, blobType)
            val key = "${blobType.name.lowercase()}/$entityId"
            irohNode.setEntry(documentId, key, encrypted)
            pushed++
        }

        setLastSyncTimestamp(documentId, blobType, System.currentTimeMillis())
        Log.i(TAG, "Pushed $pushed ${blobType.name} entries via P2P")
    }

    /**
     * Pull new entries from the shared document and import.
     */
    suspend fun pullDelta(
        documentId: String,
        blobType: BlobType,
        syncKey: ByteArray
    ) = withContext(Dispatchers.IO) {
        val prefix = "${blobType.name.lowercase()}/"
        val entries = irohNode.listEntries(documentId, prefix)

        if (entries.isEmpty()) return@withContext

        var imported = 0
        for ((_, encrypted) in entries) {
            try {
                val decrypted = SyncProtocol.decryptBlob(encrypted, syncKey)
                if (decrypted.type != blobType) continue
                importEntity(blobType, decrypted.data)
                imported++
            } catch (e: SecurityException) {
                Log.w(TAG, "Skipping entry: ${e.message}")
            }
        }

        Log.i(TAG, "Pulled $imported ${blobType.name} entries via P2P")
    }

    // MARK: - Delta queries (entity → JSON per entity, not bulk)

    private suspend fun fetchEntitiesSince(
        blobType: BlobType,
        sinceMillis: Long
    ): List<Pair<String, String>> {
        return when (blobType) {
            BlobType.READINGS -> fetchReadingsSince(sinceMillis)
            BlobType.BASELINES -> fetchBaselinesSince(sinceMillis)
            BlobType.ANOMALIES -> fetchAnomaliesSince(sinceMillis)
            BlobType.HEALTH_EVENTS -> fetchHealthEventsSince(sinceMillis)
            else -> emptyList()
        }
    }

    private suspend fun fetchReadingsSince(sinceMillis: Long): List<Pair<String, String>> {
        return readingDao.fetchCreatedAfter(sinceMillis).map { r ->
            r.id to JSONObject().apply {
                put("id", r.id); put("metricType", r.metricType)
                put("value", r.value); put("timestamp", r.timestamp)
                put("durationSec", r.durationSec ?: JSONObject.NULL)
                put("sourceId", r.sourceId); put("confidence", r.confidence)
                put("isPrimary", r.isPrimary); put("createdAt", r.createdAt)
            }.toString()
        }
    }

    private suspend fun fetchBaselinesSince(sinceMillis: Long): List<Pair<String, String>> {
        return baselineDao.fetchComputedAfter(sinceMillis).map { b ->
            b.id to JSONObject().apply {
                put("id", b.id); put("metricType", b.metricType)
                put("context", b.context); put("windowDays", b.windowDays)
                put("computedAt", b.computedAt); put("mean", b.mean)
                put("stdDev", b.stdDev); put("p5", b.p5); put("p95", b.p95)
                put("trend", b.trend); put("trendSlope", b.trendSlope)
            }.toString()
        }
    }

    private suspend fun fetchAnomaliesSince(sinceMillis: Long): List<Pair<String, String>> {
        return anomalyDao.fetchCreatedAfter(sinceMillis).map { a ->
            a.id to JSONObject().apply {
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
            }.toString()
        }
    }

    private suspend fun fetchHealthEventsSince(sinceMillis: Long): List<Pair<String, String>> {
        return healthEventDao.fetchCreatedAfter(sinceMillis).map { e ->
            e.id to JSONObject().apply {
                put("id", e.id); put("type", e.type); put("status", e.status)
                put("title", e.title)
                put("description", e.description ?: JSONObject.NULL)
                put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                put("anomalyId", e.anomalyId ?: JSONObject.NULL)
                put("parentEventId", e.parentEventId ?: JSONObject.NULL)
            }.toString()
        }
    }

    // MARK: - Import (reuses same entity deserialization as SyncManager)

    private suspend fun importEntity(blobType: BlobType, data: ByteArray) {
        val json = String(data, Charsets.UTF_8)
        when (blobType) {
            BlobType.READINGS -> importReading(JSONObject(json))
            BlobType.BASELINES -> importBaseline(JSONObject(json))
            BlobType.ANOMALIES -> importAnomaly(JSONObject(json))
            BlobType.HEALTH_EVENTS -> importHealthEvent(JSONObject(json))
            else -> {}
        }
    }

    private suspend fun importReading(o: JSONObject) {
        readingDao.insertAll(listOf(MetricReading(
            id = o.getString("id"),
            metricType = o.getString("metricType"),
            value = o.getDouble("value"),
            timestamp = o.getLong("timestamp"),
            durationSec = if (o.isNull("durationSec")) null else o.getInt("durationSec"),
            sourceId = o.getString("sourceId"),
            confidence = o.getInt("confidence"),
            isPrimary = o.getBoolean("isPrimary"),
            createdAt = o.getLong("createdAt")
        )))
    }

    private suspend fun importBaseline(o: JSONObject) {
        baselineDao.upsert(PersonalBaseline(
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

    private suspend fun importAnomaly(o: JSONObject) {
        anomalyDao.insert(Anomaly(
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
            acknowledged = o.optBoolean("acknowledged", false),
            acknowledgedAt = if (o.isNull("acknowledgedAt")) null else o.getLong("acknowledgedAt")
        ))
    }

    private suspend fun importHealthEvent(o: JSONObject) {
        healthEventDao.insert(HealthEvent(
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

    // MARK: - Delta timestamp tracking

    private fun getLastSyncTimestamp(documentId: String, blobType: BlobType): Long {
        return prefs.getLong("last_sync_${blobType.name}_$documentId", 0L)
    }

    private fun setLastSyncTimestamp(documentId: String, blobType: BlobType, timestamp: Long) {
        prefs.edit().putLong("last_sync_${blobType.name}_$documentId", timestamp).apply()
    }

    fun clearSyncState() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "WillowSync"
        private const val PREFS_NAME = "bios_p2p_sync"
    }
}
