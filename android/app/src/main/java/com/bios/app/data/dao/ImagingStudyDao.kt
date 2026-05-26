package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bios.app.model.ImagingStudy

/**
 * Persistence layer for owner-recorded imaging studies (#356).
 *
 * Plain CRUD plus a fetch-most-recent query for the medical-history
 * surface. No pattern-engine joins — Bios doesn't interpret imaging.
 */
@Dao
interface ImagingStudyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(study: ImagingStudy)

    @Update
    suspend fun update(study: ImagingStudy)

    @Delete
    suspend fun delete(study: ImagingStudy)

    @Query("SELECT * FROM imaging_studies WHERE uuid = :uuid")
    suspend fun fetchByUuid(uuid: String): ImagingStudy?

    @Query("SELECT * FROM imaging_studies ORDER BY studyDate DESC")
    suspend fun fetchAll(): List<ImagingStudy>

    @Query("SELECT * FROM imaging_studies ORDER BY studyDate DESC LIMIT :limit")
    suspend fun fetchRecent(limit: Int = 50): List<ImagingStudy>

    @Query("SELECT COUNT(*) FROM imaging_studies")
    suspend fun count(): Int
}
