package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.MigraineAttack

/**
 * Persistence layer for owner-logged migraine-attack diary entries
 * (issue #207, audit gap §2.6). The diary screen consumes [fetchAll] /
 * [fetchInRange]; the [com.bios.app.alerts.MedicationOveruseHeadacheEvaluator]
 * consumes [fetchInRange] over a 90-day window to count acute-treatment
 * days for the IHS ICHD-3 §8.2 MOH screen.
 */
@Dao
interface MigraineAttackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attack: MigraineAttack): Long

    @Update
    suspend fun update(attack: MigraineAttack)

    @Delete
    suspend fun delete(attack: MigraineAttack)

    @Query("DELETE FROM migraine_attacks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM migraine_attacks WHERE id = :id")
    suspend fun fetchById(id: String): MigraineAttack?

    @Query("SELECT * FROM migraine_attacks ORDER BY onsetTimestamp DESC")
    suspend fun fetchAll(): List<MigraineAttack>

    /**
     * Attacks whose [MigraineAttack.onsetTimestamp] falls in `[fromMillis, toMillis]`,
     * newest first. Inclusive on both ends.
     */
    @Query("""
        SELECT * FROM migraine_attacks
        WHERE onsetTimestamp BETWEEN :fromMillis AND :toMillis
        ORDER BY onsetTimestamp DESC
    """)
    suspend fun fetchInRange(fromMillis: Long, toMillis: Long): List<MigraineAttack>

    @Query("SELECT COUNT(*) FROM migraine_attacks")
    suspend fun count(): Int
}
