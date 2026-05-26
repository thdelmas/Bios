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
    val parentEventId: String? = null,                  // threading: symptom -> diagnosis -> treatment
    /**
     * Optional clinical coding for a coded diagnosis (#357). `title` stays
     * the canonical display; these fields are opt-in metadata so a coded
     * Spanish "Cefalea tensional" (ICD-10 G44.2) can travel through FHIR
     * export and back. Bios never validates the code against an external
     * catalogue.
     */
    val codeSystem: String? = null,                     // "ICD-10" | "ICD-11" | "SNOMED-CT" | other
    val code: String? = null,                           // e.g. "G44.2"
    val codeDisplay: String? = null,                    // canonical display in the source system's language
)
