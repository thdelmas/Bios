package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.RiskProfile

/**
 * Persistence layer for the owner's risk profile (#160). Single-row
 * table; the upsert via [save] always targets `id = SINGLETON_ID`.
 */
@Dao
interface RiskProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: RiskProfile)

    @Query("SELECT * FROM risk_profile WHERE id = :id")
    suspend fun fetch(id: Long = RiskProfile.SINGLETON_ID): RiskProfile?

    @Query("DELETE FROM risk_profile WHERE id = :id")
    suspend fun clear(id: Long = RiskProfile.SINGLETON_ID)
}
