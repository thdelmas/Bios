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
import com.bios.app.model.SensorType
import com.bios.contracts.BiosHealthContract
import com.bios.contracts.MetricType
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
 *   content://com.bios.app.health/payload/{readingId}     — composite-event fields
 *
 * Write URI (companion signals only):
 *   content://com.bios.app.health/companion/{metricType}
 *   ContentValues: "value" (Double), "timestamp" (Long)
 *   Accepts MENTAL_HEALTH signals from W2F (typing_cadence, circadian_phase_shift, mood_drift_score)
 *   and INTAKE events from Smokeless (tobacco_use, tobacco_craving).
 *
 * Contract: the provider is passive — it serves what SyncWorker has already written.
 * Queries do NOT trigger ingestion. Consumers that need fresher data should rely on
 * SyncWorker's schedule (default: ~15 min) or fall back to Health Connect directly.
 *
 * Security: two-layer gate.
 *   1. Android dangerous-level permissions (READ_HEALTH / WRITE_COMPANION) — coarse OS filter.
 *   2. [CompanionGate] — fine per-package allowlist managed by the owner in Settings →
 *      Companion Apps. Unknown packages are recorded as PENDING and denied until the owner
 *      explicitly approves them. Revoke is one tap. Aligned with the "owner is final" rule.
 */
class BiosHealthProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = BiosHealthContract.AUTHORITY

        private const val READINGS = 1
        private const val BASELINES_ALL = 2
        private const val BASELINE_TYPE = 3
        private const val COMPANION_WRITE = 4
        private const val STATUS_ALL = 5
        private const val STATUS_TYPE = 6
        private const val PAYLOAD = 7

        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "${BiosHealthContract.PATH_READINGS}/*", READINGS)
            addURI(AUTHORITY, BiosHealthContract.PATH_BASELINES, BASELINES_ALL)
            addURI(AUTHORITY, "${BiosHealthContract.PATH_BASELINES}/*", BASELINE_TYPE)
            addURI(AUTHORITY, "${BiosHealthContract.PATH_COMPANION}/*", COMPANION_WRITE)
            addURI(AUTHORITY, BiosHealthContract.PATH_STATUS, STATUS_ALL)
            addURI(AUTHORITY, "${BiosHealthContract.PATH_STATUS}/*", STATUS_TYPE)
            addURI(AUTHORITY, "${BiosHealthContract.PATH_PAYLOAD}/*", PAYLOAD)
        }

        val READING_COLUMNS = BiosHealthContract.READING_COLUMNS
        val BASELINE_COLUMNS = BiosHealthContract.BASELINE_COLUMNS
        val STATUS_COLUMNS = BiosHealthContract.STATUS_COLUMNS
        val PAYLOAD_COLUMNS = BiosHealthContract.PAYLOAD_COLUMNS
    }

    private lateinit var db: BiosDatabase
    private lateinit var gate: CompanionGate
    private val ensuredSources = mutableSetOf<String>()

    override fun onCreate(): Boolean {
        val ctx = context!!
        db = BiosDatabase.getInstance(ctx)
        val notifier = CompanionAccessNotifier(ctx)
        gate = CompanionGate(
            dao = db.companionGrantDao(),
            onFirstPending = { pkg -> notifier.notifyPending(pkg) }
        )
        return true
    }

    /**
     * Returns true if the calling package is allowed through the owner gate.
     * For reads we resolve via [getCallingPackage]; for writes the calling
     * package is available the same way (ContentProvider IPC is the same path).
     */
    private fun isAllowed(): Boolean {
        val pkg = callingPackage
        return runBlocking { gate.check(pkg) is CompanionGate.Decision.Allow }
    }

    /**
     * Ensure the data source row exists for [companion]. Each companion has its
     * own row so the owner can trace any reading back to the exact app that
     * wrote it. Idempotent — REPLACE handles re-inserts, the in-memory set
     * skips the I/O after the first call per process.
     */
    private fun ensureSourceFor(companion: CompanionContract.Companion) {
        if (companion.sourceId in ensuredSources) return
        runBlocking {
            db.dataSourceDao().insert(DataSource(
                id = companion.sourceId,
                sourceType = "companion",
                deviceName = companion.displayName,
                deviceModel = companion.packageName,
                sensorType = SensorType.DERIVED.name,
                readingKind = companion.defaultReadingKind.name
            ))
        }
        ensuredSources += companion.sourceId
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (!isAllowed()) return null
        return when (uriMatcher.match(uri)) {
            READINGS -> queryReadings(uri)
            BASELINES_ALL -> queryBaselines(null)
            BASELINE_TYPE -> queryBaselines(uri.lastPathSegment)
            STATUS_ALL -> queryStatus(null)
            STATUS_TYPE -> queryStatus(uri.lastPathSegment)
            PAYLOAD -> queryPayload(uri.lastPathSegment)
            else -> null
        }
    }

    /**
     * Accepts companion signals from approved companion apps. Each package may
     * only write the keys allocated to it in [CompanionContract.PACKAGES]; the
     * resulting reading is tagged with that companion's source so provenance
     * is preserved.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val caller = callingPackage
        if (!isAllowed()) {
            throw SecurityException("Companion not approved by owner")
        }
        if (uriMatcher.match(uri) != COMPANION_WRITE) {
            throw UnsupportedOperationException("Only companion/* path accepts writes")
        }
        val metricType = uri.lastPathSegment
            ?: throw IllegalArgumentException("Missing metric type in URI")
        val companion = CompanionContract.sourceFor(caller)
        if (companion == null || metricType !in companion.writableMetrics) {
            throw SecurityException("'$caller' may not write '$metricType'")
        }
        val cv = values ?: throw IllegalArgumentException("ContentValues required")
        val value = cv.getAsDouble(BiosHealthContract.CompanionInsert.VALUE)
            ?: throw IllegalArgumentException("'value' (Double) required")
        val timestamp = cv.getAsLong(BiosHealthContract.CompanionInsert.TIMESTAMP)
            ?: System.currentTimeMillis()

        ensureSourceFor(companion)

        val reading = MetricReading(
            metricType = metricType,
            value = value,
            timestamp = timestamp,
            sourceId = companion.sourceId,
            confidence = ConfidenceTier.MEDIUM.level,
            isPrimary = true
        )

        runBlocking { db.metricReadingDao().insert(reading) }

        val resultUri = Uri.withAppendedPath(
            Uri.parse("content://$AUTHORITY/${BiosHealthContract.PATH_READINGS}"), metricType
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

    /**
     * Returns the composite payload fields for a parent reading. Scalar
     * metrics carry no payload; in that case the cursor is empty rather than
     * null, so callers always get the same shape back.
     */
    private fun queryPayload(readingId: String?): Cursor {
        val fields = if (readingId.isNullOrEmpty()) {
            emptyList()
        } else {
            runBlocking { db.eventPayloadFieldDao().fetchForReading(readingId) }
        }

        val cursor = MatrixCursor(PAYLOAD_COLUMNS, fields.size)
        for (f in fields) {
            cursor.addRow(arrayOf(
                f.readingId, f.fieldKey, f.stringValue, f.doubleValue, f.longValue
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
