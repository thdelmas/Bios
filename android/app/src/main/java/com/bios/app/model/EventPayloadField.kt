package com.bios.app.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Sidecar table for composite event payloads. Each row is one field of a
// structured event attached to a parent MetricReading row.
//
// Scalar metrics (heart_rate, hba1c, …) never touch this table — they keep
// their full meaning in MetricReading.value. Composite metrics (e.g.
// EXERCISE_SESSION with {modality, start, end, avg_hr, rpe}) land one
// MetricReading row plus N EventPayloadField rows.
//
// Field keys are part of the contract. They are namespaced per MetricType
// and documented in docs/DATA_MODEL.md alongside the metric they describe.
// Treat them with the same stability commitment as MetricType.key strings —
// renames are breaking changes for every reader.
//
// Each row carries exactly one typed value; the other two columns are null.
// Three typed columns (vs. one stringly-typed) keeps SQL queries useful:
// "find sessions where avg_hr_bpm > 140" stays a numeric comparison rather
// than a string-cast.
@Entity(
    tableName = "event_payloads",
    primaryKeys = ["readingId", "fieldKey"],
    foreignKeys = [
        ForeignKey(
            entity = MetricReading::class,
            parentColumns = ["id"],
            childColumns = ["readingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("readingId")
    ]
)
data class EventPayloadField(
    val readingId: String,
    val fieldKey: String,
    val stringValue: String? = null,
    val doubleValue: Double? = null,
    val longValue: Long? = null,
)
