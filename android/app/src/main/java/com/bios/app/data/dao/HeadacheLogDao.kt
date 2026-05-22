package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.HeadacheLog

/**
 * Persistence layer for the generic owner-logged headache diary
 * (issue #207, audit gap §2.5). Separate from
 * [com.bios.app.data.dao.MigraineAttackDao] because the entities have
 * different shapes — see [HeadacheLog] vs. [com.bios.app.model.MigraineAttack].
 *
 * The diary screen consumes [fetchAll] / [fetchInRange]; the MOH evaluator
 * consumes [fetchInRange] over a 90-day window to count acute-treatment
 * days alongside the migraine-attack record.
 */
@Dao
interface HeadacheLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HeadacheLog): Long

    @Delete
    suspend fun delete(log: HeadacheLog)

    @Query("DELETE FROM headache_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM headache_logs WHERE id = :id")
    suspend fun fetchById(id: String): HeadacheLog?

    @Query("SELECT * FROM headache_logs ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<HeadacheLog>

    /**
     * Logs whose [HeadacheLog.timestamp] falls in `[fromMillis, toMillis]`,
     * newest first. Inclusive on both ends.
     */
    @Query("""
        SELECT * FROM headache_logs
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        ORDER BY timestamp DESC
    """)
    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<HeadacheLog>

    @Query("SELECT COUNT(*) FROM headache_logs")
    suspend fun count(): Int
}
