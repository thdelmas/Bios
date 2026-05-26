package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.EsasReport

/**
 * Persistence layer for owner-administered ESAS-r symptom-burden
 * reports (issue #185). Closes audit gap §2.8.
 *
 * Owner-administered, never inferred. The trend view consumes
 * [fetchInRange] / [fetchAll]; the capture screen consumes [insert].
 */
@Dao
interface EsasReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: EsasReport): Long

    @Delete
    suspend fun delete(report: EsasReport)

    @Query("DELETE FROM esas_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM esas_reports WHERE id = :id")
    suspend fun fetchById(id: Long): EsasReport?

    @Query("SELECT * FROM esas_reports ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<EsasReport>

    /**
     * Reports whose [EsasReport.timestamp] falls in `[fromMillis, toMillis]`,
     * newest first. Inclusive on both ends.
     */
    @Query("""
        SELECT * FROM esas_reports
        WHERE timestamp BETWEEN :fromMillis AND :toMillis
        ORDER BY timestamp DESC
    """)
    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<EsasReport>

    @Query("SELECT COUNT(*) FROM esas_reports")
    suspend fun count(): Int
}
