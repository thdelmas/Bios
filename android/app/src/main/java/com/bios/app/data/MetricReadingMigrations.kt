package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for the `metric_readings` table.
 *
 * ## 30 → 31 — unique index on `(sourceId, metricType, timestamp)`
 *
 * Until v31 `metric_readings.id` was a fresh `UUID.randomUUID()` on
 * every insert, so `OnConflictStrategy.REPLACE` only resolved on the
 * primary key and never fired for the *logical* identity of a reading.
 * Effect: every Gadgetbridge / Health Connect re-sync of the same
 * window produced a parallel set of rows. Owners saw "lots of
 * duplicates" on the Heart Rate dashboard (and other auto-sampled
 * metrics) after a few days of normal use.
 *
 * The fix is twofold:
 *
 * 1. **Migration:** dedupe existing rows by `(sourceId, metricType,
 *    timestamp)`, keeping the highest-confidence survivor (ties broken
 *    by newest `createdAt`, then by id as a stable last tie-break).
 *    Mirrors [com.bios.app.ingest.Deduplicator.exactMatchDedup]'s
 *    pick-best rule — same semantics, just applied to disk.
 * 2. **Unique index:** prevent future double-inserts at the SQL layer.
 *    `INSERT OR REPLACE` on a conflicting unique index updates the
 *    existing row in place, which is exactly what we want for
 *    re-syncs: no growth, no drift.
 *
 * ## Cross-source readings are preserved
 *
 * `sourceId` is in the key. Two sources reporting the same ms (e.g. a
 * paired wearable and Health Connect mirroring the same wearable) keep
 * separate rows; the existing Deduplicator picks `isPrimary` between
 * them at ingest time. The unique constraint only collapses
 * *within-source* duplicates, which is where the actual drift was.
 *
 * ## Cascade behaviour
 *
 * `event_payloads` has `onDelete = CASCADE` on `readingId`. Deleting a
 * duplicate row therefore drops its payload sidecar — fine in
 * practice because the sidecar only exists for manual entries, which
 * are themselves unique per `(sourceId, metricType, timestamp)` by
 * construction.
 */
internal object MetricReadingMigrations {

    /**
     * Pick-best ordering used by [MIGRATION_30_31]'s SQL, expressed in
     * Kotlin so the rule can be unit-tested without bringing up a real
     * SQLite. A row beats another when it has higher confidence, OR
     * equal confidence with newer createdAt, OR both equal with smaller
     * id. The migration's NOT EXISTS subquery encodes exactly this
     * inequality; this comparator is the spec the SQL implements.
     */
    internal val pickBestComparator: Comparator<DedupRow> =
        compareByDescending<DedupRow> { it.confidence }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }

    /** Minimal projection of metric_readings the comparator needs. */
    internal data class DedupRow(
        val id: String,
        val sourceId: String,
        val metricType: String,
        val timestamp: Long,
        val confidence: Int,
        val createdAt: Long,
    )

    /**
     * Pure-Kotlin shadow of the migration's "keep one row per logical
     * key" pass. Returns the survivors, one per `(sourceId, metricType,
     * timestamp)` group, ordered by [pickBestComparator]. Used by the
     * unit test; never called from production code.
     */
    internal fun pickSurvivors(rows: List<DedupRow>): List<DedupRow> =
        rows.groupBy { Triple(it.sourceId, it.metricType, it.timestamp) }
            .map { (_, group) -> group.sortedWith(pickBestComparator).first() }

    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Step 1: choose the surviving row per logical key. The
            // ordering is `confidence DESC, createdAt DESC, id ASC` —
            // identical to the in-memory Deduplicator's pick rule, with
            // `id ASC` added as a stable last tie-break so exactly one
            // row wins even when confidence and createdAt collide.
            //
            // The CASCADE FK from `event_payloads.readingId` cleans up
            // sidecars for the deleted rows automatically.
            db.execSQL(
                """
                DELETE FROM metric_readings
                WHERE id NOT IN (
                    SELECT r1.id
                    FROM metric_readings r1
                    WHERE NOT EXISTS (
                        SELECT 1 FROM metric_readings r2
                        WHERE r2.sourceId   = r1.sourceId
                          AND r2.metricType = r1.metricType
                          AND r2.timestamp  = r1.timestamp
                          AND (
                              r2.confidence > r1.confidence
                              OR (r2.confidence = r1.confidence AND r2.createdAt > r1.createdAt)
                              OR (r2.confidence = r1.confidence AND r2.createdAt = r1.createdAt AND r2.id < r1.id)
                          )
                    )
                )
                """.trimIndent()
            )

            // Step 2: create the unique index. Default Room naming is
            // `index_<table>_<col1>_<col2>...`; the entity declaration
            // must match exactly or Room's first-open schema check
            // fails. Verified by the generated schema validator.
            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                    index_metric_readings_sourceId_metricType_timestamp
                ON metric_readings (sourceId, metricType, timestamp)
                """.trimIndent()
            )
        }
    }
}
