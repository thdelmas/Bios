package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ProfessionalReview

@Dao
interface ProfessionalReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ProfessionalReview)

    @Query("SELECT * FROM professional_reviews WHERE id = :id")
    suspend fun fetchById(id: String): ProfessionalReview?

    @Query("SELECT * FROM professional_reviews WHERE anomalyId = :anomalyId ORDER BY requestedAt DESC")
    suspend fun fetchByAnomalyId(anomalyId: String): List<ProfessionalReview>

    @Query("SELECT * FROM professional_reviews ORDER BY requestedAt DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 20): List<ProfessionalReview>

    @Query("SELECT * FROM professional_reviews WHERE status IN (0, 1) ORDER BY requestedAt DESC")
    suspend fun fetchPending(): List<ProfessionalReview>

    @Query("UPDATE professional_reviews SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int)

    @Query("""
        UPDATE professional_reviews SET
            status = 1,
            shareMethod = :shareMethod,
            sharedMetrics = :sharedMetrics,
            sharedWindowDays = :sharedWindowDays,
            sharedExplanation = :sharedExplanation,
            sharedBaselines = :sharedBaselines
        WHERE id = :id
    """)
    suspend fun markShared(
        id: String,
        shareMethod: String,
        sharedMetrics: String?,
        sharedWindowDays: Int?,
        sharedExplanation: Boolean,
        sharedBaselines: Boolean
    )

    @Query("""
        UPDATE professional_reviews SET
            status = 2,
            respondedAt = :respondedAt,
            professionalNotes = :notes,
            clinicallyRelevant = :clinicallyRelevant,
            recommendation = :recommendation,
            ownerFoundHelpful = :ownerFoundHelpful
        WHERE id = :id
    """)
    suspend fun recordResponse(
        id: String,
        respondedAt: Long = System.currentTimeMillis(),
        notes: String?,
        clinicallyRelevant: Boolean?,
        recommendation: String?,
        ownerFoundHelpful: Boolean?
    )

    @Query("DELETE FROM professional_reviews")
    suspend fun deleteAll()
}
