package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.DataSource

@Dao
interface DataSourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: DataSource)

    @Query("SELECT * FROM data_sources WHERE sourceType = :sourceType LIMIT 1")
    suspend fun findByType(sourceType: String): DataSource?

    @Query(
        "SELECT * FROM data_sources " +
            "WHERE sourceType = :sourceType AND deviceName = :deviceName LIMIT 1"
    )
    suspend fun findByTypeAndDeviceName(sourceType: String, deviceName: String): DataSource?

    @Query("SELECT * FROM data_sources")
    suspend fun getAll(): List<DataSource>

    @Query("DELETE FROM data_sources")
    suspend fun deleteAll()
}
