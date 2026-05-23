package com.bios.app

import com.bios.app.model.ImmunizationRecord
import com.bios.app.screening.DoseStatus
import com.bios.app.screening.PaediatricVaccineCadenceEngine
import com.bios.app.ui.immunisations.PaediatricVaccineSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the paediatric ACIP cadence engine (#191). The engine is pure
 * — `now` is a parameter — so we pin time with concrete epoch values
 * and exercise the four status transitions: `Recorded`,
 * `NotYetEligible`, `DueNow`, `Overdue`.
 */
class PaediatricVaccineCadenceEngineTest {

    // 2026-05-21 → matches the audit date.
    private val now: Long = 1_747_824_000_000L
    private val day = 86_400_000L
    private val daysPerMonth = 30.4375

    private fun monthsBefore(months: Int): Long =
        now - (months * daysPerMonth * day).toLong()

    @Test
    fun paediatric_schedule_covers_the_canonical_acip_set() {
        val displayNames = PaediatricVaccineSchedule.entries.map { it.displayName }
        // Spot-check the audit-named anchors: HepB birth dose, the
        // 2/4/6mo battery, MMR/VAR/HepA at 12mo, and the 11–12y combo.
        assertTrue(displayNames.any { it.contains("Hepatitis B") })
        assertTrue(displayNames.any { it.contains("DTaP") })
        assertTrue(displayNames.any { it.contains("IPV") })
        assertTrue(displayNames.any { it.contains("PCV") })
        assertTrue(displayNames.any { it.contains("Rotavirus") })
        assertTrue(displayNames.any { it.contains("Hib") })
        assertTrue(displayNames.any { it.contains("MMR") })
        assertTrue(displayNames.any { it.contains("Varicella") })
        assertTrue(displayNames.any { it.contains("Hepatitis A") })
        assertTrue(displayNames.any { it.contains("HPV") })
        assertTrue(displayNames.any { it.contains("Tdap") })
        assertTrue(displayNames.any { it.contains("MenACWY") })
    }

    @Test
    fun newborn_first_DTaP_dose_is_NotYetEligible() {
        val dtap = PaediatricVaccineSchedule.byCvx("20")
        assertNotNull(dtap)
        // Baby is 1 month old → DTaP first dose (min 2mo) not yet due.
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = dtap!!,
            childBirthDate = monthsBefore(1),
            records = emptyList(),
            now = now,
        )
        val firstDose = statuses.first()
        assertEquals(DoseStatus.NotYetEligible, firstDose.second)
    }

    @Test
    fun two_month_old_first_DTaP_dose_is_DueNow() {
        val dtap = PaediatricVaccineSchedule.byCvx("20")!!
        // Pin age at ~2.5 months — squarely inside the dose-1 window of
        // [min 2mo, max 3mo]. Using exactly 2 months drifts just under
        // the floor on the `daysPerMonth` (30.4375) integer round-trip.
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = dtap,
            childBirthDate = (now - (2.5 * daysPerMonth * day).toLong()),
            records = emptyList(),
            now = now,
        )
        assertEquals(DoseStatus.DueNow, statuses.first().second)
    }

    @Test
    fun recorded_dose_renders_as_Recorded() {
        val dtap = PaediatricVaccineSchedule.byCvx("20")!!
        val birth = monthsBefore(5)
        val firstDoseDate = monthsBefore(3)
        val record = ImmunizationRecord(
            vaccineName = "DTaP",
            cvxCode = "20",
            occurrenceDate = firstDoseDate,
            doseNumber = 1,
        )
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = dtap,
            childBirthDate = birth,
            records = listOf(record),
            now = now,
        )
        val first = statuses.first()
        assertTrue("expected Recorded, got ${first.second}", first.second is DoseStatus.Recorded)
        val recorded = first.second as DoseStatus.Recorded
        assertEquals(firstDoseDate, recorded.occurrenceDate)
    }

    @Test
    fun missed_dose_past_max_age_window_is_Overdue() {
        val dtap = PaediatricVaccineSchedule.byCvx("20")!!
        // Child is 12 months old, no DTaP recorded → dose 1 (max age
        // 3mo) is overdue by ~9 months.
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = dtap,
            childBirthDate = monthsBefore(12),
            records = emptyList(),
            now = now,
        )
        val first = statuses.first()
        assertTrue("expected Overdue, got ${first.second}", first.second is DoseStatus.Overdue)
        val overdue = first.second as DoseStatus.Overdue
        assertTrue("monthsOverdue ~9, got ${overdue.monthsOverdue}", overdue.monthsOverdue in 8..10)
    }

    @Test
    fun records_without_cvx_code_are_ignored_for_cadence_math() {
        val dtap = PaediatricVaccineSchedule.byCvx("20")!!
        val freeText = ImmunizationRecord(
            vaccineName = "Some shot",
            cvxCode = null,
            occurrenceDate = (now - (1.0 * daysPerMonth * day).toLong()),
            doseNumber = 1,
        )
        // Child is ~2.5mo (inside the dose-1 window [2mo–3mo]). Free-text
        // record without CVX → engine should still mark dose 1 as DueNow
        // because we can't match the dose-number anchor.
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = dtap,
            childBirthDate = (now - (2.5 * daysPerMonth * day).toLong()),
            records = listOf(freeText),
            now = now,
        )
        assertEquals(DoseStatus.DueNow, statuses.first().second)
    }

    @Test
    fun MMR_first_dose_at_12mo_renders_as_DueNow() {
        val mmr = PaediatricVaccineSchedule.byCvx("03")!!
        val statuses = PaediatricVaccineCadenceEngine.evaluate(
            schedule = mmr,
            childBirthDate = monthsBefore(13),
            records = emptyList(),
            now = now,
        )
        assertEquals(DoseStatus.DueNow, statuses.first().second)
    }

    @Test
    fun evaluateAll_flattens_every_schedule_with_dose_recommendations() {
        val results = PaediatricVaccineCadenceEngine.evaluateAll(
            childBirthDate = monthsBefore(24),
            records = emptyList(),
            now = now,
        )
        val expectedDoseCount = PaediatricVaccineSchedule.entries.sumOf { it.doses.size }
        assertEquals(expectedDoseCount, results.size)
    }
}
