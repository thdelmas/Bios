package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.GrowthMeasurement

/**
 * Persistence for paediatric growth tracking + adult body-composition
 * trajectory (#199, audit gap §2.7).
 *
 * Two query shapes the surfaces above this DAO depend on:
 *
 *  - [fetchAll] — chronological list for the anthropometry screen and the
 *    growth-chart percentile plot.
 *  - [fetchInWindow] — failure-to-thrive surveillance, sarcopenia / cachexia
 *    screens. The patterns read consecutive measurements over a 3-12 month
 *    window to detect downward percentile-band crossings (paediatric) or
 *    lean-mass / weight decline (adult).
 *
 * A growth measurement is a discrete event, not a stream — most owners log
 * a handful per year for adults, monthly for infants. No baseline engine
 * runs over this table; the trajectory patterns ([failure_to_thrive_screen],
 * [sarcopenia_trajectory_screen], [cachexia_screen]) consume the raw
 * measurements directly.
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

    /** All measurements, newest first. Used by the anthropometry screen and trajectory plot. */
    @Query("SELECT * FROM growth_measurements ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<GrowthMeasurement>

    /** Chronological (oldest first) — what the growth-chart percentile engine prefers. */
    @Query("SELECT * FROM growth_measurements ORDER BY timestamp ASC")
    suspend fun fetchAllChronological(): List<GrowthMeasurement>

    /**
     * Measurements within `[from, to]` epoch-ms window, chronological.
     * Used by the failure-to-thrive / sarcopenia / cachexia screens.
     */
    @Query("""
        SELECT * FROM growth_measurements
        WHERE timestamp BETWEEN :fromEpochMs AND :toEpochMs
        ORDER BY timestamp ASC
    """)
    suspend fun fetchInWindow(fromEpochMs: Long, toEpochMs: Long): List<GrowthMeasurement>

    @Query("SELECT COUNT(*) FROM growth_measurements")
    suspend fun count(): Int
}
