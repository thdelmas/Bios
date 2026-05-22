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
    KNOWN_HF("Known heart failure");

    companion object {
        /** Convenience set: all pregnancy trimesters. */
        val PREGNANCY: Set<PhysiologyState> = setOf(PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3)
    }
}
