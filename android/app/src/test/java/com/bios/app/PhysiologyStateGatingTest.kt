package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.physiology.PhysiologyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the [com.bios.app.alerts.ConditionPattern.excludedStates]
 * gating mechanism (#159, audit gap §2.7).
 *
 * AnomalyDetector filters [ConditionPatterns.all] by
 * `state !in pattern.excludedStates` before evaluating. The test
 * verifies the filter against the wired-up pattern (cardiovascular_stress)
 * and pins the default-empty contract for the rest of the library.
 */
class PhysiologyStateGatingTest {

    // -- enum surface --

    @Test
    fun pregnancy_set_covers_all_three_trimesters() {
        assertEquals(
            setOf(PhysiologyState.PREGNANCY_T1, PhysiologyState.PREGNANCY_T2, PhysiologyState.PREGNANCY_T3),
            PhysiologyState.PREGNANCY,
        )
    }

    @Test
    fun standard_is_the_default_value() {
        // The default of OwnerDemographics / store / detector is STANDARD;
        // the enum ordering puts STANDARD first which keeps that intuitive.
        assertEquals(PhysiologyState.STANDARD, PhysiologyState.entries.first())
    }

    // -- gating filter --

    private fun applicable(state: PhysiologyState) =
        ConditionPatterns.all.filter { state !in it.excludedStates }

    @Test
    fun standard_state_includes_every_pattern_in_the_library() {
        val applicable = applicable(PhysiologyState.STANDARD)
        assertEquals(ConditionPatterns.all.size, applicable.size)
    }

    @Test
    fun cardiovascular_stress_is_suppressed_in_every_pregnancy_trimester() {
        // The wired example: RHR rises 10–20 bpm in pregnancy → the trend
        // pattern would false-fire. Same for postpartum + athletes.
        for (state in PhysiologyState.PREGNANCY) {
            val applicable = applicable(state)
            assertFalse(
                "cardiovascular_stress should be excluded in $state",
                applicable.any { it.id == "cardiovascular_stress" }
            )
        }
    }

    @Test
    fun cardiovascular_stress_is_also_suppressed_postpartum_and_for_athletes() {
        for (state in listOf(PhysiologyState.POSTPARTUM, PhysiologyState.ATHLETE_HIGH_FITNESS)) {
            assertFalse(
                "cardiovascular_stress should be excluded in $state",
                applicable(state).any { it.id == "cardiovascular_stress" }
            )
        }
    }

    @Test
    fun cardiovascular_stress_still_fires_in_standard_and_paediatric() {
        // Paediatric doesn't have a known normative RHR-elevation pattern in
        // this library yet — the cardiovascular-stress signal still applies.
        // (Paediatric threshold-modifier work belongs to a future PR.)
        // FRAILTY_FLAG is now excluded — see frailty_excludes_baseline_deviation_patterns.
        for (state in listOf(PhysiologyState.STANDARD, PhysiologyState.PAEDIATRIC)) {
            assertTrue(
                "cardiovascular_stress should fire in $state",
                applicable(state).any { it.id == "cardiovascular_stress" }
            )
        }
    }

    @Test
    fun frailty_excludes_baseline_deviation_patterns() {
        // Wired by issue #186 per GERIATRICS_PALLIATIVE_POV.md §2.1 (Fried
        // 2001 phenotype; Morley 2012 FRAIL questionnaire). The four patterns
        // calibrated to a stable-adult baseline false-fire in the frail >75
        // cohort whose baseline encodes deconditioning by definition.
        val applicable = applicable(PhysiologyState.FRAILTY_FLAG).map { it.id }.toSet()
        for (id in listOf(
            "sleep_disruption",
            "cardiovascular_stress",
            "cardiorespiratory_deconditioning",
            "recovery_deficit",
        )) {
            assertFalse(
                "$id should be excluded under FRAILTY_FLAG",
                id in applicable,
            )
        }
    }

    @Test
    fun frailty_does_not_suppress_unrelated_patterns() {
        // The frailty gate is targeted at four specific baseline-deviation
        // patterns. Infection onset, biomarker patterns, and emergency
        // vital cutoffs must still fire in the frail cohort — frail elders
        // get infections and have heart attacks too.
        val applicable = applicable(PhysiologyState.FRAILTY_FLAG).map { it.id }.toSet()
        assertTrue("infection_onset should fire under FRAILTY_FLAG", "infection_onset" in applicable)
    }

    @Test
    fun only_explicitly_documented_patterns_declare_excludedStates() {
        // Pins the wired-pattern set so future additions are explicit. The
        // gating currently covers:
        //   - cardiovascular_stress: RHR-elevation is normative in pregnancy,
        //     postpartum, and endurance-athlete physiology; also frail >75
        //     (#186) whose baseline encodes deconditioning
        //   - sleep_disruption / cardiorespiratory_deconditioning /
        //     recovery_deficit (#186): same frailty rationale
        //   - sepsis_screen (#182): NEWS2 thresholds are validated for adults
        //     only — paediatric early-warning scores (PEWS) use age-banded
        //     bands that don't map onto NEWS2 components
        // When a new pattern lands here, add its id to the expected map below
        // so the gating contract stays visible.
        val expected = mapOf(
            "sleep_disruption" to setOf(PhysiologyState.FRAILTY_FLAG),
            "cardiovascular_stress" to setOf(
                PhysiologyState.PREGNANCY_T1,
                PhysiologyState.PREGNANCY_T2,
                PhysiologyState.PREGNANCY_T3,
                PhysiologyState.POSTPARTUM,
                PhysiologyState.ATHLETE_HIGH_FITNESS,
                PhysiologyState.FRAILTY_FLAG,
            ),
            "cardiorespiratory_deconditioning" to setOf(PhysiologyState.FRAILTY_FLAG),
            "recovery_deficit" to setOf(PhysiologyState.FRAILTY_FLAG),
            "sepsis_screen" to setOf(PhysiologyState.PAEDIATRIC),
        )
        val wired = ConditionPatterns.all
            .filter { it.excludedStates.isNotEmpty() }
            .associate { it.id to it.excludedStates }
        assertEquals(
            "Patterns with excludedStates drifted from the documented set",
            expected, wired,
        )
    }
}
