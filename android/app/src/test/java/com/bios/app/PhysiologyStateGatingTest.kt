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
    fun standard_state_includes_every_non_pregnancy_pattern_in_the_library() {
        // Before #189 every pattern was active in STANDARD. With the
        // pregnancy-specific PregnancyPatterns landing, the four pregnancy /
        // postpartum screens (gestational HTN, severe-range PEC, postpartum
        // PEC, PPCM) are scoped *only* to their respective physiology
        // states — STANDARD should see every other pattern but not those
        // four. Pin the exact set so future additions stay explicit.
        val stateGated = setOf(
            "gestational_hypertension_screen",
            "severe_range_preeclampsia_screen",
            "postpartum_pre_eclampsia_screen",
            "peripartum_cardiomyopathy_screen",
            // Paediatric-only growth screen (#199) — STANDARD excluded.
            "failure_to_thrive_screen",
        )
        val applicable = applicable(PhysiologyState.STANDARD).map { it.id }.toSet()
        val expected = ConditionPatterns.all.map { it.id }.toSet() - stateGated
        assertEquals(expected, applicable)
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
        // See the comprehensive gating-contract doc in this PR's body and the
        // companion patterns; the expected map below pins the contract.
        // Includes ciguatera_suggestive (#196): the bradycardia gate (RHR <50 bpm
        // sustained 48h) would false-fire on endurance athletes whose baseline
        // RHR is already below the threshold.
        val pregnancyAndPostpartum = setOf(
            PhysiologyState.PREGNANCY_T1,
            PhysiologyState.PREGNANCY_T2,
            PhysiologyState.PREGNANCY_T3,
            PhysiologyState.POSTPARTUM,
        )
        val pregnancyPostpartumFrailty = pregnancyAndPostpartum + PhysiologyState.FRAILTY_FLAG
        val cardiovascularStressGate = pregnancyAndPostpartum +
            setOf(PhysiologyState.ATHLETE_HIGH_FITNESS, PhysiologyState.FRAILTY_FLAG)
        val allStates = PhysiologyState.entries.toSet()
        val outsidePregnancyAndPostpartum = allStates - pregnancyAndPostpartum
        val outsidePostpartum = allStates - setOf(PhysiologyState.POSTPARTUM)
        val outsideT2T3 = allStates -
            setOf(PhysiologyState.PREGNANCY_T2, PhysiologyState.PREGNANCY_T3)

        val expected = mapOf(
            "infection_onset" to pregnancyAndPostpartum,
            "sleep_disruption" to pregnancyPostpartumFrailty,
            "cardiovascular_stress" to cardiovascularStressGate,
            "overtraining" to pregnancyAndPostpartum,
            "metabolic_drift" to pregnancyAndPostpartum,
            "cardiorespiratory_deconditioning" to pregnancyPostpartumFrailty,
            "chronic_inflammation" to pregnancyAndPostpartum,
            "recovery_deficit" to pregnancyPostpartumFrailty,
            "menstrual_cycle_anomaly" to pregnancyAndPostpartum,
            "sepsis_screen" to setOf(PhysiologyState.PAEDIATRIC),
            "dka_screen" to setOf(PhysiologyState.PAEDIATRIC),
            "ciguatera_suggestive" to setOf(PhysiologyState.ATHLETE_HIGH_FITNESS),
            "gestational_hypertension_screen" to outsideT2T3,
            "severe_range_preeclampsia_screen" to outsidePregnancyAndPostpartum,
            "postpartum_pre_eclampsia_screen" to outsidePostpartum,
            "peripartum_cardiomyopathy_screen" to outsidePostpartum,
            // Paediatric growth + body composition (#199)
            "failure_to_thrive_screen" to (allStates - setOf(PhysiologyState.PAEDIATRIC)),
            "sarcopenia_trajectory_screen" to setOf(PhysiologyState.PAEDIATRIC),
            "cachexia_screen" to setOf(PhysiologyState.PAEDIATRIC),
        )
        val wired = ConditionPatterns.all
            .filter { it.excludedStates.isNotEmpty() }
            .associate { it.id to it.excludedStates }
        assertEquals(
            "Patterns with excludedStates drifted from the documented set",
            expected, wired,
        )
    }

    @Test
    fun mental_health_correlate_still_fires_in_pregnancy_and_postpartum() {
        // Perinatal depression IS a real condition — the gating sweep in
        // #189 explicitly preserves this pattern in PREGNANCY_T1/T2/T3 and
        // POSTPARTUM. Guards against future regressions that "tidy up" the
        // gating cohort and accidentally suppress it.
        for (state in PhysiologyState.PREGNANCY + setOf(PhysiologyState.POSTPARTUM)) {
            assertTrue(
                "mental_health_correlate should fire in $state",
                applicable(state).any { it.id == "mental_health_correlate" },
            )
        }
    }

    @Test
    fun infection_onset_is_suppressed_in_pregnancy_t2() {
        // Synthetic check: the pregnancy gating sweep (#189) suppresses
        // the broad infection-onset trend pattern in PREGNANCY_T2 because
        // RHR rises 10–20 bpm by Q2 and would false-fire continuously.
        // Acute-infection coverage remains via SepsisScreenPattern and
        // respiratoryInfection.
        assertFalse(
            applicable(PhysiologyState.PREGNANCY_T2).any { it.id == "infection_onset" },
        )
    }
}
