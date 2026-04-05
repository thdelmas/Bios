package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.UserFeedback

@Dao
interface UserFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: UserFeedback)

    @Query("SELECT * FROM user_feedback WHERE surface = :surface ORDER BY createdAt DESC LIMIT :limit")
    suspend fun fetchBySurface(surface: String, limit: Int = 50): List<UserFeedback>

    @Query("SELECT COUNT(*) FROM user_feedback WHERE surface = :surface")
    suspend fun countBySurface(surface: String): Int

    @Query("SELECT COUNT(*) FROM user_feedback")
    suspend fun countAll(): Int

    @Query("""
        SELECT AVG(CAST(rating AS FLOAT)) FROM user_feedback
        WHERE surface = :surface AND surfaceItemId = :itemId
    """)
    suspend fun avgRatingForItem(surface: String, itemId: String): Double?

    @Query("SELECT * FROM user_feedback ORDER BY createdAt DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 100): List<UserFeedback>

    @Query("DELETE FROM user_feedback")
    suspend fun deleteAll()
}
