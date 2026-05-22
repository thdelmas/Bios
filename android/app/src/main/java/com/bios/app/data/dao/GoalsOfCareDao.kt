package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.GoalsOfCare

/**
 * Persistence layer for the owner's goals of care (#184). Single-row
 * table; the upsert via [save] always targets `id = SINGLETON_ID`.
 *
 * The [com.bios.app.alerts.AlertManager] reads this surface (via
 * [com.bios.app.data.GoalsOfCareRepo]) to decide whether URGENT-tier
 * alerts should escalate. When the owner's intervention level is
 * COMFORT_ONLY, URGENT escalation is silenced in the same way
 * hospice mode silences it.
 */
@Dao
interface GoalsOfCareDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(goals: GoalsOfCare)

    @Query("SELECT * FROM goals_of_care WHERE id = :id")
    suspend fun fetch(id: Long = GoalsOfCare.SINGLETON_ID): GoalsOfCare?

    @Query("DELETE FROM goals_of_care WHERE id = :id")
    suspend fun clear(id: Long = GoalsOfCare.SINGLETON_ID)
}
