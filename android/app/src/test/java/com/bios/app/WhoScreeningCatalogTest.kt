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
 * Guards the WHO globally-scoped catalog (maximum-coverage follow-up) and
 * its interaction with [ScreeningCadenceEngine]. Time is pinned the same
 * way as the sibling screening suites: `now` is a concrete epoch and the
 * engine takes it as a parameter.
 */
class WhoScreeningCatalogTest {

    // 2026-05-21 → 1747824000000 (matches ScreeningCadenceEngineTest).
    private val now: Long = 1_747_824_000_000L
    private val day = 86_400_000L
    private val daysPerMonth = 30.4375  // matches the engine's constant

    private fun monthsAgo(months: Int): Long =
        (now - (months * daysPerMonth * day).toLong()).toLong()

    @Test
    fun who_keys_surface_in_combined_catalog() {
        val keys = ScreeningCatalog.combined.map { it.key }.toSet()
        for (key in listOf(
            "who_cvd_risk_assessment", "hiv_test", "hepatitis_b_test", "hepatitis_c_test",
        )) {
            assertTrue("$key should be in combined catalog", key in keys)
        }
    }

    @Test
    fun every_who_entry_carries_a_citation() {
        for (entry in ScreeningCatalog.who) {
            assertTrue("${entry.key} should ship a citation", entry.citation.isNotBlank())
        }
    }

    @Test
    fun cvd_risk_assessment_below_min_age_is_NotEligible() {
        // WHO total-risk assessment is gated to adults >40; a 28-yo is
        // below the band and the engine must exclude it.
        val cvd = ScreeningCatalog.who.first { it.key == "who_cvd_risk_assessment" }
        assertEquals(40, cvd.minAge)
        val status = ScreeningCadenceEngine.evaluate(
            entry = cvd,
            demographics = OwnerDemographics(ageYears = 28),
            latest = null,
            now = now,
        )
        assertTrue("28yo should be NotEligible for a 40+ check, got $status", status is ScreeningStatus.NotEligible)
    }

    @Test
    fun cvd_risk_assessment_eligible_with_no_record_is_NoRecord() {
        val cvd = ScreeningCatalog.who.first { it.key == "who_cvd_risk_assessment" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = cvd,
            demographics = OwnerDemographics(ageYears = 45),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    @Test
    fun cvd_risk_assessment_recurring_reads_DueNow_when_overdue() {
        // It's RECURRING at 12mo, so a 24-mo-old record reads as overdue.
        val cvd = ScreeningCatalog.who.first { it.key == "who_cvd_risk_assessment" }
        assertEquals(CadenceKind.RECURRING, cvd.cadenceKind)
        val latest = ScreeningEntry(screeningKey = cvd.key, performedDate = monthsAgo(24))
        val status = ScreeningCadenceEngine.evaluate(
            entry = cvd,
            demographics = OwnerDemographics(ageYears = 45),
            latest = latest,
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
    }

    @Test
    fun infectious_disease_tests_are_modelled_one_time() {
        // HIV / HBsAg / anti-HCV are "at least once" — the engine models
        // that as a sentinel Int.MAX_VALUE cadence (matching lpa_one_time).
        for (key in listOf("hiv_test", "hepatitis_b_test", "hepatitis_c_test")) {
            val entry = ScreeningCatalog.who.first { it.key == key }
            assertEquals("$key should be one-time", Int.MAX_VALUE, entry.cadenceMonths)
        }
    }

    @Test
    fun hiv_test_with_no_record_is_NoRecord_and_never_nags() {
        // A fresh install shows "no record yet", not an overdue nag — so it
        // never contributes to the passive due badge (DueNow only).
        val hiv = ScreeningCatalog.who.first { it.key == "hiv_test" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = hiv,
            demographics = OwnerDemographics(ageYears = 28),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
        assertFalse("one-time test with no record must never read DueNow", status is ScreeningStatus.DueNow)
    }

    @Test
    fun hiv_test_with_a_record_reads_Current_indefinitely_not_DueNow() {
        // Once recorded, a one-time test stays current forever — it must
        // never escalate to the overdue DueNow state.
        val hiv = ScreeningCatalog.who.first { it.key == "hiv_test" }
        val latest = ScreeningEntry(screeningKey = hiv.key, performedDate = monthsAgo(60))
        val status = ScreeningCadenceEngine.evaluate(
            entry = hiv,
            demographics = OwnerDemographics(ageYears = 28),
            latest = latest,
            now = now,
        )
        assertTrue("expected Current, got $status", status is ScreeningStatus.Current)
        assertFalse("one-time test must never read DueNow", status is ScreeningStatus.DueNow)
    }
}
