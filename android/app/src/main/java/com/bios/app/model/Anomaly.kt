package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "anomalies",
    indices = [
        Index("detectedAt"),
        Index("severity")
    ]
)
data class Anomaly(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val detectedAt: Long = System.currentTimeMillis(),
    val metricTypes: String,        // JSON array of MetricType keys
    val deviationScores: String,    // JSON object: metricType key -> z-score
    val combinedScore: Double,
    val patternId: String? = null,
    val severity: Int,              // AlertTier.level
    val title: String,
    val explanation: String,
    val suggestedAction: String? = null,
    val acknowledged: Boolean = false,
    val acknowledgedAt: Long? = null,

    // User feedback / health journal
    val feedbackAt: Long? = null,
    val feltSick: Boolean? = null,
    val visitedDoctor: Boolean? = null,
    val diagnosis: String? = null,
    val symptoms: String? = null,       // free-text symptom self-report
    val notes: String? = null,          // general journal notes
    val outcomeAccurate: Boolean? = null // was the alert correct?
)
