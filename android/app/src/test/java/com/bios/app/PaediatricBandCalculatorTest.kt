package com.bios.app

import com.bios.app.physiology.PaediatricBandCalculator
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Guards [PaediatricBandCalculator] (#198). The band boundaries follow
 * the PALS / EPLS / APLS convention:
 *
 *   0  – 28 days        → NEONATE_0_28D
 *   29 days – 11 months → INFANT_1M_12M
 *   1y – 2y 11mo        → TODDLER_1Y_3Y
 *   3y – 5y 11mo        → PRESCHOOL_3Y_5Y
 *   6y – 12y 11mo       → SCHOOL_AGE_6Y_12Y
 *   13y – 17y 11mo      → ADOLESCENT_13Y_18Y
 *   ≥ 18y               → STANDARD
 *
 * Day-boundary edges are the load-bearing assertions — the audit
 * specifies that the band transitions land on exact day boundaries
 * (1y, 3y, 6y, 13y, 18y) so a worker recompute the morning after a
 * birthday produces the right band.
 */
class PaediatricBandCalculatorTest {

    private val today = LocalDate.of(2026, 1, 1)

    private fun bandOnReferenceDate(birth: LocalDate): PhysiologyState =
        PaediatricBandCalculator.bandFor(birth, today)

    // -- canonical mid-band cases --

    @Test
    fun newborn_today_is_neonate() {
        assertEquals(PhysiologyState.NEONATE_0_28D, bandOnReferenceDate(today))
    }

    @Test
    fun two_week_old_is_neonate() {
        assertEquals(
            PhysiologyState.NEONATE_0_28D,
            bandOnReferenceDate(today.minusDays(14)),
        )
    }

    @Test
    fun six_month_old_is_infant() {
        assertEquals(
            PhysiologyState.INFANT_1M_12M,
            bandOnReferenceDate(today.minusMonths(6)),
        )
    }

    @Test
    fun two_year_old_is_toddler() {
        assertEquals(
            PhysiologyState.TODDLER_1Y_3Y,
            bandOnReferenceDate(today.minusYears(2)),
        )
    }

    @Test
    fun four_year_old_is_preschool() {
        assertEquals(
            PhysiologyState.PRESCHOOL_3Y_5Y,
            bandOnReferenceDate(today.minusYears(4)),
        )
    }

    @Test
    fun ten_year_old_is_school_age() {
        assertEquals(
            PhysiologyState.SCHOOL_AGE_6Y_12Y,
            bandOnReferenceDate(today.minusYears(10)),
        )
    }

    @Test
    fun fifteen_year_old_is_adolescent() {
        assertEquals(
            PhysiologyState.ADOLESCENT_13Y_18Y,
            bandOnReferenceDate(today.minusYears(15)),
        )
    }

    @Test
    fun twenty_five_year_old_is_standard_adult() {
        assertEquals(
            PhysiologyState.STANDARD,
            bandOnReferenceDate(today.minusYears(25)),
        )
    }

    // -- day-boundary transitions (the audit's explicit checklist) --

    @Test
    fun day_27_is_still_neonate_but_day_28_is_infant() {
        // PALS "first 28 days of life" = day 0..27 inclusive.
        assertEquals(
            PhysiologyState.NEONATE_0_28D,
            bandOnReferenceDate(today.minusDays(27)),
        )
        assertEquals(
            PhysiologyState.INFANT_1M_12M,
            bandOnReferenceDate(today.minusDays(28)),
        )
    }

    @Test
    fun first_birthday_transitions_infant_to_toddler() {
        val firstBirthday = today.minusYears(1)
        assertEquals(
            PhysiologyState.TODDLER_1Y_3Y,
            PaediatricBandCalculator.bandFor(firstBirthday, today),
        )
        // The day before the first birthday is still in the infant band.
        assertEquals(
            PhysiologyState.INFANT_1M_12M,
            PaediatricBandCalculator.bandFor(firstBirthday.plusDays(1), today),
        )
    }

    @Test
    fun third_birthday_transitions_toddler_to_preschool() {
        val thirdBirthday = today.minusYears(3)
        assertEquals(
            PhysiologyState.PRESCHOOL_3Y_5Y,
            PaediatricBandCalculator.bandFor(thirdBirthday, today),
        )
        assertEquals(
            PhysiologyState.TODDLER_1Y_3Y,
            PaediatricBandCalculator.bandFor(thirdBirthday.plusDays(1), today),
        )
    }

    @Test
    fun sixth_birthday_transitions_preschool_to_school_age() {
        val sixthBirthday = today.minusYears(6)
        assertEquals(
            PhysiologyState.SCHOOL_AGE_6Y_12Y,
            PaediatricBandCalculator.bandFor(sixthBirthday, today),
        )
        assertEquals(
            PhysiologyState.PRESCHOOL_3Y_5Y,
            PaediatricBandCalculator.bandFor(sixthBirthday.plusDays(1), today),
        )
    }

    @Test
    fun thirteenth_birthday_transitions_school_age_to_adolescent() {
        val thirteenth = today.minusYears(13)
        assertEquals(
            PhysiologyState.ADOLESCENT_13Y_18Y,
            PaediatricBandCalculator.bandFor(thirteenth, today),
        )
        assertEquals(
            PhysiologyState.SCHOOL_AGE_6Y_12Y,
            PaediatricBandCalculator.bandFor(thirteenth.plusDays(1), today),
        )
    }

    @Test
    fun eighteenth_birthday_transitions_adolescent_to_standard_adult() {
        val eighteenth = today.minusYears(18)
        assertEquals(
            PhysiologyState.STANDARD,
            PaediatricBandCalculator.bandFor(eighteenth, today),
        )
        assertEquals(
            PhysiologyState.ADOLESCENT_13Y_18Y,
            PaediatricBandCalculator.bandFor(eighteenth.plusDays(1), today),
        )
    }

    @Test
    fun nineteenth_birthday_stays_standard_adult() {
        // Once across the 18y boundary, no further paediatric band fires.
        assertEquals(
            PhysiologyState.STANDARD,
            PaediatricBandCalculator.bandFor(today.minusYears(19), today),
        )
    }

    // -- defensive cases --

    @Test
    fun future_birth_date_collapses_to_neonate_band() {
        // Clock skew or typo — Bios doesn't second-guess the input, but
        // the safety-conservative fallback is neonate (the band where
        // an over-trigger of paediatric thresholds is least dangerous).
        assertEquals(
            PhysiologyState.NEONATE_0_28D,
            PaediatricBandCalculator.bandFor(today.plusDays(7), today),
        )
    }
}
