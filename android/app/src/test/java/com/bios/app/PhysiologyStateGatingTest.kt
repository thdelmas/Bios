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
    fun cardiovascular_stress_still_fires_in_standard_and_frailty_and_paediatric() {
        // Frailty and paediatric don't have a known normative RHR-elevation
        // pattern — the cardiovascular-stress signal still applies. (Paediatric
        // threshold-modifier work belongs to a future PR.)
        for (state in listOf(PhysiologyState.STANDARD, PhysiologyState.FRAILTY_FLAG, PhysiologyState.PAEDIATRIC)) {
            assertTrue(
                "cardiovascular_stress should fire in $state",
                applicable(state).any { it.id == "cardiovascular_stress" }
            )
        }
    }

    @Test
    fun only_explicitly_documented_patterns_declare_excludedStates() {
        // Pins the wired-pattern set so future additions are explicit. Three
        // patterns currently use the gating:
        //   - cardiovascular_stress: RHR-elevation is normative in pregnancy,
        //     postpartum, and endurance-athlete physiology
        //   - sepsis_screen (#182): NEWS2 thresholds are validated for adults
        //     only — paediatric early-warning scores (PEWS) use age-banded
        //     bands that don't map onto NEWS2 components
        //   - ciguatera_suggestive (#196): the bradycardia gate (RHR <50 bpm
        //     sustained over 48h) would false-fire on endurance athletes
        //     whose baseline RHR is already below the threshold
        // When a new pattern lands here, add its id to the expected map below
        // so the gating contract stays visible.
        val expected = mapOf(
            "cardiovascular_stress" to setOf(
                PhysiologyState.PREGNANCY_T1,
                PhysiologyState.PREGNANCY_T2,
                PhysiologyState.PREGNANCY_T3,
                PhysiologyState.POSTPARTUM,
                PhysiologyState.ATHLETE_HIGH_FITNESS,
            ),
            "sepsis_screen" to setOf(PhysiologyState.PAEDIATRIC),
            "ciguatera_suggestive" to setOf(PhysiologyState.ATHLETE_HIGH_FITNESS),
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
