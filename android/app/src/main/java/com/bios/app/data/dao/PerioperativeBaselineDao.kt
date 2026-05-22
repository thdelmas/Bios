package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.physiology.PerioperativeBaseline

/**
 * DAO for the frozen pre-op [PerioperativeBaseline] snapshot (#183,
 * SURGICAL_POV.md §2.1). Single-row table by convention — see
 * [PerioperativeBaseline.SINGLETON_ID].
 */
@Dao
interface PerioperativeBaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baseline: PerioperativeBaseline)

    @Query("SELECT * FROM perioperative_baselines WHERE id = :id LIMIT 1")
    suspend fun fetch(id: Int = PerioperativeBaseline.SINGLETON_ID): PerioperativeBaseline?

    @Query("DELETE FROM perioperative_baselines")
    suspend fun deleteAll()
}
