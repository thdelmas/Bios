package com.bios.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bios.app.model.ActionItem

@Dao
interface ActionItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActionItem)

    @Query("SELECT * FROM action_items WHERE healthEventId = :eventId ORDER BY dueAt ASC, createdAt ASC")
    suspend fun fetchByEventId(eventId: String): List<ActionItem>

    @Query("SELECT * FROM action_items WHERE completed = 0 ORDER BY dueAt ASC, createdAt ASC")
    suspend fun fetchPending(): List<ActionItem>

    @Query("UPDATE action_items SET completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean, completedAt: Long? = System.currentTimeMillis())

    @Query("UPDATE action_items SET description = :description, dueAt = :dueAt WHERE id = :id")
    suspend fun update(id: String, description: String, dueAt: Long?)

    @Query("DELETE FROM action_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM action_items WHERE healthEventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("DELETE FROM action_items")
    suspend fun deleteAll()
}
