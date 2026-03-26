package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ComputedAggregate

@Dao
interface ComputedAggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(aggregate: ComputedAggregate)

    @Query("""
        SELECT * FROM computed_aggregates
        WHERE metricType = :metricType
          AND period = :period
          AND periodStart >= :startMillis
          AND periodStart <= :endMillis
        ORDER BY periodStart ASC
    """)
    suspend fun fetch(
        metricType: String,
        period: String,
        startMillis: Long,
        endMillis: Long
    ): List<ComputedAggregate>

    @Query("""
        SELECT * FROM computed_aggregates
        WHERE metricType = :metricType AND period = :period
        ORDER BY periodStart DESC
        LIMIT 1
    """)
    suspend fun fetchLatest(metricType: String, period: String): ComputedAggregate?

    @Query("SELECT * FROM computed_aggregates ORDER BY periodStart ASC")
    suspend fun fetchAll(): List<ComputedAggregate>

    @Query("DELETE FROM computed_aggregates")
    suspend fun deleteAll()
}
