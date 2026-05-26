package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.ClinicalEncounter
import com.bios.app.model.FollowUpReferral

/**
 * Persistence layer for clinical encounters and their follow-up
 * referrals (#358). Plain CRUD; encounter ↔ referral cascade-delete is
 * handled in the repo layer rather than via Room ForeignKey to keep the
 * migration shape simple.
 */
@Dao
interface ClinicalEncounterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(encounter: ClinicalEncounter)

    @Update
    suspend fun update(encounter: ClinicalEncounter)

    @Delete
    suspend fun delete(encounter: ClinicalEncounter)

    @Query("DELETE FROM clinical_encounters WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT * FROM clinical_encounters WHERE uuid = :uuid")
    suspend fun fetchByUuid(uuid: String): ClinicalEncounter?

    @Query("SELECT * FROM clinical_encounters ORDER BY admissionAt DESC")
    suspend fun fetchAll(): List<ClinicalEncounter>

    @Query("SELECT * FROM clinical_encounters ORDER BY admissionAt DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 50): List<ClinicalEncounter>

    @Query("SELECT COUNT(*) FROM clinical_encounters")
    suspend fun count(): Int

    // -- Follow-up referrals --

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferral(referral: FollowUpReferral): Long

    @Update
    suspend fun updateReferral(referral: FollowUpReferral)

    @Delete
    suspend fun deleteReferral(referral: FollowUpReferral)

    @Query("DELETE FROM follow_up_referrals WHERE encounterUuid = :encounterUuid")
    suspend fun deleteReferralsForEncounter(encounterUuid: String)

    @Query("SELECT * FROM follow_up_referrals WHERE encounterUuid = :encounterUuid ORDER BY urgency DESC, createdAt ASC")
    suspend fun fetchReferralsForEncounter(encounterUuid: String): List<FollowUpReferral>
}
