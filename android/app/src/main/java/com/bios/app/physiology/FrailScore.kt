package com.bios.app.physiology

/**
 * FRAIL questionnaire scoring (Morley 2012, IANA).
 *
 * Five-item owner-administered screen — Fatigue, Resistance, Ambulation,
 * Illnesses, Loss of weight. Each item scores 0 or 1; total 0–5.
 *
 *  - 0      → robust
 *  - 1–2    → pre-frail
 *  - ≥3     → frail
 *
 * Validated against the Fried phenotype (Fried 2001) for case-finding in
 * community-dwelling older adults; widely used because it is shorter and
 * owner-administrable without grip dynamometry or timed-gait equipment.
 *
 * Manifesto guard: this is a *pull-side* instrument. Bios never infers
 * frailty from biomarkers; the owner administers the questionnaire and
 * the score sets [PhysiologyState.FRAILTY_FLAG] when ≥3. Closes the
 * unwired-`FRAILTY_FLAG` half of the geriatrics/palliative audit
 * (GERIATRICS_PALLIATIVE_POV.md §2.1, §2.2).
 *
 * Per-item conventions follow Morley 2012:
 *  - **Fatigue** — "Are you fatigued?" 1 = all or most of the time in the
 *    past 4 weeks; 0 = some/little/none.
 *  - **Resistance** — "Do you have difficulty climbing 10 steps alone
 *    without resting, without aids?" 1 = yes; 0 = no.
 *  - **Ambulation** — "Do you have difficulty walking several hundred
 *    yards alone without aids?" 1 = yes; 0 = no.
 *  - **Illnesses** — count of physician-told conditions from a list of 11
 *    (HTN, DM, cancer, chronic lung disease, MI, CHF, angina, asthma,
 *    arthritis, stroke, kidney disease). ≥5 = 1; <5 = 0.
 *  - **Loss of weight** — ≥5 % unintentional body-mass loss in the past
 *    year. 1 = yes; 0 = no.
 */
data class FrailResponses(
    val fatigueMostOfTime: Boolean = false,
    val cannotClimbTenSteps: Boolean = false,
    val cannotWalkSeveralHundredYards: Boolean = false,
    val illnessCount: Int = 0,
    val unintentionalWeightLossOver5Percent: Boolean = false,
) {
    init {
        require(illnessCount in 0..11) {
            "FRAIL illness count must be 0..11 (Morley 2012's 11-item list), was $illnessCount"
        }
    }
}

/**
 * Categorical interpretation of a [FrailResponses] total. The total is
 * reported separately so the owner can see the raw score; the category
 * is what drives [PhysiologyState] gating.
 */
enum class FrailCategory(val displayName: String) {
    ROBUST("Robust"),
    PRE_FRAIL("Pre-frail"),
    FRAIL("Frail");
}

object FrailScore {

    /** Total score, 0–5. */
    fun score(r: FrailResponses): Int {
        var s = 0
        if (r.fatigueMostOfTime) s++
        if (r.cannotClimbTenSteps) s++
        if (r.cannotWalkSeveralHundredYards) s++
        if (r.illnessCount >= ILLNESS_BURDEN_CUTOFF) s++
        if (r.unintentionalWeightLossOver5Percent) s++
        return s
    }

    /** Maps a raw 0–5 total to [FrailCategory] per Morley 2012 cutoffs. */
    fun categorize(score: Int): FrailCategory = when {
        score <= 0 -> FrailCategory.ROBUST
        score < FRAIL_CUTOFF -> FrailCategory.PRE_FRAIL
        else -> FrailCategory.FRAIL
    }

    /** Convenience: score → category in one step. */
    fun category(r: FrailResponses): FrailCategory = categorize(score(r))

    /**
     * True when the category should activate [PhysiologyState.FRAILTY_FLAG]
     * in [PhysiologyStateStore]. Only the FRAIL category does; PRE_FRAIL is
     * surfaced to the owner but does not modify pattern firing — the
     * audit's threshold-modifier work for pre-frailty is a follow-up.
     */
    fun shouldActivateFrailtyFlag(r: FrailResponses): Boolean =
        category(r) == FrailCategory.FRAIL

    /**
     * Number of physician-told conditions at or above which the
     * "Illnesses" item scores 1, per Morley 2012.
     */
    const val ILLNESS_BURDEN_CUTOFF = 5

    /** Total ≥ this is the FRAIL category. */
    const val FRAIL_CUTOFF = 3
}
