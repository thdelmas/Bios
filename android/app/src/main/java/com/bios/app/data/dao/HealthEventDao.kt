package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.HealthEvent

@Dao
interface HealthEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: HealthEvent)

    @Query("SELECT * FROM health_events WHERE id = :id")
    suspend fun fetchById(id: String): HealthEvent?

    @Query("SELECT * FROM health_events ORDER BY createdAt DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 50): List<HealthEvent>

    @Query("SELECT * FROM health_events WHERE anomalyId = :anomalyId ORDER BY createdAt DESC")
    suspend fun fetchByAnomalyId(anomalyId: String): List<HealthEvent>

    @Query("SELECT * FROM health_events WHERE parentEventId = :parentId ORDER BY createdAt ASC")
    suspend fun fetchByParentId(parentId: String): List<HealthEvent>

    @Query("SELECT * FROM health_events WHERE status = :status ORDER BY createdAt DESC")
    suspend fun fetchByStatus(status: String): List<HealthEvent>

    @Query("SELECT * FROM health_events WHERE createdAt > :sinceMillis ORDER BY createdAt ASC")
    suspend fun fetchCreatedAfter(sinceMillis: Long): List<HealthEvent>

    @Query("SELECT * FROM health_events ORDER BY createdAt DESC")
    suspend fun fetchAll(): List<HealthEvent>

    @Query("UPDATE health_events SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE health_events SET title = :title, description = :description, updatedAt = :now WHERE id = :id")
    suspend fun update(id: String, title: String, description: String?, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM health_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM health_events")
    suspend fun deleteAll()
}
