package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.MetricReading

@Dao
interface MetricReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<MetricReading>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: MetricReading)

    @Query("""
        SELECT * FROM metric_readings
        WHERE metricType = :metricType
          AND timestamp >= :startMillis
          AND timestamp <= :endMillis
          AND isPrimary = 1
        ORDER BY timestamp ASC
    """)
    suspend fun fetch(metricType: String, startMillis: Long, endMillis: Long): List<MetricReading>

    // `readingKind = null` returns all sources; pass "SENSOR" from engine code
    // to exclude self-reports / derived values from baselines and aggregates
    // (docs/SELF_REPORTED_DATA_HOME.md decision 3).
    @Query("""
        SELECT mr.value FROM metric_readings mr
        INNER JOIN data_sources ds ON mr.sourceId = ds.id
        WHERE mr.metricType = :metricType
          AND mr.timestamp >= :startMillis
          AND mr.timestamp <= :endMillis
          AND mr.isPrimary = 1
          AND (:readingKind IS NULL OR ds.readingKind = :readingKind)
        ORDER BY mr.timestamp ASC
    """)
    suspend fun fetchValues(
        metricType: String,
        startMillis: Long,
        endMillis: Long,
        readingKind: String? = null
    ): List<Double>

    @Query("""
        SELECT * FROM metric_readings
        WHERE metricType = :metricType AND isPrimary = 1
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun fetchLatest(metricType: String, limit: Int = 1): List<MetricReading>

    @Query("SELECT COUNT(*) FROM metric_readings WHERE metricType = :metricType")
    suspend fun count(metricType: String): Int

    @Query("SELECT COUNT(*) FROM metric_readings")
    suspend fun countAll(): Int

    // Counts SENSOR-kind readings only — matches BaselineEngine's filter, so the
    // result reflects what's actually eligible to seed a baseline.
    @Query("""
        SELECT COUNT(*) FROM metric_readings mr
        INNER JOIN data_sources ds ON mr.sourceId = ds.id
        WHERE mr.metricType = :metricType
          AND mr.timestamp >= :startMillis
          AND mr.timestamp <= :endMillis
          AND mr.isPrimary = 1
          AND (:readingKind IS NULL OR ds.readingKind = :readingKind)
    """)
    suspend fun countInRange(
        metricType: String,
        startMillis: Long,
        endMillis: Long,
        readingKind: String? = null
    ): Int

    @Query("SELECT MAX(timestamp) FROM metric_readings WHERE metricType = :metricType AND isPrimary = 1")
    suspend fun lastTimestampFor(metricType: String): Long?

    @Query("""
        SELECT AVG(mr.value) FROM metric_readings mr
        INNER JOIN data_sources ds ON mr.sourceId = ds.id
        WHERE mr.metricType = :metricType
          AND mr.timestamp >= :startMillis
          AND mr.timestamp <= :endMillis
          AND mr.isPrimary = 1
          AND (:readingKind IS NULL OR ds.readingKind = :readingKind)
        GROUP BY (mr.timestamp / :bucketMillis)
        ORDER BY (mr.timestamp / :bucketMillis) ASC
    """)
    suspend fun fetchBucketedMeans(
        metricType: String,
        startMillis: Long,
        endMillis: Long,
        bucketMillis: Long,
        readingKind: String? = null
    ): List<Double>

    @Query("SELECT MIN(timestamp) FROM metric_readings")
    suspend fun oldestTimestamp(): Long?

    data class SourceFreshnessRow(
        val sourceId: String,
        val lastTimestamp: Long,
        val readingCount: Int,
    )

    @Query("""
        SELECT sourceId AS sourceId,
               COALESCE(MAX(timestamp), 0) AS lastTimestamp,
               COUNT(*) AS readingCount
        FROM metric_readings
        WHERE isPrimary = 1
        GROUP BY sourceId
    """)
    suspend fun sourceFreshness(): List<SourceFreshnessRow>

    data class MetricStatusRow(
        val metricType: String,
        val lastTimestamp: Long,
        val count24h: Int,
        val countTotal: Int,
    )

    @Query("""
        SELECT metricType AS metricType,
               COALESCE(MAX(timestamp), 0) AS lastTimestamp,
               COALESCE(SUM(CASE WHEN timestamp >= :since24h THEN 1 ELSE 0 END), 0) AS count24h,
               COUNT(*) AS countTotal
        FROM metric_readings
        WHERE isPrimary = 1
        GROUP BY metricType
    """)
    suspend fun statusSummary(since24h: Long): List<MetricStatusRow>

    @Query("SELECT * FROM metric_readings WHERE createdAt > :sinceMillis ORDER BY createdAt ASC")
    suspend fun fetchCreatedAfter(sinceMillis: Long): List<MetricReading>

    /**
     * Per-metric-type row count + most-recent timestamp, count-descending.
     * Powers the debug screen in #253 ("what's actually in my DB right now").
     * `lastTimestamp` is nullable because empty buckets (no surviving rows
     * for a metric after a pruning sweep) report MAX(...) = NULL.
     */
    data class MetricTypeCountRow(
        val metricType: String,
        val count: Int,
        val lastTimestamp: Long?,
    )

    @Query("""
        SELECT metricType AS metricType,
               COUNT(*) AS count,
               MAX(timestamp) AS lastTimestamp
        FROM metric_readings
        GROUP BY metricType
        ORDER BY count DESC
    """)
    suspend fun metricTypeCounts(): List<MetricTypeCountRow>

    /**
     * Per-sourceId row count for a single metric type, count-descending.
     * Used by the debug screen to surface vendor-specific drift on the
     * top metrics (e.g. HR coming half from Health Connect and half from
     * a paired wearable). Joins the data_sources table for the human-
     * readable label.
     */
    data class SourceCountRow(
        val sourceId: String,
        val sourceLabel: String?,
        val count: Int,
    )

    @Query("""
        SELECT mr.sourceId AS sourceId,
               ds.deviceName AS sourceLabel,
               COUNT(*) AS count
        FROM metric_readings mr
        LEFT JOIN data_sources ds ON mr.sourceId = ds.id
        WHERE mr.metricType = :metricType
        GROUP BY mr.sourceId
        ORDER BY count DESC
    """)
    suspend fun sourceCountsForMetric(metricType: String): List<SourceCountRow>

    @Query("DELETE FROM metric_readings WHERE timestamp < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    @Query("DELETE FROM metric_readings WHERE timestamp >= :afterMillis")
    suspend fun deleteAfter(afterMillis: Long): Int

    /**
     * Delete a single reading by id. Used by the headache / migraine
     * entry repos (#283 Cut 2) so an entity-table delete also clears
     * the parent HEADACHE/CLUSTER/MIGRAINE_ATTACK_EVENT row written
     * alongside it. Callers are responsible for cascading to any
     * linked MEDICATION_INTAKE child (via the
     * [com.bios.app.model.HeadacheEventFields.ABORTIVE_MEDICATION_INTAKE_ID]
     * payload field) and for clearing `event_payloads` via
     * [EventPayloadFieldDao.deleteForReading].
     */
    @Query("DELETE FROM metric_readings WHERE id = :readingId")
    suspend fun deleteById(readingId: String): Int

    @Query("DELETE FROM metric_readings")
    suspend fun deleteAll()
}
