package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Owner-recorded allergy or intolerance (#355). Audit gap surfaced by the
 * Sagrat Cor discharge: every clinical encounter starts with an allergies
 * line, and Bios had no way to record one — not even "no known allergies".
 *
 * **Recording is not endorsement.** Bios stores the owner's stated
 * allergies; it never adjudicates whether a reaction was truly IgE-mediated,
 * idiosyncratic, side-effect, or psychosomatic. [reactionType] is
 * descriptive — picked by the owner — not diagnostic.
 *
 * **Hard delete is allowed.** Unlike [MedicationAnnotation], where
 * discontinuation preserves history, an allergy entered by mistake should
 * not haunt the record. The owner can delete a row outright.
 *
 * **Anaphylaxis pattern context (#355 follow-up).** Once this entity ships,
 * `AcuteWindowPatterns.anaphylaxisScreen` may consult the active allergens
 * list to weight its HR + SpO2 signature — but only if the owner has
 * manually logged an exposure event in the lookback window. No inference
 * from medication-intake logs. That integration is **not** part of this
 * PR.
 */
@Entity(
    tableName = "allergy_records",
    indices = [Index("substance"), Index("verifiedByClinician")],
)
data class AllergyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Free-text substance name as the owner records it ("penicillin",
     * "shellfish", "latex", "iodine contrast"). Stays the canonical
     * display string regardless of whether [substanceCode] is set.
     */
    val substance: String,
    /**
     * Optional structured code drawn from a clinical vocabulary
     * (RxNorm CUI for drugs, SNOMED CT for substances). Free text —
     * Bios never validates against an external service.
     */
    val substanceCode: String? = null,
    /** Vocabulary the [substanceCode] is drawn from ("RxNorm", "SNOMED-CT"). */
    val substanceSource: String? = null,
    /** Allergy vs intolerance vs side-effect vs unknown — owner's pick. */
    val reactionType: ReactionType = ReactionType.UNKNOWN,
    /** Subjective severity for the worst reaction the owner has had. */
    val severity: AllergySeverity = AllergySeverity.UNKNOWN,
    /**
     * Free-text symptom description ("hives, throat tightness", "GI upset
     * within 30 min", "dry cough"). No structured taxonomy — surfaced
     * verbatim on the allergies screen.
     */
    val manifestation: String? = null,
    /** When the reaction first occurred, if known. Epoch millis. */
    val onsetDate: Long? = null,
    /**
     * Owner-asserted vs clinician-documented. The clinician-documented
     * flag is purely informational — Bios never verifies the assertion.
     */
    val verifiedByClinician: Boolean = false,
    /** Free-text note for anything that doesn't fit the structured fields. */
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class ReactionType { ALLERGY, INTOLERANCE, SIDE_EFFECT, UNKNOWN }

enum class AllergySeverity { MILD, MODERATE, SEVERE, LIFE_THREATENING, UNKNOWN }
