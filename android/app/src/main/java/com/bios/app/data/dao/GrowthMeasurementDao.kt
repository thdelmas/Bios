package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.GrowthMeasurement

/**
 * Persistence for body measurements (#199). A measurement is a discrete
 * event, not a stream; no baseline engine runs over this table.
 */
@Dao
interface GrowthMeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: GrowthMeasurement): Long

    @Update
    suspend fun update(measurement: GrowthMeasurement)

    @Delete
    suspend fun delete(measurement: GrowthMeasurement)

    @Query("SELECT * FROM growth_measurements WHERE id = :id")
    suspend fun fetchById(id: String): GrowthMeasurement?

    /** All measurements, newest first. Used by the body measurements screen. */
    @Query("SELECT * FROM growth_measurements ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<GrowthMeasurement>

    @Query("SELECT COUNT(*) FROM growth_measurements")
    suspend fun count(): Int
}
