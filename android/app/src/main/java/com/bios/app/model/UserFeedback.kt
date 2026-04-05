package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Lightweight feedback record for any surface in the app.
 *
 * Captures owner responses to "Was this useful?" prompts across
 * alerts, biomarker references, privacy dashboard, and monthly surveys.
 *
 * Stored locally. Only aggregated anonymously if owner opts into
 * Community tier + research pipeline.
 */
@Entity(
    tableName = "user_feedback",
    indices = [
        Index("surface"),
        Index("createdAt")
    ]
)
data class UserFeedback(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val surface: String,             // Where: "alert", "biomarker_ref", "privacy_dashboard", "survey"
    val surfaceItemId: String? = null, // Which item (patternId, metricType, etc.)
    val rating: Int,                 // -1 = not useful, 0 = neutral, 1 = useful
    val comment: String? = null,     // Optional free-text (stored locally only)
    val createdAt: Long = System.currentTimeMillis()
)
