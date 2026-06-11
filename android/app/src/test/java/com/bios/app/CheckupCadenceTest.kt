package com.bios.app

import com.bios.app.model.ScreeningEntry
import com.bios.app.screening.CadenceKind
import com.bios.app.screening.CheckupHealthDataLink
import com.bios.app.screening.OwnerDemographics
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningStatus
import com.bios.contracts.MetricType
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

    @Test
    fun checkup_below_min_age_is_NotEligible() {
        // Hearing check is gated to 50+; a 28-yo is below the band and the
        // engine must exclude it regardless of record state (#402).
        val hearing = ScreeningCatalog.checkups.first { it.key == "hearing_test" }
        assertEquals(50, hearing.minAge)
        val status = ScreeningCadenceEngine.evaluate(
            entry = hearing,
            demographics = OwnerDemographics(ageYears = 28),
            latest = null,
            now = now,
        )
        assertTrue("28yo should be NotEligible for a 50+ check, got $status", status is ScreeningStatus.NotEligible)
    }

    @Test
    fun recurring_checkup_within_window_reads_Current() {
        // Periodic exam is RECURRING at 36mo; performed 12mo ago is well
        // inside the window, so it reads Current — not DueNow (#402).
        val exam = ScreeningCatalog.checkups.first { it.key == "periodic_health_exam" }
        assertEquals(CadenceKind.RECURRING, exam.cadenceKind)
        val latest = ScreeningEntry(screeningKey = exam.key, performedDate = monthsAgo(12))
        val status = ScreeningCadenceEngine.evaluate(
            entry = exam,
            demographics = OwnerDemographics(ageYears = 28),
            latest = latest,
            now = now,
        )
        assertTrue("expected Current, got $status", status is ScreeningStatus.Current)
    }

    @Test
    fun blood_pressure_check_links_to_the_bp_metric_keys() {
        // The health-data link is what stops blood_pressure_check showing
        // "No record" when Bios already holds a BP reading (#400).
        val linked = CheckupHealthDataLink.metricKeysByScreeningKey["blood_pressure_check"]
        assertTrue("blood_pressure_check should link to the systolic metric key",
            linked?.contains(MetricType.BLOOD_PRESSURE_SYSTOLIC.key) == true)
        assertTrue("blood_pressure_check should link to the diastolic metric key",
            linked?.contains(MetricType.BLOOD_PRESSURE_DIASTOLIC.key) == true)
    }

    @Test
    fun lab_backed_screenings_link_to_their_biomarker_metric_keys() {
        // An imported / manually-entered biomarker reading is the screening,
        // so the owner never re-logs a lab Bios already holds.
        val links = CheckupHealthDataLink.metricKeysByScreeningKey
        assertEquals(listOf(MetricType.HBA1C.key), links["hba1c"])
        assertEquals(listOf(MetricType.LIPOPROTEIN_A.key), links["lpa_one_time"])
        // A lipid panel is satisfied by any of its component analytes.
        val lipid = links["lipid_panel"].orEmpty()
        for (component in listOf(
            MetricType.TOTAL_CHOLESTEROL.key, MetricType.LDL_CHOLESTEROL.key,
            MetricType.HDL_CHOLESTEROL.key, MetricType.TRIGLYCERIDES.key,
        )) {
            assertTrue("lipid_panel should link to $component", component in lipid)
        }
    }

    @Test
    fun every_linked_screening_key_exists_in_the_catalog() {
        // A link to a key no catalog defines would silently never fire;
        // guard against that drift.
        val catalogKeys = ScreeningCatalog.combined.map { it.key }.toSet()
        for (screeningKey in CheckupHealthDataLink.metricKeysByScreeningKey.keys) {
            assertTrue("$screeningKey is linked but not in the catalog", screeningKey in catalogKeys)
        }
    }
}
