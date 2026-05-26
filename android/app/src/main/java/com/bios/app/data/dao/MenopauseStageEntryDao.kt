package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.MenopauseStageEntry

/**
 * Persistence layer for owner-recorded menopause-stage history (#209).
 *
 * Lives in the separately-encrypted reproductive DB. STRAW+10 staging;
 * latest row by [MenopauseStageEntry.recordedDate] is the current stage.
 * History-preserving so earlier readings can be interpreted under the
 * stage the owner was in at the time.
 */
@Dao
interface MenopauseStageEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MenopauseStageEntry)

    @Delete
    suspend fun delete(entry: MenopauseStageEntry)

    @Query("SELECT * FROM menopause_stage_entries ORDER BY recordedDate DESC")
    suspend fun fetchAll(): List<MenopauseStageEntry>

    @Query("SELECT * FROM menopause_stage_entries ORDER BY recordedDate DESC LIMIT 1")
    suspend fun fetchLatest(): MenopauseStageEntry?

    @Query("SELECT COUNT(*) FROM menopause_stage_entries")
    suspend fun count(): Int

    @Query("DELETE FROM menopause_stage_entries")
    suspend fun clear()
}
