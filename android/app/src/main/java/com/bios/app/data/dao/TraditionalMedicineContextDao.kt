package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.MedicalTradition
import com.bios.app.model.TraditionalMedicineContext

/**
 * Persistence layer for owner-declared traditional-medicine context (#193).
 *
 * Multi-row table — the owner may have a TCM row, an Ayurveda row, and a
 * curandera row all at once. Each is keyed by an independent string id.
 *
 * All queries are pull-side reads; nothing here drives a push-side
 * surface by itself. Vocabulary overlay (the only downstream surface
 * this DAO feeds) is gated row-by-row by
 * [TraditionalMedicineContext.vocabularyOverlayConsent] and renders at
 * most one footnote line in alert-detail views.
 */
@Dao
interface TraditionalMedicineContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TraditionalMedicineContext)

    @Update
    suspend fun update(entry: TraditionalMedicineContext)

    @Delete
    suspend fun delete(entry: TraditionalMedicineContext)

    @Query("SELECT * FROM traditional_medicine_context WHERE id = :id")
    suspend fun fetchById(id: String): TraditionalMedicineContext?

    /**
     * All recorded tradition contexts, newest first.
     */
    @Query("""
        SELECT * FROM traditional_medicine_context
        ORDER BY createdAt DESC
    """)
    suspend fun fetchAll(): List<TraditionalMedicineContext>

    /**
     * Rows for which the owner has explicitly opted into vocabulary
     * overlay. Used by alert-detail views to decide whether to render
     * any tradition-vocabulary footnote at all.
     */
    @Query("""
        SELECT * FROM traditional_medicine_context
        WHERE vocabularyOverlayConsent = 1
        ORDER BY createdAt DESC
    """)
    suspend fun fetchOverlayOptedIn(): List<TraditionalMedicineContext>

    /** Rows tagged with a specific tradition. */
    @Query("""
        SELECT * FROM traditional_medicine_context
        WHERE tradition = :tradition
        ORDER BY createdAt DESC
    """)
    suspend fun fetchByTradition(tradition: MedicalTradition): List<TraditionalMedicineContext>

    @Query("SELECT COUNT(*) FROM traditional_medicine_context")
    suspend fun count(): Int

    @Query("DELETE FROM traditional_medicine_context WHERE id = :id")
    suspend fun deleteById(id: String)
}
