package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.PhoneSleepSample

/**
 * Sample buffer behind [com.bios.app.ingest.PhoneSleepWorker] (#134).
 *
 * The worker writes one row per periodic firing, reads back the
 * overnight window at the morning trigger, and prunes anything older
 * than 48 h so the buffer can't grow unboundedly. Keep operations
 * minimal — this is a transient buffer, not a long-lived archive.
 */
@Dao
interface PhoneSleepSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: PhoneSleepSample)

    @Query("""
        SELECT * FROM phone_sleep_samples
        WHERE timestamp >= :startMillis AND timestamp <= :endMillis
        ORDER BY timestamp ASC
    """)
    suspend fun fetchInRange(startMillis: Long, endMillis: Long): List<PhoneSleepSample>

    @Query("DELETE FROM phone_sleep_samples WHERE timestamp < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long): Int

    @Query("SELECT COUNT(*) FROM phone_sleep_samples")
    suspend fun count(): Int

    @Query("SELECT MAX(timestamp) FROM phone_sleep_samples")
    suspend fun lastTimestamp(): Long?
}
