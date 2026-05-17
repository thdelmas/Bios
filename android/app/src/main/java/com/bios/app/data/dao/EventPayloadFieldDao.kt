package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.EventPayloadField

@Dao
interface EventPayloadFieldDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<EventPayloadField>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(field: EventPayloadField)

    @Query("SELECT * FROM event_payloads WHERE readingId = :readingId")
    suspend fun fetchForReading(readingId: String): List<EventPayloadField>

    @Query("SELECT * FROM event_payloads WHERE readingId IN (:readingIds)")
    suspend fun fetchForReadings(readingIds: List<String>): List<EventPayloadField>

    @Query("DELETE FROM event_payloads WHERE readingId = :readingId")
    suspend fun deleteForReading(readingId: String)

    @Query("DELETE FROM event_payloads")
    suspend fun deleteAll()
}
