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

    @Query("SELECT MIN(timestamp) FROM metric_readings")
    suspend fun oldestTimestamp(): Long?

    @Query("DELETE FROM metric_readings WHERE timestamp < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    @Query("DELETE FROM metric_readings")
    suspend fun deleteAll()
}
