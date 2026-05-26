package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.GenderAffirmingCareEntry

/**
 * Persistence layer for owner-recorded gender-affirming care episodes
 * (#209). Lives in the separately-encrypted reproductive DB. The DB's
 * legacy name reflects its history (cycle data); the encryption envelope
 * is the right one for any sensitive reproductive / hormone data.
 */
@Dao
interface GenderAffirmingCareEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: GenderAffirmingCareEntry)

    @Delete
    suspend fun delete(entry: GenderAffirmingCareEntry)

    @Query("SELECT * FROM gender_affirming_care_entries ORDER BY startDate DESC")
    suspend fun fetchAll(): List<GenderAffirmingCareEntry>

    @Query("SELECT * FROM gender_affirming_care_entries ORDER BY startDate DESC LIMIT 1")
    suspend fun fetchLatest(): GenderAffirmingCareEntry?

    @Query("SELECT COUNT(*) FROM gender_affirming_care_entries")
    suspend fun count(): Int

    @Query("DELETE FROM gender_affirming_care_entries")
    suspend fun clear()
}
