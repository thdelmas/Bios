package com.bios.app

import com.bios.app.screening.AnatomyProfile
import com.bios.app.screening.ApplicabilityResolution
import com.bios.app.screening.OwnerDemographics
import com.bios.app.screening.ScreeningCadenceEngine
import com.bios.app.screening.ScreeningCatalog
import com.bios.app.screening.ScreeningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards anatomy-direct screening routing (#209, audit gap §2.16 and
 * Indigenous Americas §2.4).
 *
 * The PreventiveCareScreen used to ask the owner to pick "female-bodied"
 * or "male-bodied," a binary that's exclusionary for trans, non-binary,
 * intersex, and post-surgical owners. The fix is anatomy-direct
 * questions: "do you have a cervix?", "do you have breast tissue?", and
 * so on. Cadence routing then runs per organ rather than per gender
 * bucket.
 *
 * These tests pin the routing rules:
 *
 * - cervix=yes → cervical_cancer becomes available
 * - breast=yes → mammogram becomes available
 * - prostate=yes → AAA recommendation becomes available
 * - anatomy unset → the entry is shown as elective (not filtered out)
 * - explicit no → the entry is filtered (NotEligible)
 */
class AnatomyRoutingTest {

    private val now: Long = 1_747_824_000_000L  // 2026-05-21

    // -- anatomy → key-level resolution --

    @Test
    fun cervix_yes_routes_cervical_cancer_to_Match() {
        val profile = AnatomyProfile(hasCervix = true)
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("cervical_cancer"))
    }

    @Test
    fun cervix_no_routes_cervical_cancer_to_NotApplicable() {
        val profile = AnatomyProfile(hasCervix = false)
        assertEquals(ApplicabilityResolution.NotApplicable, profile.resolveForKey("cervical_cancer"))
    }

    @Test
    fun cervix_unset_routes_cervical_cancer_to_Unset() {
        val profile = AnatomyProfile(hasCervix = null)
        assertEquals(ApplicabilityResolution.Unset, profile.resolveForKey("cervical_cancer"))
    }

    @Test
    fun breast_yes_routes_mammogram_to_Match() {
        val profile = AnatomyProfile(hasBreastTissue = true)
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("mammogram"))
    }

    @Test
    fun breast_no_routes_mammogram_to_NotApplicable() {
        val profile = AnatomyProfile(hasBreastTissue = false)
        assertEquals(ApplicabilityResolution.NotApplicable, profile.resolveForKey("mammogram"))
    }

    @Test
    fun prostate_yes_routes_aaa_to_Match() {
        val profile = AnatomyProfile(hasProstate = true)
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("aaa_one_time"))
    }

    @Test
    fun prostate_no_routes_aaa_to_NotApplicable() {
        val profile = AnatomyProfile(hasProstate = false)
        assertEquals(ApplicabilityResolution.NotApplicable, profile.resolveForKey("aaa_one_time"))
    }

    @Test
    fun universal_screenings_are_unaffected_by_anatomy() {
        val profile = AnatomyProfile(
            hasBreastTissue = false,
            hasCervix = false,
            hasUterus = false,
            hasProstate = false,
        )
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("colorectal_cancer"))
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("hba1c"))
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("lipid_panel"))
        assertEquals(ApplicabilityResolution.Match, profile.resolveForKey("depression_screen"))
    }

    // -- end-to-end: the cadence engine consults AnatomyProfile --

    @Test
    fun owner_with_cervix_yes_sees_cervical_cancer_NoRecord() {
        val cervical = ScreeningCatalog.uspstf.first { it.key == "cervical_cancer" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = cervical,
            demographics = OwnerDemographics(
                ageYears = 40,
                anatomy = AnatomyProfile(hasCervix = true),
            ),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    @Test
    fun owner_with_cervix_no_sees_cervical_cancer_NotEligible() {
        val cervical = ScreeningCatalog.uspstf.first { it.key == "cervical_cancer" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = cervical,
            demographics = OwnerDemographics(
                ageYears = 40,
                anatomy = AnatomyProfile(hasCervix = false),
            ),
            latest = null,
            now = now,
        )
        assertTrue("expected NotEligible, got $status", status is ScreeningStatus.NotEligible)
    }

    @Test
    fun owner_with_breast_tissue_yes_sees_mammogram_NoRecord() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(
                ageYears = 50,
                anatomy = AnatomyProfile(hasBreastTissue = true),
            ),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }

    @Test
    fun owner_with_breast_tissue_no_sees_mammogram_NotEligible() {
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(
                ageYears = 50,
                anatomy = AnatomyProfile(hasBreastTissue = false),
            ),
            latest = null,
            now = now,
        )
        assertTrue("expected NotEligible, got $status", status is ScreeningStatus.NotEligible)
    }

    @Test
    fun owner_who_has_both_anatomies_sees_both_screenings_available() {
        // A trans woman with breast tissue post-HRT and no cervix; or a
        // non-binary owner with a mixed anatomy profile. Each anatomy
        // question is independent — mammography becomes applicable from
        // breast=yes, cervical does not from cervix=no.
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val cervical = ScreeningCatalog.uspstf.first { it.key == "cervical_cancer" }

        val demographics = OwnerDemographics(
            ageYears = 50,
            anatomy = AnatomyProfile(hasBreastTissue = true, hasCervix = false),
        )
        assertEquals(
            ScreeningStatus.NoRecord,
            ScreeningCadenceEngine.evaluate(mammogram, demographics, latest = null, now = now),
        )
        assertTrue(
            ScreeningCadenceEngine.evaluate(cervical, demographics, latest = null, now = now)
                is ScreeningStatus.NotEligible,
        )
    }

    @Test
    fun all_anatomy_unset_renders_as_elective_not_filtered() {
        // Default state for a new install: owner hasn't answered. The
        // screening must show as elective rather than vanish from the UI.
        val mammogram = ScreeningCatalog.uspstf.first { it.key == "mammogram" }
        val status = ScreeningCadenceEngine.evaluate(
            entry = mammogram,
            demographics = OwnerDemographics(
                ageYears = 50,
                anatomy = AnatomyProfile(),  // all null
            ),
            latest = null,
            now = now,
        )
        assertEquals(ScreeningStatus.NoRecord, status)
    }
}
