package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ScreeningEntry

/**
 * Persistence layer for owner-recorded screening history (#155).
 *
 * The cadence engine's hottest query: [fetchLatestByKey] — "when did the
 * owner last have screening X" — drives the "next due" computation per
 * catalog entry.
 */
@Dao
interface ScreeningEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScreeningEntry): Long

    @Delete
    suspend fun delete(entry: ScreeningEntry)

    @Query("SELECT * FROM screening_entries WHERE id = :id")
    suspend fun fetchById(id: Long): ScreeningEntry?

    @Query("SELECT * FROM screening_entries ORDER BY performedDate DESC")
    suspend fun fetchAll(): List<ScreeningEntry>

    @Query("""
        SELECT * FROM screening_entries
        WHERE screeningKey = :key
        ORDER BY performedDate DESC
        LIMIT 1
    """)
    suspend fun fetchLatestByKey(key: String): ScreeningEntry?

    @Query("SELECT COUNT(*) FROM screening_entries")
    suspend fun count(): Int
}
