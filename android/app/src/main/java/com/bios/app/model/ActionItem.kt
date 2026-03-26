package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "action_items",
    indices = [
        Index("healthEventId"),
        Index("completed"),
        Index("dueAt")
    ]
)
data class ActionItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val healthEventId: String,
    val description: String,
    val dueAt: Long? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
