package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.MedicationAnnotation

/**
 * Persistence layer for owner-recorded current medications (audit gap §2.5).
 *
 * Two queries the alert-explanation surface depends on:
 *
 *  - [fetchActive] — `endDate IS NULL` — what the owner is *currently*
 *    taking. The alert-context enhancer uses this to format the
 *    "Annotated current medications: …" line.
 *  - [fetchActiveOn] — what the owner was on at a specific point in time
 *    (`startDate ≤ at AND (endDate IS NULL OR endDate ≥ at)`). Used when
 *    the explanation surface wants medication context at the moment an
 *    anomaly fired, not at the moment the owner views it. v1 ships
 *    notification-time enrichment (fetchActive); historical-window
 *    enrichment lands when AlertsScreen wires the same helper.
 */
@Dao
interface MedicationAnnotationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: MedicationAnnotation): Long

    @Update
    suspend fun update(annotation: MedicationAnnotation)

    @Delete
    suspend fun delete(annotation: MedicationAnnotation)

    @Query("SELECT * FROM medication_annotations WHERE id = :id")
    suspend fun fetchById(id: Long): MedicationAnnotation?

    /**
     * All medications, active first (endDate IS NULL), then discontinued
     * by most recent endDate. Within each group, most recent startDate
     * wins so newly-added meds rise to the top of the list.
     */
    @Query("""
        SELECT * FROM medication_annotations
        ORDER BY (endDate IS NOT NULL), endDate DESC, startDate DESC
    """)
    suspend fun fetchAll(): List<MedicationAnnotation>

    /** Currently-taken medications (endDate IS NULL), newest startDate first. */
    @Query("""
        SELECT * FROM medication_annotations
        WHERE endDate IS NULL
        ORDER BY startDate DESC
    """)
    suspend fun fetchActive(): List<MedicationAnnotation>

    /**
     * Medications the owner was on at a given epoch-ms timestamp.
     * Inclusive on both edges so a med started exactly at `at` counts.
     */
    @Query("""
        SELECT * FROM medication_annotations
        WHERE startDate <= :at AND (endDate IS NULL OR endDate >= :at)
        ORDER BY startDate DESC
    """)
    suspend fun fetchActiveOn(at: Long): List<MedicationAnnotation>

    @Query("SELECT COUNT(*) FROM medication_annotations")
    suspend fun count(): Int
}
