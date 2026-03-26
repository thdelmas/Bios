package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "health_events",
    indices = [
        Index("createdAt"),
        Index("type"),
        Index("status"),
        Index("anomalyId"),
        Index("parentEventId")
    ]
)
data class HealthEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,                                   // HealthEventType.name
    val status: String = HealthEventStatus.OPEN.name,
    val title: String,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val anomalyId: String? = null,                      // optional link to an Anomaly
    val parentEventId: String? = null                   // threading: symptom -> diagnosis -> treatment
)
