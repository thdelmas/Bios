package com.bios.app.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Owner-declared goals of care (#184, audit gap §3.2 from
 * GERIATRICS_PALLIATIVE_POV.md, with converging support from the
 * Oncology, Emergency/Critical Care, and Cardiology HF-prognosis
 * audits).
 *
 * When the owner has set goals of care — e.g. DNAR, DNI, comfort-only,
 * no-hospitalisation — the URGENT-tier escalation pathway must honor
 * those goals. A cancer patient in hospice should not have their
 * wearable calling EMS at every detected emergency. The owner's
 * documented wishes dominate.
 *
 * Per the manifesto: the owner sets prospectively while competent.
 * The system honors what the owner already declared. Bios never
 * automatically detects goals of care — this is an owner-declared
 * setting only.
 *
 * **Single-row design.** Always `id = SINGLETON_ID`; the repo upserts.
 * This is one owner's standing preference, not a history table.
 *
 * **Reference framework:** This data shape follows the POLST/MOLST
 * (Physician/Medical Orders for Life-Sustaining Treatment) framework
 * and the NCP Clinical Practice Guidelines for Quality Palliative
 * Care, 4th edition (2018). Bios stores the owner's *self-declared*
 * preferences; the authoritative clinical document remains with the
 * owner's clinical team (referenced via [documentLocation]).
 *
 * Manifesto guard: pull-side only. Setting these preferences never
 * pushes a notification. The owner navigates in, the owner edits.
 * Hospice mode (see [com.bios.app.physiology.PhysiologyState.HOSPICE_MODE])
 * is owner-revocable instantly — no waiting period, no clinical
 * confirmation, no friction beyond the standard confirmation dialog.
 */
@Entity(tableName = "goals_of_care")
data class GoalsOfCare(
    @PrimaryKey val id: Long = SINGLETON_ID,
    /** Owner's CPR preference. Default UNSPECIFIED — owner hasn't declared. */
    val cprPreference: CprPreference = CprPreference.UNSPECIFIED,
    /** Owner's hospitalisation preference. Default UNSPECIFIED. */
    val hospitalizationPreference: HospitalizationPreference = HospitalizationPreference.UNSPECIFIED,
    /** Owner's intervention-level preference. Default UNSPECIFIED. */
    val interventionLevel: InterventionLevel = InterventionLevel.UNSPECIFIED,
    /** Epoch millis when the owner last reviewed this declaration. */
    val lastReviewedAt: Long = System.currentTimeMillis(),
    /**
     * Free-text owner-controlled location of the authoritative document
     * (e.g. "POLST in chart at clinic X", "advance directive in safe
     * deposit box"). Bios does not store the document itself — only the
     * owner's pointer.
     */
    val documentLocation: String? = null,
    /** Owner-set free-text notes / context. */
    val notes: String? = null,
) {
    companion object {
        /**
         * Stable primary key for the single owner-profile row. The repo
         * upserts on this id so save() always replaces the previous row.
         */
        const val SINGLETON_ID: Long = 1L
    }
}

/**
 * Owner's CPR (cardiopulmonary resuscitation) preference.
 *
 * - [FULL_CODE] — owner wants resuscitation attempts (default
 *   clinical assumption when nothing is on file).
 * - [DNAR_DNI] — Do-Not-Attempt-Resuscitation + Do-Not-Intubate.
 *   The most common documented preference at end of life.
 * - [DNAR_OK_TO_INTUBATE] — rare; the owner declines chest
 *   compressions but accepts intubation for reversible respiratory
 *   failure. Surfaced for completeness.
 * - [UNSPECIFIED] — owner hasn't declared. Treated as FULL_CODE by
 *   downstream code when a decision is forced, but kept distinct so
 *   the UI can prompt for an explicit choice rather than assuming.
 */
enum class CprPreference(val displayName: String) {
    UNSPECIFIED("Not specified"),
    FULL_CODE("Full code (attempt resuscitation)"),
    DNAR_DNI("DNAR + DNI (do not resuscitate, do not intubate)"),
    DNAR_OK_TO_INTUBATE("DNAR, OK to intubate for reversible respiratory failure"),
}

/**
 * Owner's preference about hospitalisation.
 *
 * - [ANY] — owner accepts any clinically indicated hospitalisation.
 * - [COMFORT_FOCUSED] — owner accepts hospitalisation only when
 *   needed for symptom relief (e.g. pain crisis).
 * - [AVOID_UNLESS_PAINFUL] — owner prefers to avoid hospital except
 *   to relieve significant pain or distress.
 * - [NONE] — owner declines hospitalisation altogether. Aligns with
 *   home-hospice and DNH (Do-Not-Hospitalise) orders.
 */
enum class HospitalizationPreference(val displayName: String) {
    UNSPECIFIED("Not specified"),
    ANY("Any clinically indicated"),
    COMFORT_FOCUSED("Comfort-focused only"),
    AVOID_UNLESS_PAINFUL("Avoid unless needed for pain relief"),
    NONE("None (no hospitalisation)"),
}

/**
 * Owner's intervention-level preference. Mirrors the POLST Section B
 * "Medical Interventions" ladder.
 *
 * - [FULL] — full treatment, including ICU-level care.
 * - [SELECTIVE] — selective treatment (antibiotics, IV fluids,
 *   cardiac monitoring) but not intensive interventions.
 * - [COMFORT_ONLY] — comfort-focused care only. When this is set,
 *   Bios silences automatic URGENT-tier escalation alongside
 *   hospice mode.
 */
enum class InterventionLevel(val displayName: String) {
    UNSPECIFIED("Not specified"),
    FULL("Full treatment (including ICU)"),
    SELECTIVE("Selective treatment (no ICU / no intensive interventions)"),
    COMFORT_ONLY("Comfort-only care"),
}
