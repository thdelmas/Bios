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

    @Query("""
        UPDATE anomalies SET
            feedbackAt = :feedbackAt,
            feltSick = :feltSick,
            visitedDoctor = :visitedDoctor,
            diagnosis = :diagnosis,
            symptoms = :symptoms,
            notes = :notes,
            outcomeAccurate = :outcomeAccurate
        WHERE id = :id
    """)
    suspend fun saveFeedback(
        id: String,
        feedbackAt: Long = System.currentTimeMillis(),
        feltSick: Boolean?,
        visitedDoctor: Boolean?,
        diagnosis: String?,
        symptoms: String?,
        notes: String?,
        outcomeAccurate: Boolean?
    )

    @Query("SELECT * FROM anomalies WHERE feedbackAt IS NOT NULL ORDER BY feedbackAt DESC LIMIT :limit")
    suspend fun fetchWithFeedback(limit: Int = 50): List<Anomaly>

    @Query("SELECT * FROM anomalies ORDER BY detectedAt DESC")
    suspend fun fetchAll(): List<Anomaly>

    @Query("DELETE FROM anomalies")
    suspend fun deleteAll()
}
