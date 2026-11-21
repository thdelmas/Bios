package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.MedicationAnnotation

/**
 * Persistence layer for the owner's own medication list.
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

    @Query("SELECT COUNT(*) FROM medication_annotations")
    suspend fun count(): Int
}
