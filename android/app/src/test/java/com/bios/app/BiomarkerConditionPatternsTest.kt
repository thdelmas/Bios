package com.bios.app

import com.bios.app.alerts.BiomarkerConditionPatterns
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.SignalRule
import com.bios.app.alerts.ThresholdSource
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the absolute-threshold extension to [SignalRule] and the biomarker
 * patterns that depend on it. These patterns are how the BIOMARKER contract
 * surface earns its keep under the §8.8 acceptance rule ("each new key has
 * at least one cross-correlation use"). If they regress silently, the labs
 * stop driving alerts.
 */
class BiomarkerConditionPatternsTest {

    // -- SignalRule.isAbsolute --

    @Test
    fun isAbsolute_is_false_for_baseline_relative_rules() {
        val rule = SignalRule(
            MetricType.HEART_RATE, DeviationDirection.ABOVE,
            thresholdSigma = 1.5, minDurationHours = 24, weight = 1.0
        )
        assertFalse(rule.isAbsolute)
    }

    @Test
    fun isAbsolute_is_true_when_absoluteAbove_is_set() {
        val rule = SignalRule(
            MetricType.HSCRP, DeviationDirection.ABOVE,
            thresholdSigma = 0.0, minDurationHours = 0, weight = 1.0,
            absoluteAbove = 1.0
        )
        assertTrue(rule.isAbsolute)
    }

    @Test
    fun isAbsolute_is_true_when_absoluteBelow_is_set() {
        val rule = SignalRule(
            MetricType.BLOOD_GLUCOSE, DeviationDirection.BELOW,
            thresholdSigma = 0.0, minDurationHours = 0, weight = 1.0,
            absoluteBelow = 70.0
        )
        assertTrue(rule.isAbsolute)
    }

    // -- Pattern wiring --

    @Test
    fun biomarker_patterns_are_registered_in_global_all_list() {
        assertTrue(BiomarkerConditionPatterns.inflammationSignature in ConditionPatterns.all)
        assertTrue(BiomarkerConditionPatterns.prediabetesSignature in ConditionPatterns.all)
    }

    @Test
    fun inflammation_signature_gates_on_hsCRP_at_or_above_1_mg_per_L() {
        val pattern = BiomarkerConditionPatterns.inflammationSignature
        val hsCrpRule = pattern.signalRules.first { it.metricType == MetricType.HSCRP }
        assertTrue("hsCRP rule should be required so the lab anchors the pattern", hsCrpRule.required)
        assertTrue(hsCrpRule.isAbsolute)
        assertEquals(1.0, hsCrpRule.absoluteAbove!!, 1e-9)
        assertNull(hsCrpRule.absoluteBelow)
        assertEquals(ThresholdSource.LITERATURE, hsCrpRule.source)
    }

    @Test
    fun inflammation_signature_pairs_lab_with_baseline_relative_vital_signs() {
        val pattern = BiomarkerConditionPatterns.inflammationSignature
        val supporting = pattern.signalRules.filter { !it.isAbsolute }
        // Resting HR up and HRV down are the canonical autonomic shifts under
        // chronic inflammation. Both are baseline-relative (no absolute).
        val supportingMetrics = supporting.map { it.metricType }.toSet()
        assertTrue(MetricType.RESTING_HEART_RATE in supportingMetrics)
        assertTrue(MetricType.HEART_RATE_VARIABILITY in supportingMetrics)
        for (rule in supporting) {
            assertFalse(
                "Corroborating rules must not be required — they're supporting, not gating",
                rule.required
            )
        }
    }

    @Test
    fun prediabetes_signature_gates_on_HbA1c_at_or_above_5_point_7_pct() {
        val pattern = BiomarkerConditionPatterns.prediabetesSignature
        val hba1cRule = pattern.signalRules.first { it.metricType == MetricType.HBA1C }
        assertTrue(hba1cRule.required)
        assertTrue(hba1cRule.isAbsolute)
        assertEquals(5.7, hba1cRule.absoluteAbove!!, 1e-9)
        assertEquals(ThresholdSource.LITERATURE, hba1cRule.source)
    }

    @Test
    fun prediabetes_signature_pairs_lab_with_sleep_and_resting_HR_drift() {
        val pattern = BiomarkerConditionPatterns.prediabetesSignature
        val supportingMetrics = pattern.signalRules
            .filter { !it.isAbsolute }
            .map { it.metricType }
            .toSet()
        assertTrue(MetricType.SLEEP_EFFICIENCY in supportingMetrics)
        assertTrue(MetricType.RESTING_HEART_RATE in supportingMetrics)
    }

    @Test
    fun patterns_carry_literature_citations_for_every_threshold() {
        for (pattern in BiomarkerConditionPatterns.all) {
            for (rule in pattern.signalRules) {
                assertNotNull("${pattern.id}/${rule.metricType.key} should cite its threshold source", rule.source)
                assertTrue(
                    "${pattern.id}/${rule.metricType.key} should ship a citation string",
                    rule.citation.isNotBlank()
                )
            }
        }
    }
}
