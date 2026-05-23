package com.bios.app.physiology

import java.time.LocalDate
import java.time.Period

/**
 * Pure-function band derivation from birth date (#198). Splits the
 * paediatric population into the six PALS / EPLS / APLS age bands:
 *
 *  - `0 – 28 days`            → [PhysiologyState.NEONATE_0_28D]
 *  - `29 days – 11 months`    → [PhysiologyState.INFANT_1M_12M]
 *  - `1 year – 2 years 11mo`  → [PhysiologyState.TODDLER_1Y_3Y]
 *  - `3 years – 5 years 11mo` → [PhysiologyState.PRESCHOOL_3Y_5Y]
 *  - `6 years – 12 years 11mo`→ [PhysiologyState.SCHOOL_AGE_6Y_12Y]
 *  - `13 years – 17 years 11mo` → [PhysiologyState.ADOLESCENT_13Y_18Y]
 *  - `≥ 18 years`             → [PhysiologyState.STANDARD]
 *
 * Boundaries follow the AHA PALS 2020 + UK Resuscitation Council APLS
 * 2021 convention: a band is entered the day the owner crosses the
 * lower bound and exited the day they cross the next lower bound. So
 * a 19-year-old (born 19 years before "today") falls out of the
 * adolescent band into [PhysiologyState.STANDARD].
 *
 * The calculator is intentionally side-effect-free — date in, state
 * out — so it's directly unit-testable on day-boundary edges (29d,
 * 1y, 3y, 6y, 13y, 18y).
 */
object PaediatricBandCalculator {

    /**
     * Compute the [PhysiologyState] for an owner with [birthDate] on
     * [today]. Future birth dates (clock-skew or owner typo) collapse
     * to [PhysiologyState.NEONATE_0_28D] — Bios doesn't second-guess
     * the owner's input, but a negative-age never tips into adult
     * thresholds where the safety risk is largest.
     */
    fun bandFor(birthDate: LocalDate, today: LocalDate = LocalDate.now()): PhysiologyState {
        if (!today.isAfter(birthDate)) return PhysiologyState.NEONATE_0_28D

        val days = java.time.temporal.ChronoUnit.DAYS.between(birthDate, today)
        if (days < NEONATE_MAX_DAYS) return PhysiologyState.NEONATE_0_28D

        val age = Period.between(birthDate, today)
        val years = age.years
        return when {
            years < 1 -> PhysiologyState.INFANT_1M_12M
            years < 3 -> PhysiologyState.TODDLER_1Y_3Y
            years < 6 -> PhysiologyState.PRESCHOOL_3Y_5Y
            years < 13 -> PhysiologyState.SCHOOL_AGE_6Y_12Y
            years < 18 -> PhysiologyState.ADOLESCENT_13Y_18Y
            else -> PhysiologyState.STANDARD
        }
    }

    /**
     * Neonatal period in days. PALS uses "first 28 days of life", which
     * means day 0..27 inclusive; on day 28 the owner enters the infant
     * band. Encoded as a strict less-than for clarity.
     */
    private const val NEONATE_MAX_DAYS = 28L
}
