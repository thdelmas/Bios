package com.bios.app.model

/**
 * `event_payloads` field-key namespace for the ICHD-3-shaped headache
 * event types added in #283 Cut 1:
 *
 *  - [com.bios.contracts.MetricType.MIGRAINE_ATTACK_EVENT]
 *  - [com.bios.contracts.MetricType.HEADACHE_ATTACK_EVENT]
 *  - [com.bios.contracts.MetricType.CLUSTER_HEADACHE_ATTACK_EVENT]
 *
 * Centralised so the entry surface ([MigraineAttack] / [HeadacheLog] →
 * MetricReading-row writer, Cut 2), the chronic-migraine evaluator
 * (Cut 3), the MOH evaluator (already shipping, will be updated to
 * consume the structured rows), and any future clinician-share
 * exporter can't drift on spelling. Same shape and rationale as
 * [SeizureEventFields].
 *
 * The IHS ICHD-3 (International Classification of Headache Disorders,
 * 3rd ed.) field set is the documented contract. Onset and end are
 * carried on the parent [MetricReading.timestamp] +
 * [MetricReading.durationSec] respectively — they don't need payload
 * fields. Severity is carried on the parent [MetricReading.value]
 * (0–10 numeric rating scale, the same scale [HeadacheLog.intensity]
 * and [MigraineAttack.peakIntensity] already use).
 *
 * The remaining ICHD-3 fields live here as nullable payload keys —
 * a payload row exists when the owner recorded that field at entry
 * time, doesn't when they didn't. Renderers must treat absence as
 * "owner did not record" not "owner answered no."
 */
object HeadacheEventFields {

    /**
     * Headache side. ICHD-3 §1.1.C.1 (migraine criterion: unilateral
     * location, often pulsating).
     * Allowed values: [SIDE_UNILATERAL_LEFT], [SIDE_UNILATERAL_RIGHT],
     * [SIDE_BILATERAL], [SIDE_HOLOCRANIAL].
     */
    const val SIDE = "side"
    const val SIDE_UNILATERAL_LEFT = "unilateral_left"
    const val SIDE_UNILATERAL_RIGHT = "unilateral_right"
    const val SIDE_BILATERAL = "bilateral"
    const val SIDE_HOLOCRANIAL = "holocranial"

    /**
     * Headache character. ICHD-3 §1.1.C.3 (migraine criterion: pulsating
     * quality). Allowed values: [CHARACTER_PULSATING],
     * [CHARACTER_PRESSING], [CHARACTER_STABBING],
     * [CHARACTER_BURNING], [CHARACTER_OTHER].
     */
    const val CHARACTER = "character"
    const val CHARACTER_PULSATING = "pulsating"
    const val CHARACTER_PRESSING = "pressing"
    const val CHARACTER_STABBING = "stabbing"
    const val CHARACTER_BURNING = "burning"
    const val CHARACTER_OTHER = "other"

    /** ICHD-3 §1.1.D.2 — photophobia. `longValue` 1 = true, 0 = false. */
    const val PHOTOPHOBIA = "photophobia"

    /** ICHD-3 §1.1.D.2 — phonophobia. `longValue` 1 = true, 0 = false. */
    const val PHONOPHOBIA = "phonophobia"

    /** ICHD-3 §1.1.D.1 — nausea (with or without vomiting). `longValue`
     *  1 = true, 0 = false. */
    const val NAUSEA = "nausea"

    /** ICHD-3 §1.2 — aura (visual / sensory / speech / motor symptoms
     *  preceding or accompanying the attack). `longValue` 1 = true,
     *  0 = false. */
    const val AURA = "aura"

    /** ICHD-3 §1.1.C.4 — aggravation by routine physical activity (e.g.
     *  walking, climbing stairs). `longValue` 1 = true, 0 = false. */
    const val AGGRAVATED_BY_ACTIVITY = "aggravated_by_activity"

    /** ICHD-3 §3.1.B.3 — cranial autonomic features ipsilateral to the
     *  pain (conjunctival injection, lacrimation, nasal congestion,
     *  ptosis, etc.). Diagnostic of cluster headache and other TACs.
     *  `longValue` 1 = true, 0 = false. */
    const val AUTONOMIC_FEATURES = "autonomic_features"

    /** Free-text owner-recorded triggers (comma-separated, mirrors the
     *  [MigraineTrigger] enum names where they apply). Stored on
     *  `stringValue`. */
    const val TRIGGERS = "triggers"

    /** Free-text name of the abortive medication taken (e.g.
     *  "sumatriptan 50 mg", "ibuprofen 400 mg"). Mirrors the existing
     *  [MigraineAttack.medicationTaken] / [HeadacheLog.medicationTaken]
     *  free-text fields so legacy rows can be carried forward. */
    const val ABORTIVE_MEDICATION = "abortive_medication"

    /** `MetricReading.id` of the linked
     *  [com.bios.contracts.MetricType.MEDICATION_INTAKE] row written by
     *  the per-event writer (Cut 2). Lets the MOH evaluator (and any
     *  future MS DMT adherence path) walk back from the structured
     *  medication-intake row to the parent headache event. Stored on
     *  `stringValue`. */
    const val ABORTIVE_MEDICATION_INTAKE_ID = "abortive_medication_intake_id"

    /** Owner free-text note. */
    const val NOTES = "notes"
}
