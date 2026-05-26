package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Generic owner-logged headache diary entry. Closes
 * [docs/audits/NEUROLOGY_POV.md] §2.5 (issue #207). Distinct from
 * [MigraineAttack]: a [HeadacheLog] is the simpler shape for owners whose
 * headaches don't classify as migraine — tension-type, cluster, secondary,
 * or unclassified. Both surfaces are available on the same Identity →
 * "Headache & migraine diary" screen; the owner picks the entity that
 * matches what they actually experienced.
 *
 * The headache-type taxonomy mirrors the IHS ICHD-3 high-level groupings
 * (tension-type §2, migraine §1, trigeminal autonomic cephalalgias incl.
 * cluster §3, other §4). When the owner is sure about the type they pick
 * the matching enum; when not, [HeadacheType.OTHER] keeps the record
 * useful for the medication-overuse-headache evaluator without forcing a
 * diagnostic claim.
 *
 * Manifesto guards: same as [MigraineAttack] — owner-administered, pull-
 * side only, on-device. Bios never infers a headache from biomarkers.
 *
 * The IHS ICHD-3 §8.2 medication-overuse-headache definition keys on
 * "acute headache treatment days per month" — every entry whose
 * [medicationTaken] is non-null contributes one such day for that
 * calendar date. [com.bios.app.alerts.MedicationOveruseHeadacheEvaluator]
 * counts these days alongside [MigraineAttack.medicationTaken] entries.
 */
@Entity(
    tableName = "headache_logs",
    indices = [Index("timestamp")],
)
data class HeadacheLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Epoch millis when the headache occurred / was logged. */
    val timestamp: Long,
    /** Headache intensity on the 0-10 numeric rating scale. */
    val intensity: Int,
    /**
     * Headache-type classification. IHS ICHD-3 groupings; [HeadacheType.OTHER]
     * is the safe choice when the owner is unsure.
     */
    val type: HeadacheType,
    /**
     * Duration of the headache episode in minutes. `null` for ongoing /
     * unknown — the diary surface presents an open-ended entry and the
     * owner sets the duration on close.
     */
    val durationMinutes: Int? = null,
    /**
     * Free-text name of acute treatment taken (e.g. "paracetamol 1g",
     * "ibuprofen 400mg", "aspirin"). Same shape as
     * [MigraineAttack.medicationTaken]; both surfaces contribute to the
     * MOH evaluator's acute-headache-treatment-days count.
     */
    val medicationTaken: String? = null,
    /** Optional owner free-text note. */
    val notes: String? = null,
) {
    companion object {
        const val MIN_INTENSITY = 0
        const val MAX_INTENSITY = 10

        /** Validates a 0-10 intensity; throws if out of range. */
        fun validateIntensity(intensity: Int) {
            require(intensity in MIN_INTENSITY..MAX_INTENSITY) {
                "Intensity must be in $MIN_INTENSITY..$MAX_INTENSITY (got $intensity)"
            }
        }
    }
}

/**
 * Owner-selected headache type. IHS ICHD-3 high-level groupings — no
 * sub-classification beyond the primary class, because the diagnostic
 * sub-classification belongs to a clinician, not the owner-input surface.
 */
enum class HeadacheType {
    /** IHS ICHD-3 §2 — tension-type headache (the most common headache). */
    TENSION,

    /**
     * IHS ICHD-3 §1 — migraine. Owners who want the richer migraine-
     * specific record (aura, triggers) use [MigraineAttack] instead;
     * this enum value is here for owners who log headaches uniformly
     * via this diary even when they meet migraine criteria.
     */
    MIGRAINE,

    /** IHS ICHD-3 §3.1 — cluster headache. */
    CLUSTER,

    /**
     * IHS ICHD-3 other (sinus, post-traumatic, medication-induced, etc.)
     * or owner-uncertain. The MOH evaluator does not need the type to
     * count acute-treatment days, so OTHER entries with
     * [HeadacheLog.medicationTaken] still feed the screen.
     */
    OTHER,
}
