package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.ImmunizationRecord

/**
 * Persistence layer for owner-recorded immunisations (audit gap §2.3).
 *
 * Queries the screening-cadence engine (#155) will consume:
 *
 *  - [fetchAll] — full immunisation history, newest first
 *  - [fetchLatestByCvxCode] — "when did the owner last get vaccine X" —
 *    the cadence engine uses this to compute "next dose due in N months"
 */
@Dao
interface ImmunizationRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ImmunizationRecord): Long

    @Update
    suspend fun update(record: ImmunizationRecord)

    @Delete
    suspend fun delete(record: ImmunizationRecord)

    @Query("SELECT * FROM immunization_records WHERE id = :id")
    suspend fun fetchById(id: Long): ImmunizationRecord?

    @Query("SELECT * FROM immunization_records ORDER BY occurrenceDate DESC")
    suspend fun fetchAll(): List<ImmunizationRecord>

    /** Most recent dose of a given CVX-coded vaccine, or null if none. */
    @Query("""
        SELECT * FROM immunization_records
        WHERE cvxCode = :cvxCode
        ORDER BY occurrenceDate DESC
        LIMIT 1
    """)
    suspend fun fetchLatestByCvxCode(cvxCode: String): ImmunizationRecord?

    @Query("SELECT COUNT(*) FROM immunization_records")
    suspend fun count(): Int
}
