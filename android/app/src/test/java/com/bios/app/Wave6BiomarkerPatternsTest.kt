package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.alerts.Wave6BiomarkerPatterns
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wave-6 biomarker condition patterns: the renal-function-decline
 * and hepatic-injury screens. Same shape contract as Wave 5: lab anchors
 * via `required = true` + absolute thresholds, literature citations per
 * rule, ADVISORY tier.
 */
class Wave6BiomarkerPatternsTest {

    @Test
    fun wave6_patterns_are_registered_in_global_all_list() {
        assertTrue(Wave6BiomarkerPatterns.renalFunctionDeclineScreen in ConditionPatterns.all)
        assertTrue(Wave6BiomarkerPatterns.hepaticInjuryScreen in ConditionPatterns.all)
    }

    @Test
    fun every_wave6_pattern_carries_a_literature_citation_per_rule() {
        for (pattern in Wave6BiomarkerPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source,
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank(),
                )
            }
        }
    }

    // -- renal_function_decline_screen --

    @Test
    fun renal_decline_screen_gates_on_eGFR_below_60() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.EGFR }
        assertTrue("eGFR rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(60.0, rule.absoluteBelow!!, 1e-9)
    }

    @Test
    fun renal_decline_screen_requires_creatinine_corroborator() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.CREATININE }
        assertTrue("creatinine corroborator should be required for the screening tier", rule.required)
        assertEquals(1.3, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun renal_decline_screen_offers_urate_optional_corroborator() {
        val rule = Wave6BiomarkerPatterns.renalFunctionDeclineScreen.signalRules
            .first { it.metricType == MetricType.URIC_ACID }
        assertFalse("urate corroborator should be optional", rule.required)
        // 7.0 mg/dL is the universal-male clinical cutoff for hyperuricaemia,
        // equivalent to the ~420 µmol/L value cited in the SI literature.
        assertEquals(7.0, rule.absoluteAbove!!, 1e-9)
    }

    // -- hepatic_injury_screen --

    @Test
    fun hepatic_injury_screen_gates_on_ALT_above_3x_ULN() {
        val rule = Wave6BiomarkerPatterns.hepaticInjuryScreen.signalRules
            .first { it.metricType == MetricType.ALT }
        assertTrue("ALT rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(100.0, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun hepatic_injury_screen_carries_AST_GGT_bilirubin_albumin_corroborators() {
        val pattern = Wave6BiomarkerPatterns.hepaticInjuryScreen
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.AST in corroborators)
        assertTrue(MetricType.GGT in corroborators)
        assertTrue(MetricType.BILIRUBIN_TOTAL in corroborators)
        assertTrue(MetricType.ALBUMIN in corroborators)
    }

    @Test
    fun hepatic_injury_screen_uses_clinically_conventional_us_thresholds() {
        val rules = Wave6BiomarkerPatterns.hepaticInjuryScreen.signalRules
            .associateBy { it.metricType }
        assertEquals(105.0, rules.getValue(MetricType.AST).absoluteAbove!!, 1e-9)
        assertEquals(120.0, rules.getValue(MetricType.GGT).absoluteAbove!!, 1e-9)
        // 2.0 mg/dL ≈ 35 µmol/L (factor 17.1)
        assertEquals(2.0, rules.getValue(MetricType.BILIRUBIN_TOTAL).absoluteAbove!!, 1e-9)
        // 3.5 g/dL = 35 g/L (factor 10)
        assertEquals(3.5, rules.getValue(MetricType.ALBUMIN).absoluteBelow!!, 1e-9)
    }
}
