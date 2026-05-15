package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.LoggedEvent

@Dao
interface LoggedEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: LoggedEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LoggedEvent>)

    @Query("SELECT * FROM logged_events WHERE id = :id")
    suspend fun fetchById(id: String): LoggedEvent?

    @Query("""
        SELECT * FROM logged_events
        WHERE eventType = :eventType
          AND timestamp >= :startMillis
          AND timestamp <= :endMillis
        ORDER BY timestamp ASC
    """)
    suspend fun fetchByType(
        eventType: String,
        startMillis: Long,
        endMillis: Long
    ): List<LoggedEvent>

    @Query("""
        SELECT * FROM logged_events
        WHERE timestamp >= :startMillis
          AND timestamp <= :endMillis
        ORDER BY timestamp ASC
    """)
    suspend fun fetchInWindow(startMillis: Long, endMillis: Long): List<LoggedEvent>

    @Query("SELECT * FROM logged_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 50): List<LoggedEvent>

    @Query("SELECT COUNT(*) FROM logged_events WHERE eventType = :eventType")
    suspend fun countByType(eventType: String): Int

    @Query("SELECT COUNT(*) FROM logged_events")
    suspend fun countAll(): Int

    @Query("DELETE FROM logged_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM logged_events WHERE timestamp < :beforeMillis")
    suspend fun deleteBefore(beforeMillis: Long): Int

    @Query("DELETE FROM logged_events")
    suspend fun deleteAll()
}
