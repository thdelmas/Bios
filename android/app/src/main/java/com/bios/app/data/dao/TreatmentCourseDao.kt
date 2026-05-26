package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.TreatmentCourse

/**
 * Persistence layer for owner-recorded treatment courses (#192).
 *
 * Note the `(actualEndAt IS NULL)` ordering quirk in [fetchAll]:
 * SQLite sorts NULL before non-NULL by default, so prefacing the
 * ORDER BY with the IS NOT NULL expression flips that — open
 * courses (actualEndAt IS NULL) rise to the top of the list and
 * closed courses sort below by most recent close.
 */
@Dao
interface TreatmentCourseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: TreatmentCourse): Long

    @Update
    suspend fun update(course: TreatmentCourse)

    @Delete
    suspend fun delete(course: TreatmentCourse)

    @Query("SELECT * FROM treatment_courses WHERE id = :id")
    suspend fun fetchById(id: String): TreatmentCourse?

    /**
     * All courses, open ones first (actualEndAt IS NULL) then most-
     * recently-closed. Within each group, newer startedAt wins.
     */
    @Query("""
        SELECT * FROM treatment_courses
        ORDER BY (actualEndAt IS NOT NULL), actualEndAt DESC, startedAt DESC
    """)
    suspend fun fetchAll(): List<TreatmentCourse>

    /** Currently open courses (actualEndAt IS NULL), newest startedAt first. */
    @Query("""
        SELECT * FROM treatment_courses
        WHERE actualEndAt IS NULL
        ORDER BY startedAt DESC
    """)
    suspend fun fetchOpen(): List<TreatmentCourse>

    @Query("SELECT COUNT(*) FROM treatment_courses")
    suspend fun count(): Int
}
