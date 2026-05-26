package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Clinical encounter — an admission/discharge from an ER, hospital, or
 * clinic visit (#358). Bundles together the records produced by one
 * visit so the owner can pull up "everything from this visit" as a unit.
 *
 * Surfaced by the Sagrat Cor discharge audit. A single ER visit produces
 * 30+ correlated data points (vitals, labs, diagnoses, imaging,
 * medications, follow-up referrals) that were previously scattered
 * across MetricReading / HealthEvent / MedicationAnnotation /
 * AllergyRecord / ImagingStudy with no join key.
 *
 * **Scope of this entity:** the encounter record itself (admission /
 * discharge times, facility, free-text reason and discharge summary,
 * language). The cross-cutting `encounterId` column on existing
 * entities (MetricReading, HealthEvent, etc.) is **not** part of this
 * PR — that's a separate, larger migration. Today, an encounter is a
 * descriptive record; the join is added when the existing entities
 * grow their encounterId columns.
 *
 * **Verbatim storage.** Discharge summary text lives untranslated and
 * unsummarised. No on-device LLM "simplification" of medical narrative
 * — that's evaluation, and evaluation belongs to the owner and their
 * clinician.
 *
 * **Erasure by design.** Hard delete is allowed (encounter rows + the
 * dependent FollowUpReferral rows cascade). If a future PR wires
 * encounterId on child rows, those should *unlink* (set null) rather
 * than cascade-delete — the lab result and the prescription are still
 * the owner's data even if the visit record goes away.
 */
@Entity(
    tableName = "clinical_encounters",
    indices = [Index("admissionAt"), Index("facilityKind")],
)
data class ClinicalEncounter(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    /** Facility name as the owner records it ("Hospital Universitari Sagrat Cor"). */
    val facility: String? = null,
    val facilityKind: FacilityKind = FacilityKind.OTHER,
    /** Admission timestamp, epoch millis. */
    val admissionAt: Long,
    /** Discharge timestamp; null while admitted as inpatient. */
    val dischargeAt: Long? = null,
    /** Owner-recorded or imported chief complaint / reason for visit. */
    val reasonForVisit: String? = null,
    /** Verbatim discharge-summary narrative (Spanish, English, etc.). */
    val dischargeSummaryText: String? = null,
    /** ISO 639-1 language tag for the summary text. */
    val dischargeSummaryLanguage: String? = null,
    /** Verbatim free-text follow-up instructions from the clinician. */
    val followUpInstructions: String? = null,
    /** Free-text owner note. */
    val ownerNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Kind of facility the encounter took place at. Pragmatic taxonomy that
 * matches how owners describe visits, not a regulatory billing code.
 */
enum class FacilityKind { ER, INPATIENT, OUTPATIENT, TELEHEALTH, URGENT_CARE, OTHER }

/**
 * Follow-up referral attached to a [ClinicalEncounter] (#358). Records
 * what the clinician said the owner should do next ("follow up with
 * Neurology within 2 weeks"). Zero-or-many per encounter.
 *
 * **No appointment scheduling, no calendar integration, no reminders.**
 * The manifesto explicitly rules these out — clinician owns recall;
 * Bios records, surfaces, never nags.
 */
@Entity(
    tableName = "follow_up_referrals",
    indices = [Index("encounterUuid"), Index("urgency")],
)
data class FollowUpReferral(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** FK to ClinicalEncounter.uuid. Not declared as a Room ForeignKey
     *  in v1 to keep migration shape simple; the constraint lives in
     *  the repo layer (delete-encounter also deletes its referrals). */
    val encounterUuid: String,
    /** Specialty or service the owner should follow up with
     *  ("Neurology", "AP / Primary Care", "Cardiology"). Free-text. */
    val specialty: String,
    val urgency: ReferralUrgency = ReferralUrgency.ROUTINE,
    /** "within 2 weeks", "as needed", "before next AFib episode". */
    val suggestedTimeframe: String? = null,
    /** Optional named facility the referral targets. */
    val facility: String? = null,
    /** Why the clinician issued the referral. */
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class ReferralUrgency { ROUTINE, URGENT, EMERGENT }
