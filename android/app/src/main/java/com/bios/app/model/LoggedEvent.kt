package com.bios.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// Discrete event-shaped data: substance use, falls, near-misses, acute
// symptom episodes, check-in misses. Parallel to `MetricReading` (which
// is timeseries-shaped). Owned by `docs/SELF_REPORTED_DATA_HOME.md`
// decision 2; that doc explains why these don't squash into
// `MetricReading.value = 1.0`.
//
// `eventType` is a String so it can host both `MetricType` keys
// (tobacco_use, fall_event, …) and the future `SymptomKind` taxonomy
// (decision 4) without a schema change. `note` is owner-private and
// never analyzed — it exists for owner recall, not for engines.
@Entity(
    tableName = "logged_events",
    foreignKeys = [
        ForeignKey(
            entity = DataSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("eventType", "timestamp"),
        Index("timestamp"),
        Index("sourceId")
    ]
)
data class LoggedEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: Long,
    val severity: Int? = null,
    val durationMs: Long? = null,
    val value: Double? = null,
    val sourceId: String,
    val packageName: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
