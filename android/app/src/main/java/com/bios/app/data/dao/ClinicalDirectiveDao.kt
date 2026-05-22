package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ClinicalDirective

/**
 * Persistence layer for the owner's clinical-directive metadata
 * (#184). Single-row table; the upsert via [save] always targets
 * `id = SINGLETON_ID`.
 */
@Dao
interface ClinicalDirectiveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(directive: ClinicalDirective)

    @Query("SELECT * FROM clinical_directive WHERE id = :id")
    suspend fun fetch(id: Long = ClinicalDirective.SINGLETON_ID): ClinicalDirective?

    @Query("DELETE FROM clinical_directive WHERE id = :id")
    suspend fun clear(id: Long = ClinicalDirective.SINGLETON_ID)
}
