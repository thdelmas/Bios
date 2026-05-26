package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.PregnancyPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pregnancy- and postpartum-specific patterns from issue #189
 * (OBGYN_POV §2.1–2.3, with converging support from Primary Care and the
 * Cardiology peripartum-cardiomyopathy lens).
 *
 * Three preeclampsia sub-shapes + a postpartum PPCM screen are tested:
 *
 *  - [PregnancyPatterns.gestationalHypertensionScreen] — 7-day median
 *    ≥140/90 across ≥3 readings, gated to T2/T3.
 *  - [PregnancyPatterns.severeRangePreeclampsiaScreen] — single reading
 *    ≥160/110, URGENT-floor, active in every pregnancy + postpartum
 *    state.
 *  - [PregnancyPatterns.postpartumPreEclampsiaScreen] — same threshold,
 *    POSTPARTUM-only.
 *  - [PregnancyPatterns.peripartumCardiomyopathyScreen] — tachycardia +
 *    activity drop + sleep disruption, POSTPARTUM-only, ADVISORY tier.
 */
class PregnancyPatternsTest {

    // -- registration --

    @Test
    fun all_pregnancy_patterns_register_in_global_all_list() {
        assertTrue(PregnancyPatterns.gestationalHypertensionScreen in ConditionPatterns.all)
        assertTrue(PregnancyPatterns.severeRangePreeclampsiaScreen in ConditionPatterns.all)
        assertTrue(PregnancyPatterns.postpartumPreEclampsiaScreen in ConditionPatterns.all)
        assertTrue(PregnancyPatterns.peripartumCardiomyopathyScreen in ConditionPatterns.all)
    }

    // -- gestational_hypertension_screen --

    @Test
    fun gestational_hypertension_uses_7d_median_with_3_reading_floor() {
        val pattern = PregnancyPatterns.gestationalHypertensionScreen
        val sbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertTrue(sbp.isAbsolute)
        assertEquals(140.0, sbp.absoluteAbove!!, 1e-9)
        assertEquals(168, sbp.absoluteWindowHours)
        assertEquals(3, sbp.absoluteMinReadings)
        assertEquals(ThresholdSource.LITERATURE, sbp.source)
        assertFalse(sbp.required)

        val dbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC }
        assertTrue(dbp.isAbsolute)
        assertEquals(90.0, dbp.absoluteAbove!!, 1e-9)
        assertEquals(168, dbp.absoluteWindowHours)
        assertEquals(3, dbp.absoluteMinReadings)
        assertFalse(dbp.required)
    }

    @Test
    fun gestational_hypertension_is_only_active_in_T2_and_T3() {
        val excluded = PregnancyPatterns.gestationalHypertensionScreen.excludedStates
        // T1 + STANDARD + every non-pregnancy state should be excluded.
        for (state in PhysiologyState.entries) {
            val shouldBeActive = state == PhysiologyState.PREGNANCY_T2 ||
                state == PhysiologyState.PREGNANCY_T3
            assertEquals(
                "gestational_hypertension_screen active-state for $state",
                !shouldBeActive,
                state in excluded,
            )
        }
    }

    @Test
    fun gestational_hypertension_uses_OR_semantics() {
        // Either SBP ≥140 OR DBP ≥90 fires the screen alone — isolated
        // diastolic-onset hypertension is common in early pregnancy.
        assertEquals(1, PregnancyPatterns.gestationalHypertensionScreen.minActiveSignals)
    }

    @Test
    fun gestational_hypertension_has_no_severity_floor() {
        // Mild-range pattern; severity comes from the classifier.
        assertNull(PregnancyPatterns.gestationalHypertensionScreen.severityFloor)
    }

    // -- severe_range_preeclampsia_screen --

    @Test
    fun severe_range_uses_single_reading_160_over_110() {
        val pattern = PregnancyPatterns.severeRangePreeclampsiaScreen
        val sbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertTrue(sbp.isAbsolute)
        assertEquals(160.0, sbp.absoluteAbove!!, 1e-9)
        assertEquals("Single-reading semantics", 0, sbp.absoluteWindowHours)

        val dbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC }
        assertTrue(dbp.isAbsolute)
        assertEquals(110.0, dbp.absoluteAbove!!, 1e-9)
        assertEquals(0, dbp.absoluteWindowHours)
    }

    @Test
    fun severe_range_declares_URGENT_severity_floor() {
        assertEquals(
            AlertTier.URGENT,
            PregnancyPatterns.severeRangePreeclampsiaScreen.severityFloor,
        )
    }

    @Test
    fun severe_range_is_active_in_every_pregnancy_trimester_and_postpartum() {
        val excluded = PregnancyPatterns.severeRangePreeclampsiaScreen.excludedStates
        for (state in PhysiologyState.PREGNANCY + setOf(PhysiologyState.POSTPARTUM)) {
            assertFalse("severe_range should fire in $state", state in excluded)
        }
        // Outside pregnancy / postpartum, the non-pregnant hypertensive-
        // urgency pattern (HypertensionPatterns.hypertensiveUrgency) takes
        // over.
        assertTrue(PhysiologyState.STANDARD in excluded)
        assertTrue(PhysiologyState.ATHLETE_HIGH_FITNESS in excluded)
    }

    @Test
    fun severe_range_uses_OR_semantics() {
        assertEquals(1, PregnancyPatterns.severeRangePreeclampsiaScreen.minActiveSignals)
    }

    // -- postpartum_pre_eclampsia_screen --

    @Test
    fun postpartum_screen_uses_same_160_over_110_cutoffs() {
        val pattern = PregnancyPatterns.postpartumPreEclampsiaScreen
        val sbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertEquals(160.0, sbp.absoluteAbove!!, 1e-9)
        val dbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC }
        assertEquals(110.0, dbp.absoluteAbove!!, 1e-9)
    }

    @Test
    fun postpartum_screen_is_only_active_in_postpartum() {
        val excluded = PregnancyPatterns.postpartumPreEclampsiaScreen.excludedStates
        for (state in PhysiologyState.entries) {
            assertEquals(
                "postpartum_pre_eclampsia_screen active-state for $state",
                state != PhysiologyState.POSTPARTUM,
                state in excluded,
            )
        }
    }

    @Test
    fun postpartum_screen_declares_URGENT_severity_floor() {
        assertEquals(
            AlertTier.URGENT,
            PregnancyPatterns.postpartumPreEclampsiaScreen.severityFloor,
        )
    }

    // -- peripartum_cardiomyopathy_screen --

    @Test
    fun ppcm_screen_carries_rhr_steps_and_sleep_signals() {
        val rules = PregnancyPatterns.peripartumCardiomyopathyScreen.signalRules
        assertNotNull(rules.find { it.metricType == MetricType.RESTING_HEART_RATE })
        assertNotNull(rules.find { it.metricType == MetricType.STEPS })
        assertNotNull(rules.find { it.metricType == MetricType.SLEEP_STAGE })
    }

    @Test
    fun ppcm_screen_is_only_active_in_postpartum() {
        val excluded = PregnancyPatterns.peripartumCardiomyopathyScreen.excludedStates
        for (state in PhysiologyState.entries) {
            assertEquals(
                state != PhysiologyState.POSTPARTUM,
                state in excluded,
            )
        }
    }

    @Test
    fun ppcm_screen_has_no_severity_floor_advisory_tier() {
        // ESC 2019 / Sliwa 2020: PPCM is a same-week cardiology-evaluation
        // question, not an immediate-action URGENT alert. The severe-range
        // BP patterns above carry the URGENT path; PPCM stays at the
        // classifier-default tier (typically ADVISORY).
        assertNull(PregnancyPatterns.peripartumCardiomyopathyScreen.severityFloor)
    }

    // -- citation hygiene --

    @Test
    fun preeclampsia_patterns_cite_acog_2020_and_sibai_2003() {
        for (pattern in listOf(
            PregnancyPatterns.gestationalHypertensionScreen,
            PregnancyPatterns.severeRangePreeclampsiaScreen,
            PregnancyPatterns.postpartumPreEclampsiaScreen,
        )) {
            val refs = pattern.references.joinToString("\n")
            assertTrue("${pattern.id} missing ACOG 2020", refs.contains("ACOG"))
            assertTrue("${pattern.id} missing Sibai 2003", refs.contains("Sibai"))
        }
    }

    @Test
    fun ppcm_screen_cites_sliwa_2020() {
        val refs = PregnancyPatterns.peripartumCardiomyopathyScreen.references.joinToString("\n")
        assertTrue(refs.contains("Sliwa"))
    }

    @Test
    fun every_signal_rule_is_literature_sourced_with_a_citation() {
        for (pattern in PregnancyPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source,
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    // -- AlertContentPolicy-style spot checks --
    // Global compliance is enforced by AlertContentPolicyTest; this spot-
    // check guards the most common copy-paste failure mode for the
    // preeclampsia text, which is uniquely tempting to phrase as
    // "you may have preeclampsia".

    @Test
    fun preeclampsia_text_avoids_diagnosis_language() {
        for (pattern in listOf(
            PregnancyPatterns.gestationalHypertensionScreen,
            PregnancyPatterns.severeRangePreeclampsiaScreen,
            PregnancyPatterns.postpartumPreEclampsiaScreen,
        )) {
            val combined = (pattern.title + " " + pattern.explanation + " " +
                (pattern.suggestedAction ?: "")).lowercase()
            assertFalse(
                "${pattern.id} should not claim diagnosis (you have / you may have)",
                combined.contains("you have preeclampsia") ||
                    combined.contains("you may have preeclampsia"),
            )
        }
    }

    @Test
    fun severe_range_suggested_action_references_immediate_assessment() {
        // Brief's exemplar: "Blood pressure ≥160/110 in pregnancy — seek
        // immediate medical assessment." Verify the action carries that
        // shape rather than the non-pregnant "rest and re-check" wording.
        val action = PregnancyPatterns.severeRangePreeclampsiaScreen.suggestedAction!!
        val lower = action.lowercase()
        assertTrue("Should reference immediate", lower.contains("immediate"))
        assertTrue(
            "Should reference medical assessment or emergency services",
            lower.contains("medical assessment") || lower.contains("emergency"),
        )
    }
}
