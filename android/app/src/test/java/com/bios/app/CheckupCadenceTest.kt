package com.bios.app

import com.bios.app.model.ScreeningEntry
import com.bios.app.screening.CadenceKind
import com.bios.app.screening.OwnerDemographics
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the routine periodic-checkup catalog (periodic-checkup follow-up)
 * and the [CadenceKind.MIN_INTERVAL_SINCE_LAST] semantics in
 * [ScreeningCadenceEngine]. Split out of [ScreeningCadenceEngineTest] to
 * keep both files under the 500-line cap. Time is pinned the same way:
 * `now` is a concrete epoch, the engine takes it as a parameter.
 */
class CheckupCadenceTest {

    // 2026-05-21 → 1747824000000 (matches ScreeningCadenceEngineTest).
    private val now: Long = 1_747_824_000_000L
    private val day = 86_400_000L
    private val daysPerMonth = 30.4375  // matches the engine's constant

    private fun monthsAgo(months: Int): Long =
        (now - (months * daysPerMonth * day).toLong()).toLong()

    @Test
    fun checkup_keys_surface_in_combined_catalog() {
        val keys = ScreeningCatalog.combined.map { it.key }.toSet()
        for (key in listOf(
            "periodic_health_exam", "blood_pressure_check", "dental_checkup",
            "eye_exam", "skin_exam", "hearing_test",
        )) {
            assertTrue("$key should be in combined catalog", key in keys)
        }
    }

    @Test
    fun every_checkup_entry_carries_a_citation() {
        for (entry in ScreeningCatalog.checkups) {
            assertTrue("${entry.key} should ship a citation", entry.citation.isNotBlank())
        }
    }

    @Test
    fun recurring_checkup_still_reads_DueNow_when_overdue() {
        // The periodic wellness visit is RECURRING — it keeps the normal
        // due/overdue behaviour, unlike the min-interval checkups.
        val exam = ScreeningCatalog.checkups.first { it.key == "periodic_health_exam" }  // 36mo
        assertEquals(CadenceKind.RECURRING, exam.cadenceKind)
        val latest = ScreeningEntry(screeningKey = exam.key, performedDate = monthsAgo(48))
        val status = ScreeningCadenceEngine.evaluate(
            entry = exam,
            demographics = OwnerDemographics(ageYears = 28),
            latest = latest,
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
    }

    @Test
    fun min_interval_checkup_reads_WithinInterval_before_the_delay_elapses() {
        val dental = ScreeningCatalog.checkups.first { it.key == "dental_checkup" }  // 6mo min-interval
        assertEquals(CadenceKind.MIN_INTERVAL_SINCE_LAST, dental.cadenceKind)
        val latest = ScreeningEntry(screeningKey = dental.key, performedDate = monthsAgo(2))
        val status = ScreeningCadenceEngine.evaluate(
            entry = dental,
            demographics = OwnerDemographics(ageYears = 28),
            latest = latest,
            now = now,
        )
        assertTrue("expected WithinInterval, got $status", status is ScreeningStatus.WithinInterval)
        status as ScreeningStatus.WithinInterval
        assertTrue("monthsUntilEligible ~4, got ${status.monthsUntilEligible}", status.monthsUntilEligible in 3..4)
    }

    @Test
    fun min_interval_checkup_reads_IntervalElapsed_after_the_delay_but_never_DueNow() {
        val dental = ScreeningCatalog.checkups.first { it.key == "dental_checkup" }  // 6mo min-interval
        val latest = ScreeningEntry(screeningKey = dental.key, performedDate = monthsAgo(10))
        val status = ScreeningCadenceEngine.evaluate(
            entry = dental,
            demographics = OwnerDemographics(ageYears = 28),
            latest = latest,
            now = now,
        )
        // Manifesto no-shame rule: a recommended delay you can't be "late"
        // for never escalates to the overdue DueNow state.
        assertFalse("min-interval checkup must never read DueNow", status is ScreeningStatus.DueNow)
        assertTrue("expected IntervalElapsed, got $status", status is ScreeningStatus.IntervalElapsed)
    }

    @Test
    fun min_interval_checkup_with_no_record_is_NoRecord() {
        val eye = ScreeningCatalog.checkups.first { it.key == "eye_exam" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = eye,
            demographics = OwnerDemographics(ageYears = 28),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }
}
