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
 *  - **Paediatrics** — HR ranges age-dependent; SpO2 cutoffs differ
 *  - **Frailty** (>75) — falls and check-in misses have different
 *    prevalence; recovery patterns operate on different timescales
 *  - **Endurance athletes** — RHR of 42 is fitness, not bradycardia
 *
 * Manifesto guard: owner sets state explicitly. Bios never infers
 * pregnancy from BBT or athleticism from RHR.
 *
 * v1 ships the enum + the `excludedStates` gating on
 * [com.bios.app.alerts.ConditionPattern] + one pattern wired (the
 * cardiovascular-stress pattern excludes pregnancy states because RHR
 * rises in pregnancy are normative). Pattern-by-pattern threshold
 * modifiers (paediatric HR-high, athlete RHR-low, pregnancy SpO2
 * floor), age-band tables, and pregnancy replacement patterns
 * (preeclampsia_signature) all land as follow-ups.
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
    PAEDIATRIC("Paediatric (under 18)"),

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
    KNOWN_COPD("Known COPD");

    companion object {
        /** Convenience set: all pregnancy trimesters. */
        val PREGNANCY: Set<PhysiologyState> = setOf(PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3)
    }
}
