package com.bios.app.physiology

/**
 * Owner-set physiological context that modifies how Bios interprets
 * baseline-relative signals (#159, audit gap §2.7).
 *
 * The 14-day rolling personal baseline is correct for a stable adult.
 * It produces systematically wrong signals in five populations:
 *
 *  - **Pregnancy** — RHR rises 10–20 bpm by Q2, BBT pattern is abolished,
 *    sleep architecture changes by trimester
 *  - **Postpartum** — sleep fragmentation is normative for ~6 months
 *  - **Paediatrics** — HR / RR ranges are age-dependent. PALS / EPLS /
 *    APLS reference ranges differ across six age bands (neonate, infant,
 *    toddler, preschool, school-age, adolescent). Adult-population
 *    emergency cutoffs are wrong: tachycardia ≥130 fires on a healthy
 *    toddler's resting HR; bradycardia ≤35 stays silent on a neonate at
 *    80 bpm in real bradycardia. Issue #198.
 *  - **Frailty** (>75) — falls and check-in misses have different
 *    prevalence; recovery patterns operate on different timescales
 *  - **Endurance athletes** — RHR of 42 is fitness, not bradycardia
 *
 * Manifesto guard: owner sets state explicitly. Bios never infers
 * pregnancy from BBT, athleticism from RHR, or paediatric band from
 * biomarkers — birth date is the only owner-supplied input that drives
 * automatic band selection, and the band is recomputed daily by
 * [com.bios.app.physiology.PaediatricBandWorker].
 */
enum class PhysiologyState(val displayName: String) {
    /** Default. Standard adult physiology, all patterns active. */
    STANDARD("Standard adult"),
    PREGNANCY_T1("Pregnancy — trimester 1"),
    PREGNANCY_T2("Pregnancy — trimester 2"),
    PREGNANCY_T3("Pregnancy — trimester 3"),
    POSTPARTUM("Postpartum (first 6 months)"),
    ATHLETE_HIGH_FITNESS("Endurance athlete"),
    FRAILTY_FLAG("Frailty / age >75"),

    /**
     * Coarse paediatric parent flag — retained so the v1 SepsisScreenPattern
     * `excludedStates` and existing call sites keep working, and so an
     * owner who wants to flag "child" without supplying a birth date can
     * still do so. New patterns that need band-specific thresholds should
     * use the six sub-bands below ([NEONATE_0_28D]..[ADOLESCENT_13Y_18Y]).
     * The [PAEDIATRIC_ALL] companion set is the canonical "any paediatric
     * band" gate.
     */
    PAEDIATRIC("Paediatric (under 18) — unspecified band"),

    /** Neonate, 0–28 days. PALS HR 100–180 bpm, RR 30–60 /min. */
    NEONATE_0_28D("Paediatric — neonate (0–28 days)"),

    /** Infant, 1–12 months. PALS HR 80–160 bpm, RR 25–50 /min. */
    INFANT_1M_12M("Paediatric — infant (1–12 months)"),

    /** Toddler, 1–3 years. PALS HR 80–130 bpm, RR 20–30 /min. */
    TODDLER_1Y_3Y("Paediatric — toddler (1–3 years)"),

    /** Preschool, 3–5 years. PALS HR 80–120 bpm, RR 20–25 /min. */
    PRESCHOOL_3Y_5Y("Paediatric — preschool (3–5 years)"),

    /** School age, 6–12 years. PALS HR 70–110 bpm, RR 14–22 /min. */
    SCHOOL_AGE_6Y_12Y("Paediatric — school age (6–12 years)"),

    /** Adolescent, 13–18 years. PALS HR 60–100 bpm, RR 12–20 /min. */
    ADOLESCENT_13Y_18Y("Paediatric — adolescent (13–18 years)"),

    /**
     * Hospice / end-of-life context (#184, audit gap §3.2 from
     * GERIATRICS_PALLIATIVE_POV.md, with converging support from the
     * Oncology, Emergency/Critical Care, and Cardiology HF-prognosis
     * audits). When the owner sets HOSPICE_MODE, the URGENT-tier
     * escalation pathway short-circuits: alerts may still appear
     * in-app for the owner and caregivers to read, but no auto-call,
     * no SMS-to-emergency-contact, and no high-importance notification
     * sound or vibration. The manifesto's "silence is a feature"
     * reaches its purest form here — Bios honors what the owner
     * already declared.
     *
     * Owner-revocable instantly. No waiting period. No clinical
     * confirmation. The owner is final.
     */
    HOSPICE_MODE("Hospice / end-of-life care"),

    /**
     * Owner-declared known asthma (#200, audit gap §2.9 from
     * PAEDIATRICS_POV.md + MEDICAL_PROFESSIONAL_POV.md). Gates the
     * acute asthma-exacerbation screening pattern via the
     * [com.bios.app.alerts.ConditionPattern.requiredStates] axis —
     * the pattern only fires for owners who have flagged themselves
     * as asthmatic. Healthy-owner false positives from a strenuous
     * hike (RR up + activity drop) or transient illness would dominate
     * without this gate. Same shape as HOSPICE_MODE: owner-revocable,
     * no clinical confirmation, the owner is final.
     */
    KNOWN_ASTHMA("Known asthma"),

    /**
     * Owner-declared known COPD (#200). Gates the acute COPD-exacerbation
     * screening pattern. COPD owners typically have lower SpO2 baselines
     * than the general population (88–92 % is normal for some, not hypoxia),
     * and the personal-baseline framework handles that; the acute screen
     * additionally fires only for this population so non-COPD owners on a
     * high-altitude trip don't trigger it. Owner-revocable, no clinical
     * confirmation required.
     */
    KNOWN_COPD("Known COPD"),

    /**
     * Owner-set: a clinician has diagnosed heart failure (HFrEF, HFpEF, or
     * mid-range EF). Gates the HeartLogic-lite decompensation-prodrome
     * pattern in [com.bios.app.alerts.HeartFailureDecompensationPattern].
     *
     * Cardiology audit §2.4 (CARDIOLOGY_POV.md): the wearable-derivable subset
     * of the FDA-cleared HeartLogic algorithm (Boehmer 2017 MultiSENSE) —
     * RHR trend, nocturnal RR trend, activity decline, body-weight rise —
     * predicts decompensation 7–14 days ahead in known-HF populations.
     * Firing the prodrome pattern on owners *without* a HF diagnosis would
     * be clinically irrelevant noise (a 32-year-old with a viral illness
     * would light up every signal), so the pattern only runs when the
     * owner has affirmatively set this state. Manifesto guard: Bios never
     * infers HF — the owner picks.
     */
    KNOWN_HF("Known heart failure"),

    /**
     * Cardio-oncology contexts (#201, ONCOLOGY_POV §2.3-2.5). Owner-declared
     * cancer therapy state; enables CardioOncologyPatterns surveillance via
     * the `requiredStates` axis. Drug-class annotation lives separately in
     * [PhysiologyStateStore.drugClass]. Bios never infers cancer therapy.
     */
    ON_ACTIVE_CHEMOTHERAPY("Active chemotherapy"),
    ON_IMMUNE_CHECKPOINT_INHIBITOR("Immune-checkpoint inhibitor (ICI) therapy"),
    ON_CAR_T_RECOVERY("CAR-T cell therapy recovery"),

    /**
     * Peri-operative states (#205, SURGICAL_POV §2.1-§2.6). Owner-set or
     * auto-transitioned via [com.bios.app.physiology.PerioperativeStateTransitionWorker].
     * Suppresses baseline-relative patterns whose deviations are normative
     * post-op, and activates post-op-windowed surveillance patterns
     * (SSI / VTE / anastomotic-leak) via `requiredStates`.
     */
    PREHAB_WINDOW("Pre-op prehabilitation window"),
    POD_0_30("Post-op days 0–30"),
    POD_30_90("Post-op days 30–90"),
    POD_90_PLUS("Post-op 90+ days");

    companion object {
        /** Convenience set: all pregnancy trimesters. */
        val PREGNANCY: Set<PhysiologyState> = setOf(PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3)

        /**
         * Convenience set: every paediatric state, including the coarse
         * [PAEDIATRIC] parent flag and the six age-banded sub-states.
         * Use in [com.bios.app.alerts.ConditionPattern.excludedStates] when
         * an adult-validated pattern (e.g. NEWS2, adult cardiovascular
         * deconditioning thresholds) should not fire for any paediatric
         * owner.
         */
        val PAEDIATRIC_ALL: Set<PhysiologyState> = setOf(
            PAEDIATRIC, NEONATE_0_28D, INFANT_1M_12M, TODDLER_1Y_3Y,
            PRESCHOOL_3Y_5Y, SCHOOL_AGE_6Y_12Y, ADOLESCENT_13Y_18Y,
        )

        /**
         * Pre-adolescent paediatric sub-bands (0–12 years). Adult-population
         * cardiovascular trend patterns (cardiovascular_stress,
         * cardiorespiratoryDeconditioning) ship suppressed for these bands
         * because the personal-baseline assumption is invalid: growing
         * children have non-stationary baselines and PALS-anchored absolute
         * cutoffs differ enough that the adult-z-score machinery just
         * mis-fires. Adolescents (13–18y) approach adult physiology and
         * keep the trend patterns active. Issue #198.
         */
        val PAEDIATRIC_PRE_ADOLESCENT: Set<PhysiologyState> = setOf(
            NEONATE_0_28D, INFANT_1M_12M, TODDLER_1Y_3Y,
            PRESCHOOL_3Y_5Y, SCHOOL_AGE_6Y_12Y,
        )

        /** All cardio-oncology treatment states (#201). */
        val CANCER_TREATMENT: Set<PhysiologyState> = setOf(
            ON_ACTIVE_CHEMOTHERAPY, ON_IMMUNE_CHECKPOINT_INHIBITOR, ON_CAR_T_RECOVERY,
        )

        /** All peri-operative states (SURGICAL_POV §2.1). */
        val PERIOPERATIVE: Set<PhysiologyState> = setOf(
            PREHAB_WINDOW, POD_0_30, POD_30_90, POD_90_PLUS,
        )
    }
}
