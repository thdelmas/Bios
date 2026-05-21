package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.app.alerts.Wave5BiomarkerPatterns
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wave-5 biomarker patterns (renal / hepatic / insulin-
 * resistance) shipped in #158. Same shape contract as the wave 1-4
 * patterns in [com.bios.app.alerts.BiomarkerConditionPatterns]: lab
 * anchors via `required = true` + absolute threshold; wearable /
 * metabolic corroborators stay baseline-relative or absolute on their
 * own clinical cutoffs. Audit gap §2.8.
 */
class Wave5BiomarkerPatternsTest {

    // -- registration --

    @Test
    fun wave5_patterns_are_registered_in_global_all_list() {
        assertTrue(Wave5BiomarkerPatterns.ckdProgressionSignature in ConditionPatterns.all)
        assertTrue(Wave5BiomarkerPatterns.nafldSignature in ConditionPatterns.all)
        assertTrue(Wave5BiomarkerPatterns.insulinResistanceSignature in ConditionPatterns.all)
    }

    @Test
    fun every_wave5_pattern_carries_a_literature_citation_per_rule() {
        for (pattern in Wave5BiomarkerPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source
                )
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank()
                )
            }
        }
    }

    // -- ckd_progression_signature --

    @Test
    fun ckd_signature_gates_on_eGFR_below_60() {
        val rule = Wave5BiomarkerPatterns.ckdProgressionSignature.signalRules
            .first { it.metricType == MetricType.EGFR }
        assertTrue("eGFR rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(60.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    @Test
    fun ckd_signature_carries_creatinine_hemoglobin_RHR_corroborators() {
        val pattern = Wave5BiomarkerPatterns.ckdProgressionSignature
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.CREATININE in corroborators)
        assertTrue(MetricType.HEMOGLOBIN in corroborators)
        assertTrue(MetricType.RESTING_HEART_RATE in corroborators)
    }

    @Test
    fun ckd_signature_requires_at_least_one_corroborator() {
        // eGFR alone (1 signal) won't fire; minActiveSignals=2 forces
        // creatinine, anemia-of-CKD, or RHR drift to corroborate.
        assertEquals(2, Wave5BiomarkerPatterns.ckdProgressionSignature.minActiveSignals)
    }

    // -- nafld_signature --

    @Test
    fun nafld_signature_gates_on_ALT_at_or_above_50_U_per_L() {
        val rule = Wave5BiomarkerPatterns.nafldSignature.signalRules
            .first { it.metricType == MetricType.ALT }
        assertTrue("ALT rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(50.0, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun nafld_signature_carries_AST_GGT_HbA1c_sleep_corroborators() {
        val pattern = Wave5BiomarkerPatterns.nafldSignature
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.AST in corroborators)
        assertTrue(MetricType.GGT in corroborators)
        assertTrue(MetricType.HBA1C in corroborators)
        assertTrue(MetricType.SLEEP_EFFICIENCY in corroborators)
    }

    @Test
    fun nafld_GGT_corroborator_uses_60_U_per_L_cutoff() {
        val ggt = Wave5BiomarkerPatterns.nafldSignature.signalRules
            .first { it.metricType == MetricType.GGT }
        assertFalse("GGT is corroborating, not gating", ggt.required)
        assertTrue(ggt.isAbsolute)
        assertEquals(60.0, ggt.absoluteAbove!!, 1e-9)
    }

    // -- insulin_resistance_signature --

    @Test
    fun insulin_resistance_signature_gates_on_HOMA_IR_at_or_above_2_point_5() {
        val rule = Wave5BiomarkerPatterns.insulinResistanceSignature.signalRules
            .first { it.metricType == MetricType.HOMA_IR }
        assertTrue("HOMA-IR rule should anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        assertEquals(2.5, rule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun insulin_resistance_signature_carries_glucose_CV_HbA1c_RHR_corroborators() {
        val pattern = Wave5BiomarkerPatterns.insulinResistanceSignature
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.GLUCOSE_CV in corroborators)
        assertTrue(MetricType.HBA1C in corroborators)
        assertTrue(MetricType.RESTING_HEART_RATE in corroborators)
    }

    @Test
    fun insulin_resistance_glucose_CV_corroborator_is_baseline_relative() {
        // Glucose CV is sensor-derived from CGM data, so use the standard
        // z-score machinery — not an absolute cutoff.
        val cv = Wave5BiomarkerPatterns.insulinResistanceSignature.signalRules
            .first { it.metricType == MetricType.GLUCOSE_CV }
        assertFalse(cv.isAbsolute)
        assertEquals(DeviationDirection.ABOVE, cv.direction)
        assertEquals(168, cv.minDurationHours)
    }

    @Test
    fun insulin_resistance_requires_at_least_one_corroborator() {
        assertEquals(2, Wave5BiomarkerPatterns.insulinResistanceSignature.minActiveSignals)
    }

    // -- LOINC + units --

    @Test
    fun wave5_metric_types_carry_their_clinically_conventional_units() {
        assertEquals(com.bios.contracts.MetricUnit.ML_PER_MIN_PER_173, MetricType.EGFR.unit)
        assertEquals(com.bios.contracts.MetricUnit.MG_PER_DL, MetricType.CREATININE.unit)
        assertEquals(com.bios.contracts.MetricUnit.U_PER_L, MetricType.ALT.unit)
        assertEquals(com.bios.contracts.MetricUnit.U_PER_L, MetricType.AST.unit)
        assertEquals(com.bios.contracts.MetricUnit.U_PER_L, MetricType.GGT.unit)
    }
}
