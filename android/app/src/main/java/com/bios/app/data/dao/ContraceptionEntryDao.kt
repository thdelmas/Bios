package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ContraceptionEntry

/**
 * Persistence layer for owner-recorded contraception history (#209).
 *
 * Lives in the separately-encrypted reproductive DB. Latest row by
 * [ContraceptionEntry.startDate] is the current method; older rows are
 * preserved so the timeline reads correctly for past cycles.
 */
@Dao
interface ContraceptionEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ContraceptionEntry)

    @Delete
    suspend fun delete(entry: ContraceptionEntry)

    @Query("SELECT * FROM contraception_entries ORDER BY startDate DESC")
    suspend fun fetchAll(): List<ContraceptionEntry>

    @Query("SELECT * FROM contraception_entries ORDER BY startDate DESC LIMIT 1")
    suspend fun fetchLatest(): ContraceptionEntry?

    @Query("SELECT COUNT(*) FROM contraception_entries")
    suspend fun count(): Int

    @Query("DELETE FROM contraception_entries")
    suspend fun clear()
}
