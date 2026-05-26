package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.AllergyRecord

/**
 * Persistence layer for owner-recorded allergies and intolerances (#355).
 *
 * Hard-delete is allowed — see entity doc. Mistakenly entered rows
 * disappear cleanly; there is no soft-delete history for allergies.
 */
@Dao
interface AllergyRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AllergyRecord): Long

    @Update
    suspend fun update(record: AllergyRecord)

    @Delete
    suspend fun delete(record: AllergyRecord)

    @Query("SELECT * FROM allergy_records WHERE id = :id")
    suspend fun fetchById(id: Long): AllergyRecord?

    @Query("SELECT * FROM allergy_records ORDER BY substance ASC")
    suspend fun fetchAll(): List<AllergyRecord>

    @Query("SELECT COUNT(*) FROM allergy_records")
    suspend fun count(): Int
}
