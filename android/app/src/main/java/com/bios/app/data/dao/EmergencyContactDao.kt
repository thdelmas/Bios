package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.EmergencyContact

/**
 * Persistence layer for owner-designated emergency contacts (#179, EM/CC
 * audit gap §2.1).
 *
 * Queries the URGENT-escalation pipeline consumes:
 *
 *  - [fetchAll] — Settings screen listing
 *  - [fetchEligibleForTier] — when an alert fires, return the contacts
 *    whose [EmergencyContact.minTierLevel] is at or below the alert's
 *    tier *and* whose [EmergencyContact.acknowledgementTimeoutMinutes]
 *    is non-null. These are the rows the timeout worker may surface.
 */
@Dao
interface EmergencyContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)

    @Query("SELECT * FROM emergency_contacts WHERE id = :id")
    suspend fun fetchById(id: Long): EmergencyContact?

    @Query("SELECT * FROM emergency_contacts ORDER BY createdAt ASC")
    suspend fun fetchAll(): List<EmergencyContact>

    /**
     * Contacts eligible to be surfaced for an alert at [tierLevel] —
     * i.e. their [EmergencyContact.minTierLevel] is at or below the
     * alert's tier *and* they have a non-null ack timeout.
     */
    @Query("""
        SELECT * FROM emergency_contacts
        WHERE minTierLevel <= :tierLevel
          AND acknowledgementTimeoutMinutes IS NOT NULL
        ORDER BY createdAt ASC
    """)
    suspend fun fetchEligibleForTier(tierLevel: Int): List<EmergencyContact>

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun count(): Int
}
