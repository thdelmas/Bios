package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.EcgStrip

/**
 * Persistence layer for owner-captured single-lead ECG strips (#188,
 * audit gap §2.8). See [EcgStrip] for the storage model.
 *
 * Standard insert / list / delete shape. No update — strips are
 * immutable artefacts (the waveform itself can't change; the
 * classification was the vendor's call at capture time). Edits to the
 * owner-annotation [EcgStrip.note] flow through a delete + re-insert,
 * preserving the row's identity via the explicit [EcgStrip.id].
 */
@Dao
interface EcgStripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(strip: EcgStrip): Long

    @Delete
    suspend fun delete(strip: EcgStrip)

    @Query("DELETE FROM ecg_strips WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM ecg_strips WHERE id = :id")
    suspend fun fetchById(id: String): EcgStrip?

    @Query("SELECT * FROM ecg_strips ORDER BY timestamp DESC")
    suspend fun fetchAll(): List<EcgStrip>

    /**
     * Lookup strips overlapping a time window. Used by the pattern
     * engine to ask "is there an ECG strip the owner captured near
     * this PPG-derived AFib screen?" — the screen's confirmation
     * surface joins the rhythm-classifier output against this table.
     *
     * Overlap semantics: a strip starting at `timestamp` and lasting
     * `durationSeconds` overlaps `[startMs, endMs]` when
     * `timestamp <= endMs` and `timestamp + durationSeconds*1000 >= startMs`.
     */
    @Query("""
        SELECT * FROM ecg_strips
        WHERE timestamp <= :endMs
          AND (timestamp + durationSeconds * 1000) >= :startMs
        ORDER BY timestamp DESC
    """)
    suspend fun fetchInWindow(startMs: Long, endMs: Long): List<EcgStrip>

    @Query("SELECT COUNT(*) FROM ecg_strips")
    suspend fun count(): Int
}
