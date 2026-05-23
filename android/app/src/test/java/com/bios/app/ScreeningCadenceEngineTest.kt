package com.bios.app

import com.bios.app.model.RiskProfile
import com.bios.app.model.ScreeningEntry
import com.bios.app.screening.Applicability
import com.bios.app.screening.OwnerDemographics
import com.bios.app.screening.RiskGate
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningCatalogEntry
import com.bios.app.screening.ScreeningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the screening-cadence engine (#155). The engine is pure — `now`
 * is a parameter, no DB, no clock side effects — so the tests pin time
 * with concrete epoch values and exercise every status transition.
 */
class ScreeningCadenceEngineTest {

    // 2026-05-21 (matches the audit date in docs/audits) → 1747824000000
    private val now: Long = 1_747_824_000_000L
    private val day = 86_400_000L
    private val daysPerMonth = 30.4375  // matches the engine's constant

    private fun monthsAgo(months: Int): Long =
        (now - (months * daysPerMonth * day).toLong()).toLong()

    // -- catalog shape contracts --

    @Test
    fun catalog_has_no_duplicate_keys() {
        val keys = ScreeningCatalog.uspstf.map { it.key }
        assertEquals(
            "Duplicate screening keys: ${keys - keys.toSet()}",
            keys.size, keys.toSet().size
        )
    }

    @Test
    fun catalog_covers_the_canonical_USPSTF_adult_set() {
        val keys = ScreeningCatalog.uspstf.map { it.key }.toSet()
        // High-impact screenings the audit issue explicitly names.
        assertTrue("colorectal_cancer should be in catalog", "colorectal_cancer" in keys)
        assertTrue("mammogram should be in catalog", "mammogram" in keys)
        assertTrue("cervical_cancer should be in catalog", "cervical_cancer" in keys)
        assertTrue("aaa_one_time should be in catalog", "aaa_one_time" in keys)
        assertTrue("lipid_panel should be in catalog", "lipid_panel" in keys)
        assertTrue("hba1c should be in catalog", "hba1c" in keys)
        assertTrue("dexa_bone_density should be in catalog", "dexa_bone_density" in keys)
    }

    @Test
    fun every_catalog_entry_carries_a_citation() {
        for (entry in ScreeningCatalog.uspstf) {
            assertTrue(
                "${entry.key} should ship a citation",
                entry.citation.isNotBlank()
            )
        }
    }

    // -- age-gate transitions --

    @Test
    fun below_age_floor_returns_NotEligible() {
        val colorectal = ScreeningCatalog.uspstf.first { it.key == "colorectal_cancer" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = colorectal,
            demographics = OwnerDemographics(ageYears = 30),
            latest = null,
            now = now,
        )
        assertTrue(status is ScreeningStatus.NotEligible)
    }

    @Test
    fun above_age_ceiling_returns_NotEligible() {
        val colorectal = ScreeningCatalog.uspstf.first { it.key == "colorectal_cancer" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = colorectal,
            demographics = OwnerDemographics(ageYears = 80),
            latest = null,
            now = now,
        )
        assertTrue(status is ScreeningStatus.NotEligible)
    }

    @Test
    fun at_age_floor_passes_age_gate() {
        val colorectal = ScreeningCatalog.uspstf.first { it.key == "colorectal_cancer" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = colorectal,
            demographics = OwnerDemographics(ageYears = 45),
            latest = null,
            now = now,
        )
        // No history yet at the floor → NoRecord, not NotEligible.
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    // -- applicability gate --

    @Test
    fun anatomy_specific_screening_is_NotEligible_when_owner_presents_differently() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_MALE),
            latest = null,
            now = now,
        )
        assertTrue(status is ScreeningStatus.NotEligible)
    }

    @Test
    fun anatomy_specific_screening_passes_when_presentsAs_matches() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_FEMALE),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    @Test
    fun anatomy_specific_screening_passes_when_presentsAs_is_null() {
        // Owner hasn't declared anatomy presentation — render the recommendation
        // as elective; don't filter it out.
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = null),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    // -- cadence math --

    @Test
    fun recent_screening_renders_as_Current_with_months_until_due() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }  // 24mo cadence
        val latest = ScreeningEntry(
            screeningKey = "mammogram",
            performedDate = monthsAgo(6),
        )
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_FEMALE),
            latest = latest,
            now = now,
        )
        assertTrue("expected Current, got $status", status is ScreeningStatus.Current)
        status as ScreeningStatus.Current
        // daysPerMonth round-trip drifts by 1 month at the 6-month mark
        // (truncation of 5.97 → 5); accept either reading.
        assertTrue("monthsSinceLast should be ~6, got ${status.monthsSinceLast}", status.monthsSinceLast in 5..6)
        // 24 - 6 = 18, give or take rounding around the daysPerMonth conversion.
        assertTrue("monthsUntilDue should be ~18, got ${status.monthsUntilDue}", status.monthsUntilDue in 17..18)
    }

    @Test
    fun screening_within_30_day_grace_window_renders_as_DueNow() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }  // 24mo cadence
        // Last performed 23.5 months ago → within 30-day grace window → DueNow.
        val latest = ScreeningEntry(
            screeningKey = "mammogram",
            performedDate = (now - (23.5 * daysPerMonth * day).toLong()),
        )
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_FEMALE),
            latest = latest,
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
        status as ScreeningStatus.DueNow
        assertEquals(0, status.monthsOverdue)
    }

    @Test
    fun screening_past_cadence_window_renders_as_DueNow_with_monthsOverdue() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }  // 24mo cadence
        // Last performed 30 months ago → 6 months overdue.
        val latest = ScreeningEntry(
            screeningKey = "mammogram",
            performedDate = monthsAgo(30),
        )
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_FEMALE),
            latest = latest,
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
        status as ScreeningStatus.DueNow
        assertTrue("monthsOverdue should be ~6, got ${status.monthsOverdue}", status.monthsOverdue in 5..6)
    }

    // -- evaluateAll convenience --

    @Test
    fun evaluateAll_runs_lookup_once_per_catalog_entry() {
        val lookups = mutableListOf<String>()
        val results = ScreeningCadenceEngine.evaluateAll(
            catalog = ScreeningCatalog.uspstf,
            demographics = OwnerDemographics(ageYears = 50, presentsAs = Applicability.PRESENTS_AS_FEMALE),
            latestByKey = { key ->
                lookups += key
                null
            },
            now = now,
        )
        assertEquals(ScreeningCatalog.uspstf.size, results.size)
        assertEquals(ScreeningCatalog.uspstf.size, lookups.size)
        assertEquals(ScreeningCatalog.uspstf.map { it.key }, lookups)
    }

    // -- #191 extensions: risk-gated cadences (hereditary, IHS, ever-smoker) --

    @Test
    fun hereditary_entry_is_NotEligible_when_owner_has_not_recorded_the_syndrome() {
        val lynch = ScreeningCatalog.hereditary.first { it.key == "lynch_colonoscopy" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = lynch,
            demographics = OwnerDemographics(ageYears = 30),
            latest = null,
            riskProfile = RiskProfile(),  // no syndrome flag set
            now = now,
        )
        assertTrue(status is ScreeningStatus.NotEligible)
        status as ScreeningStatus.NotEligible
        assertTrue(
            "Risk-gated reason should start with 'Requires owner-recorded': ${status.reason}",
            status.reason.startsWith("Requires owner-recorded"),
        )
    }

    @Test
    fun hereditary_entry_passes_when_owner_records_the_syndrome_flag() {
        val lynch = ScreeningCatalog.hereditary.first { it.key == "lynch_colonoscopy" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = lynch,
            demographics = OwnerDemographics(ageYears = 30),
            latest = null,
            riskProfile = RiskProfile(lynchSyndrome = true),
            now = now,
        )
        // 30y is above the floor (20), no record on file → NoRecord.
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    @Test
    fun hereditary_entry_age_band_still_applies_when_syndrome_flag_is_set() {
        val fap = ScreeningCatalog.hereditary.first { it.key == "fap_colonoscopy" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = fap,
            demographics = OwnerDemographics(ageYears = 8),  // below FAP floor of 10
            latest = null,
            riskProfile = RiskProfile(fapFamilialAdenomatousPolyposis = true),
            now = now,
        )
        assertTrue(status is ScreeningStatus.NotEligible)
        status as ScreeningStatus.NotEligible
        assertTrue(
            "Age-floor reason should mention age 10: ${status.reason}",
            status.reason.contains("10"),
        )
    }

    @Test
    fun hereditary_cadence_renders_DueNow_when_overdue() {
        val lynch = ScreeningCatalog.hereditary.first { it.key == "lynch_colonoscopy" }  // 18mo cadence
        val latest = ScreeningEntry(screeningKey = lynch.key, performedDate = monthsAgo(24))
        val status = ScreeningCadenceEngine.evaluate(
            entry = lynch,
            demographics = OwnerDemographics(ageYears = 30),
            latest = latest,
            riskProfile = RiskProfile(lynchSyndrome = true),
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
        status as ScreeningStatus.DueNow
        assertTrue("monthsOverdue ~6, got ${status.monthsOverdue}", status.monthsOverdue in 5..7)
    }

    @Test
    fun ihs_hba1c_only_surfaces_for_owners_with_diabetic_aian_flag() {
        val ihs = ScreeningCatalog.ihsPaho.first { it.key == "ihs_hba1c_diabetic_aian" }
        val withoutFlag = ScreeningCadenceEngine.evaluate(
            entry = ihs,
            demographics = OwnerDemographics(ageYears = 45),
            latest = null,
            riskProfile = RiskProfile(),
            now = now,
        )
        assertTrue(withoutFlag is ScreeningStatus.NotEligible)
        val withFlag = ScreeningCadenceEngine.evaluate(
            entry = ihs,
            demographics = OwnerDemographics(ageYears = 45),
            latest = null,
            riskProfile = RiskProfile(ihsDiabeticAianStatus = true),
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, withFlag)
    }

    @Test
    fun ihs_hba1c_renders_DueNow_at_7_months_with_6mo_cadence() {
        val ihs = ScreeningCatalog.ihsPaho.first { it.key == "ihs_hba1c_diabetic_aian" }  // 6mo cadence
        val latest = ScreeningEntry(screeningKey = ihs.key, performedDate = monthsAgo(7))
        val status = ScreeningCadenceEngine.evaluate(
            entry = ihs,
            demographics = OwnerDemographics(ageYears = 45),
            latest = latest,
            riskProfile = RiskProfile(ihsDiabeticAianStatus = true),
            now = now,
        )
        assertTrue("expected DueNow, got $status", status is ScreeningStatus.DueNow)
    }

    @Test
    fun aaa_one_time_only_surfaces_for_owners_with_any_smoking_history() {
        val aaa = ScreeningCatalog.uspstf.first { it.key == "aaa_one_time" }
        val nonSmoker = ScreeningCadenceEngine.evaluate(
            entry = aaa,
            demographics = OwnerDemographics(ageYears = 68, presentsAs = Applicability.PRESENTS_AS_MALE),
            latest = null,
            riskProfile = RiskProfile(),  // no tobacco years on file
            now = now,
        )
        assertTrue(nonSmoker is ScreeningStatus.NotEligible)
        val everSmoker = ScreeningCadenceEngine.evaluate(
            entry = aaa,
            demographics = OwnerDemographics(ageYears = 68, presentsAs = Applicability.PRESENTS_AS_MALE),
            latest = null,
            riskProfile = RiskProfile(personalTobaccoYears = 20),
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, everSmoker)
    }

    @Test
    fun lpa_once_in_lifetime_is_Current_indefinitely_after_one_record() {
        val lpa = ScreeningCatalog.cardiology.first { it.key == "lpa_one_time" }
        val latest = ScreeningEntry(screeningKey = lpa.key, performedDate = monthsAgo(120))  // 10 years ago
        val status = ScreeningCadenceEngine.evaluate(
            entry = lpa,
            demographics = OwnerDemographics(ageYears = 50),
            latest = latest,
            riskProfile = RiskProfile(),
            now = now,
        )
        // Cadence is Int.MAX_VALUE → "current" effectively forever once
        // one measurement is on file.
        assertTrue("expected Current, got $status", status is ScreeningStatus.Current)
    }

    @Test
    fun acs_mammogram_surfaces_alongside_uspstf_in_combined_catalog() {
        val keys = ScreeningCatalog.combined.map { it.key }.toSet()
        assertTrue("ACS mammogram should be in combined catalog", "acs_mammogram" in keys)
        assertTrue("USPSTF mammogram should still be in combined catalog", "mammogram" in keys)
    }

    @Test
    fun combined_catalog_has_no_duplicate_keys() {
        val keys = ScreeningCatalog.combined.map { it.key }
        assertEquals(
            "Duplicate keys in combined catalog: ${keys - keys.toSet()}",
            keys.size, keys.toSet().size,
        )
    }

    @Test
    fun every_combined_entry_carries_a_citation() {
        for (entry in ScreeningCatalog.combined) {
            assertTrue(
                "${entry.key} should ship a citation",
                entry.citation.isNotBlank(),
            )
        }
    }

    @Test
    fun every_hereditary_entry_is_risk_gated() {
        // Hereditary entries should always be syndrome-gated — surfacing
        // a non-gated NCCN cadence to non-carriers would be noise.
        for (entry in ScreeningCatalog.hereditary) {
            assertTrue(
                "${entry.key} should carry a non-NONE riskGate",
                entry.riskGate != RiskGate.NONE,
            )
        }
    }

    @Test
    fun ever_smoker_gate_treats_packYears_as_smoking_history() {
        assertTrue(
            ScreeningCadenceEngine.satisfiesRiskGate(
                RiskGate.EVER_SMOKER,
                RiskProfile(personalTobaccoPackYears = 10),
            )
        )
        assertFalse(
            ScreeningCadenceEngine.satisfiesRiskGate(
                RiskGate.EVER_SMOKER,
                RiskProfile(),
            )
        )
    }

    @Test
    fun risk_gate_is_unsatisfied_when_profile_is_null() {
        // No risk-profile row on disk yet → every gated entry hidden.
        assertFalse(ScreeningCadenceEngine.satisfiesRiskGate(RiskGate.LYNCH_SYNDROME, null))
        assertFalse(ScreeningCadenceEngine.satisfiesRiskGate(RiskGate.IHS_DIABETIC_AIAN, null))
        // Non-gated entries pass through regardless.
        assertTrue(ScreeningCadenceEngine.satisfiesRiskGate(RiskGate.NONE, null))
    }
}
