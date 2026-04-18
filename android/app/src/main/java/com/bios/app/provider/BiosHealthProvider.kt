package com.bios.app.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.bios.app.data.BiosDatabase
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SensorType
import kotlinx.coroutines.runBlocking

/**
 * ContentProvider exposing Bios health data to companion apps (W2F).
 *
 * Read URIs:
 *   content://com.bios.app.health/readings/{metricType}?start={epochMs}&end={epochMs}
 *   content://com.bios.app.health/baselines
 *   content://com.bios.app.health/baselines/{metricType}
 *   content://com.bios.app.health/status                  — one row per known metricType
 *   content://com.bios.app.health/status/{metricType}     — one row for that metricType
 *
 * Write URI (companion signals only):
 *   content://com.bios.app.health/companion/{metricType}
 *   ContentValues: "value" (Double), "timestamp" (Long)
 *   Only accepts MENTAL_HEALTH domain metrics (typing_cadence, circadian_phase_shift, mood_drift_score).
 *
 * Contract: the provider is passive — it serves what SyncWorker has already written.
 * Queries do NOT trigger ingestion. Consumers that need fresher data should rely on
 * SyncWorker's schedule (default: ~15 min) or fall back to Health Connect directly.
 *
 * Security: signature-level permission — only apps signed with the same key can read/write.
 */
class BiosHealthProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.bios.app.health"
        private const val COMPANION_SOURCE_ID = "companion_w2f"

        private const val READINGS = 1
        private const val BASELINES_ALL = 2
        private const val BASELINE_TYPE = 3
        private const val COMPANION_WRITE = 4
        private const val STATUS_ALL = 5
        private const val STATUS_TYPE = 6

        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        // Metric types companion apps are allowed to write
        private val COMPANION_METRICS = setOf(
            "typing_cadence", "circadian_phase_shift", "mood_drift_score"
        )

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "readings/*", READINGS)
            addURI(AUTHORITY, "baselines", BASELINES_ALL)
            addURI(AUTHORITY, "baselines/*", BASELINE_TYPE)
            addURI(AUTHORITY, "companion/*", COMPANION_WRITE)
            addURI(AUTHORITY, "status", STATUS_ALL)
            addURI(AUTHORITY, "status/*", STATUS_TYPE)
        }

        val READING_COLUMNS = arrayOf(
            "id", "metric_type", "value", "timestamp", "duration_sec",
            "source_id", "confidence", "is_primary"
        )

        val BASELINE_COLUMNS = arrayOf(
            "metric_type", "context", "window_days", "computed_at",
            "mean", "std_dev", "p5", "p95", "trend", "trend_slope"
        )

        val STATUS_COLUMNS = arrayOf(
            "metric_type", "last_ingested_at", "reading_count_24h", "reading_count_total"
        )
    }

    private lateinit var db: BiosDatabase
    private var companionSourceEnsured = false

    override fun onCreate(): Boolean {
        db = BiosDatabase.getInstance(context!!)
        return true
    }

    /** Ensure the companion data source row exists (once per provider lifecycle). */
    private fun ensureCompanionSource() {
        if (companionSourceEnsured) return
        runBlocking {
            val existing = db.dataSourceDao().findByType("companion")
            if (existing == null) {
                db.dataSourceDao().insert(DataSource(
                    id = COMPANION_SOURCE_ID,
                    sourceType = "companion",
                    deviceName = "W2F",
                    deviceModel = "Companion App",
                    sensorType = SensorType.DERIVED.name
                ))
            }
        }
        companionSourceEnsured = true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            READINGS -> queryReadings(uri)
            BASELINES_ALL -> queryBaselines(null)
            BASELINE_TYPE -> queryBaselines(uri.lastPathSegment)
            STATUS_ALL -> queryStatus(null)
            STATUS_TYPE -> queryStatus(uri.lastPathSegment)
            else -> null
        }
    }

    /**
     * Accepts companion signals from W2F.
     * Only MENTAL_HEALTH domain metrics are writable — everything else is rejected.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uriMatcher.match(uri) != COMPANION_WRITE) {
            throw UnsupportedOperationException("Only companion/* path accepts writes")
        }
        val metricType = uri.lastPathSegment
            ?: throw IllegalArgumentException("Missing metric type in URI")
        if (metricType !in COMPANION_METRICS) {
            throw SecurityException("Metric '$metricType' is not writable by companion apps")
        }
        val cv = values ?: throw IllegalArgumentException("ContentValues required")
        val value = cv.getAsDouble("value")
            ?: throw IllegalArgumentException("'value' (Double) required")
        val timestamp = cv.getAsLong("timestamp")
            ?: System.currentTimeMillis()

        ensureCompanionSource()

        val reading = MetricReading(
            metricType = metricType,
            value = value,
            timestamp = timestamp,
            sourceId = COMPANION_SOURCE_ID,
            confidence = ConfidenceTier.MEDIUM.level,
            isPrimary = true
        )

        runBlocking { db.metricReadingDao().insert(reading) }

        val resultUri = Uri.withAppendedPath(
            Uri.parse("content://$AUTHORITY/readings"), metricType
        )
        context?.contentResolver?.notifyChange(resultUri, null)
        return resultUri
    }

    private fun queryReadings(uri: Uri): Cursor {
        val metricType = uri.pathSegments[1]
        val startMs = uri.getQueryParameter("start")?.toLongOrNull() ?: 0L
        val endMs = uri.getQueryParameter("end")?.toLongOrNull() ?: System.currentTimeMillis()

        val readings = runBlocking {
            db.metricReadingDao().fetch(metricType, startMs, endMs)
        }

        val cursor = MatrixCursor(READING_COLUMNS, readings.size)
        for (r in readings) {
            cursor.addRow(arrayOf(
                r.id, r.metricType, r.value, r.timestamp, r.durationSec,
                r.sourceId, r.confidence, if (r.isPrimary) 1 else 0
            ))
        }
        return cursor
    }

    private fun queryBaselines(metricType: String?): Cursor {
        val baselines = runBlocking {
            if (metricType != null) {
                listOfNotNull(db.personalBaselineDao().fetch(metricType))
            } else {
                db.personalBaselineDao().fetchAll()
            }
        }

        val cursor = MatrixCursor(BASELINE_COLUMNS, baselines.size)
        for (b in baselines) {
            cursor.addRow(arrayOf(
                b.metricType, b.context, b.windowDays, b.computedAt,
                b.mean, b.stdDev, b.p5, b.p95, b.trend, b.trendSlope
            ))
        }
        return cursor
    }

    /**
     * Returns per-metric ingestion state so consumers can distinguish
     * "Bios empty — fall back to Health Connect" from "Bios has data but is lagging."
     *
     * When [filterMetric] is null every known [MetricType] is included, so callers
     * always see a stable row set (a metric with no readings returns zeros rather than
     * being absent). When [filterMetric] is an unknown key the cursor is empty.
     */
    private fun queryStatus(filterMetric: String?): Cursor {
        val since24h = System.currentTimeMillis() - DAY_MILLIS
        val rows = runBlocking { db.metricReadingDao().statusSummary(since24h) }
            .associateBy { it.metricType }

        val keys = if (filterMetric == null) {
            MetricType.entries.map { it.key }
        } else {
            if (MetricType.fromKey(filterMetric) == null) emptyList() else listOf(filterMetric)
        }

        val cursor = MatrixCursor(STATUS_COLUMNS, keys.size)
        for (key in keys) {
            val row = rows[key]
            cursor.addRow(arrayOf(
                key,
                row?.lastTimestamp ?: 0L,
                row?.count24h ?: 0,
                row?.countTotal ?: 0,
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.bios.health"

    override fun update(uri: Uri, values: ContentValues?, sel: String?, args: Array<out String>?): Int =
        throw UnsupportedOperationException("Use insert for companion signals")

    override fun delete(uri: Uri, sel: String?, args: Array<out String>?): Int =
        throw UnsupportedOperationException("Companion apps cannot delete Bios data")
}
