package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.Anomaly

@Dao
interface AnomalyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anomaly: Anomaly)

    @Query("SELECT * FROM anomalies ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 20): List<Anomaly>

    @Query("SELECT * FROM anomalies WHERE acknowledged = 0 ORDER BY severity DESC, detectedAt DESC")
    suspend fun fetchUnacknowledged(): List<Anomaly>

    @Query("UPDATE anomalies SET acknowledged = 1, acknowledgedAt = :now WHERE id = :id")
    suspend fun acknowledge(id: String, now: Long = System.currentTimeMillis())

    /** Writes the journal note only; journal columns of earlier versions keep their stored values. */
    @Query("UPDATE anomalies SET feedbackAt = :feedbackAt, notes = :notes WHERE id = :id")
    suspend fun saveNote(id: String, notes: String, feedbackAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM anomalies WHERE detectedAt > :sinceMillis ORDER BY detectedAt ASC")
    suspend fun fetchCreatedAfter(sinceMillis: Long): List<Anomaly>

    @Query("SELECT * FROM anomalies ORDER BY detectedAt DESC")
    suspend fun fetchAll(): List<Anomaly>

    @Query("SELECT * FROM anomalies WHERE id = :id")
    suspend fun fetchById(id: String): Anomaly?

    @Query("DELETE FROM anomalies")
    suspend fun deleteAll()

    @Query("DELETE FROM anomalies WHERE detectedAt >= :afterMillis")
    suspend fun deleteAfter(afterMillis: Long): Int
}
