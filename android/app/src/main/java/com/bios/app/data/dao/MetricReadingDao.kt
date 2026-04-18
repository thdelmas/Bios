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

    @Query("""
        SELECT value FROM metric_readings
        WHERE metricType = :metricType
          AND timestamp >= :startMillis
          AND timestamp <= :endMillis
          AND isPrimary = 1
        ORDER BY timestamp ASC
    """)
    suspend fun fetchValues(metricType: String, startMillis: Long, endMillis: Long): List<Double>

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

    @Query("""
        SELECT AVG(value) FROM metric_readings
        WHERE metricType = :metricType
          AND timestamp >= :startMillis
          AND timestamp <= :endMillis
          AND isPrimary = 1
        GROUP BY (timestamp / :bucketMillis)
        ORDER BY (timestamp / :bucketMillis) ASC
    """)
    suspend fun fetchBucketedMeans(
        metricType: String,
        startMillis: Long,
        endMillis: Long,
        bucketMillis: Long
    ): List<Double>

    @Query("SELECT MIN(timestamp) FROM metric_readings")
    suspend fun oldestTimestamp(): Long?

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

    @Query("DELETE FROM metric_readings WHERE timestamp < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    @Query("DELETE FROM metric_readings")
    suspend fun deleteAll()
}
