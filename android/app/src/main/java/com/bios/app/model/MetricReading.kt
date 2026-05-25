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
        Index("sourceId"),
        // Logical identity of a reading. The primary key is a UUID
        // generated per insert, so without this REPLACE never fires for
        // the same physical reading and every adapter re-sync produces
        // a parallel set of rows. With it, INSERT OR REPLACE collapses
        // re-imports into in-place updates. sourceId is part of the key
        // so cross-source rows at the same ms keep separate rows; the
        // Deduplicator picks isPrimary between them at ingest.
        Index(value = ["sourceId", "metricType", "timestamp"], unique = true),
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
    val createdAt: Long = System.currentTimeMillis(),
    // Owner-recall free text, never read by any engine. Reserved for surfaces
    // where the owner annotates their own reading (biomarker entry, manual
    // sleep duration). Engines must not query this column; the manifesto's
    // "instrument, not coach" stance scopes evaluation to the owner.
    val note: String? = null
)
