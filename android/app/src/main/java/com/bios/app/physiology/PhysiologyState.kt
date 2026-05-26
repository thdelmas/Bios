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
     * Perimenopause (#209, OBGYN_POV §2.12). STRAW+10 staging — early
     * and late phases collapsed into one engine-facing state because
     * the gating effect is the same: cycle-pattern variability IS the
     * physiology. The `menstrual_cycle_anomaly` pattern (which assumes
     * a stable personal cycle baseline) is suppressed here so the
     * owner doesn't get false anomaly flags for what STRAW+10 calls
     * normal perimenopausal cycle variability.
     *
     * Vasomotor symptoms (hot flashes, night sweats) also skew
     * nighttime skin temperature and HR; future passes can add
     * perimenopause-specific replacement patterns.
     *
     * Reference: Harlow SD et al. (2012) "Executive summary of the
     * Stages of Reproductive Aging Workshop + 10," J Clin Endocrinol
     * Metab 97(4):1159–1168.
     */
    PERIMENOPAUSE("Perimenopause"),

    /**
     * Postmenopause (#209). ≥12 months since last menstrual period
     * (STRAW+10 stage +1 / +2). Cycle-anomaly patterns are obviously
     * inapplicable; bone-density and cardiovascular risk profile shift
     * — surface for future patterns to consult, currently used as the
     * negative gate on cycle-related anomaly detection.
     */
    POSTMENOPAUSE("Postmenopause"),

    /**
     * Owner-recorded polycystic ovary syndrome (#209, OBGYN §2.13).
     * Gates heightened glucose / HbA1c sensitivity — PCOS roughly
     * doubles the lifetime risk of type-2 diabetes (Moran et al. 2010,
     * Hum Reprod Update), so the metabolic-drift threshold is the
     * place future passes will tighten. The owner sets the flag from
     * Settings → PCOS / Endometriosis context; Bios never infers it.
     */
    KNOWN_PCOS("Known PCOS"),

    /**
     * Owner-recorded endometriosis (#209, OBGYN §2.14). Gates
     * pelvic-pain symptom tracking on the ESAS-r framework. The owner
     * sets the flag from Settings → PCOS / Endometriosis context;
     * Bios never infers it.
     */
    KNOWN_ENDOMETRIOSIS("Known endometriosis");

    companion object {
        /** Convenience set: all pregnancy trimesters. */
        val PREGNANCY: Set<PhysiologyState> = setOf(PREGNANCY_T1, PREGNANCY_T2, PREGNANCY_T3)

        /**
         * Convenience set: any menopause-transition state where
         * cycle-stability-based patterns become non-applicable.
         */
        val MENOPAUSE_TRANSITION: Set<PhysiologyState> =
            setOf(PERIMENOPAUSE, POSTMENOPAUSE)
    }
}
