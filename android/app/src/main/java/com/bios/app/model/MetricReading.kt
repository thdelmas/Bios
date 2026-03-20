package com.bios.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "metric_readings",
    foreignKeys = [
        ForeignKey(
            entity = DataSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("metricType", "timestamp"),
        Index("timestamp"),
        Index("sourceId")
    ]
)
data class MetricReading(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val metricType: String,
    val value: Double,
    val timestamp: Long,        // epoch millis
    val durationSec: Int? = null,
    val sourceId: String,
    val confidence: Int,        // ConfidenceTier.level
    val isPrimary: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
