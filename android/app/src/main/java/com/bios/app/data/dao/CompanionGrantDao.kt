package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.CompanionGrant
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionGrantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: CompanionGrant)

    @Query("SELECT * FROM companion_grants WHERE packageName = :pkg LIMIT 1")
    suspend fun find(pkg: String): CompanionGrant?

    @Query("SELECT * FROM companion_grants ORDER BY firstSeenAt DESC")
    fun observeAll(): Flow<List<CompanionGrant>>

    @Query("SELECT * FROM companion_grants ORDER BY firstSeenAt DESC")
    suspend fun getAll(): List<CompanionGrant>

    @Query("SELECT * FROM companion_grants WHERE state = :state ORDER BY firstSeenAt DESC")
    suspend fun getByState(state: String): List<CompanionGrant>

    @Query("SELECT COUNT(*) FROM companion_grants WHERE state = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    @Query("UPDATE companion_grants SET lastAccessAt = :ts, accessCount = accessCount + 1 WHERE packageName = :pkg")
    suspend fun recordAccess(pkg: String, ts: Long)

    @Query("DELETE FROM companion_grants WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM companion_grants")
    suspend fun deleteAll()
}
