package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.PersonalBaseline

@Dao
interface PersonalBaselineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(baseline: PersonalBaseline)

    @Query("SELECT * FROM personal_baselines WHERE metricType = :metricType AND context = :context LIMIT 1")
    suspend fun fetch(metricType: String, context: String = "ALL"): PersonalBaseline?

    @Query("SELECT * FROM personal_baselines WHERE computedAt > :sinceMillis ORDER BY computedAt ASC")
    suspend fun fetchComputedAfter(sinceMillis: Long): List<PersonalBaseline>

    @Query("SELECT * FROM personal_baselines ORDER BY metricType")
    suspend fun fetchAll(): List<PersonalBaseline>

    @Query("DELETE FROM personal_baselines")
    suspend fun deleteAll()
}
