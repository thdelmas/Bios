package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.FastStrokeEvent

/**
 * Persistence layer for owner-recorded FAST stroke-recognition checks
 * (issue #207, audit gap §2.15). The screen consumes [insert] / [fetchAll];
 * the FHIR exporter consumes [fetchAll] over the export window to emit
 * suspected-stroke flag observations.
 */
@Dao
interface FastStrokeEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: FastStrokeEvent): Long

    @Delete
    suspend fun delete(event: FastStrokeEvent)

    @Query("DELETE FROM fast_stroke_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM fast_stroke_events WHERE id = :id")
    suspend fun fetchById(id: String): FastStrokeEvent?

    @Query("SELECT * FROM fast_stroke_events ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<FastStrokeEvent>

    /**
     * Events whose [FastStrokeEvent.timestamp] falls in `[fromMillis, toMillis]`,
     * newest first. Inclusive on both ends.
     */
    @Query("""
        SELECT * FROM fast_stroke_events
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        ORDER BY timestamp DESC
    """)
    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<FastStrokeEvent>

    @Query("SELECT COUNT(*) FROM fast_stroke_events")
    suspend fun count(): Int
}
