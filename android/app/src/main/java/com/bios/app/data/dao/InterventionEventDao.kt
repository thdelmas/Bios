package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.InterventionEvent

/**
 * Persistence layer for owner-recorded interventions (#192).
 *
 * The DAO exposes the standard CRUD set plus two filtered queries that
 * the Settings UI relies on:
 *
 *  - [fetchByCourse] — sessions belonging to a given course, newest
 *    first. Drives the course-detail screen.
 *  - [fetchSince] — all events from a given epoch-ms watermark. The
 *    alert-explanation enhancer is the eventual consumer; today no
 *    surface auto-reads it.
 *
 * Recording is not endorsement (see [InterventionEvent]). Nothing in
 * this DAO judges efficacy; queries are pure data retrieval.
 */
@Dao
interface InterventionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: InterventionEvent): Long

    @Update
    suspend fun update(event: InterventionEvent)

    @Delete
    suspend fun delete(event: InterventionEvent)

    @Query("SELECT * FROM intervention_events WHERE id = :id")
    suspend fun fetchById(id: String): InterventionEvent?

    @Query("SELECT * FROM intervention_events ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<InterventionEvent>

    @Query("""
        SELECT * FROM intervention_events
        WHERE treatmentCourseId = :courseId
        ORDER BY timestamp DESC
    """)
    suspend fun fetchByCourse(courseId: String): List<InterventionEvent>

    @Query("""
        SELECT * FROM intervention_events
        WHERE timestamp >= :since
        ORDER BY timestamp DESC
    """)
    suspend fun fetchSince(since: Long): List<InterventionEvent>

    @Query("SELECT COUNT(*) FROM intervention_events")
    suspend fun count(): Int
}
