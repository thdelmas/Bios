package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.SourceMetricToggle
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceMetricToggleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(toggle: SourceMetricToggle)

    @Query("SELECT * FROM source_metric_toggles WHERE enabled = 0")
    suspend fun disabledPairs(): List<SourceMetricToggle>

    @Query("SELECT * FROM source_metric_toggles")
    fun allFlow(): Flow<List<SourceMetricToggle>>
}
