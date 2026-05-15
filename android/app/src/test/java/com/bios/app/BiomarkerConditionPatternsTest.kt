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

    // -- dyslipidemia_signature --

    @Test
    fun dyslipidemia_signature_gates_on_LDL_at_or_above_160_mg_per_dL() {
        val pattern = BiomarkerConditionPatterns.dyslipidemiaSignature
        val ldlRule = pattern.signalRules.first { it.metricType == MetricType.LDL_CHOLESTEROL }
        assertTrue(ldlRule.required)
        assertTrue(ldlRule.isAbsolute)
        assertEquals(160.0, ldlRule.absoluteAbove!!, 1e-9)
    }

    @Test
    fun dyslipidemia_signature_carries_corroborating_lipid_markers() {
        val pattern = BiomarkerConditionPatterns.dyslipidemiaSignature
        val supporting = pattern.signalRules.filter { !it.required }
        val supportingMetrics = supporting.map { it.metricType }.toSet()
        assertTrue(MetricType.HDL_CHOLESTEROL in supportingMetrics)
        assertTrue(MetricType.TRIGLYCERIDES in supportingMetrics)
        assertTrue(MetricType.APO_B in supportingMetrics)
    }

    @Test
    fun dyslipidemia_HDL_rule_uses_absoluteBelow_for_low_concern() {
        val pattern = BiomarkerConditionPatterns.dyslipidemiaSignature
        val hdlRule = pattern.signalRules.first { it.metricType == MetricType.HDL_CHOLESTEROL }
        assertTrue(hdlRule.isAbsolute)
        assertEquals(40.0, hdlRule.absoluteBelow!!, 1e-9)
        assertNull(hdlRule.absoluteAbove)
    }

    @Test
    fun dyslipidemia_signature_requires_at_least_one_corroborator_via_minActiveSignals() {
        // LDL required + 1 corroborator = 2 active signals = pattern fires.
        // LDL alone would only be 1 active signal — minActiveSignals = 2
        // prevents an isolated LDL spike from triggering the pattern.
        assertEquals(2, BiomarkerConditionPatterns.dyslipidemiaSignature.minActiveSignals)
    }

    // -- vitamin_d_deficiency_signature --

    @Test
    fun vitamin_d_signature_gates_on_25_OH_below_20_ng_per_mL() {
        val pattern = BiomarkerConditionPatterns.vitaminDDeficiencySignature
        val vdRule = pattern.signalRules.first { it.metricType == MetricType.VITAMIN_D_25OH }
        assertTrue(vdRule.required)
        assertTrue(vdRule.isAbsolute)
        // BELOW threshold — low vit D is the concern, mirroring the bands.
        assertEquals(20.0, vdRule.absoluteBelow!!, 1e-9)
        assertNull(vdRule.absoluteAbove)
        assertEquals(ThresholdSource.LITERATURE, vdRule.source)
    }

    @Test
    fun vitamin_d_signature_carries_recovery_proxies_as_corroborators() {
        val pattern = BiomarkerConditionPatterns.vitaminDDeficiencySignature
        val corroborators = pattern.signalRules
            .filter { !it.required }
            .map { it.metricType }
            .toSet()
        // Sleep / activity / mood are the three best-evidenced wearable
        // proxies for symptomatic vitamin D deficiency.
        assertTrue(MetricType.SLEEP_EFFICIENCY in corroborators)
        assertTrue(MetricType.ACTIVE_MINUTES in corroborators)
        assertTrue(MetricType.MOOD_DRIFT_SCORE in corroborators)
    }

    @Test
    fun vitamin_d_signature_requires_at_least_one_corroborator() {
        assertEquals(2, BiomarkerConditionPatterns.vitaminDDeficiencySignature.minActiveSignals)
    }

    // -- hypothyroid_signature --

    @Test
    fun hypothyroid_signature_gates_on_TSH_at_or_above_4_mIU_per_L() {
        val pattern = BiomarkerConditionPatterns.hypothyroidSignature
        val tshRule = pattern.signalRules.first { it.metricType == MetricType.TSH }
        assertTrue(tshRule.required)
        assertTrue(tshRule.isAbsolute)
        assertEquals(4.0, tshRule.absoluteAbove!!, 1e-9)
        assertNull(tshRule.absoluteBelow)
        assertEquals(ThresholdSource.LITERATURE, tshRule.source)
    }

    @Test
    fun hypothyroid_signature_uses_free_T4_below_0_8_as_an_overt_hypo_corroborator() {
        val pattern = BiomarkerConditionPatterns.hypothyroidSignature
        val ft4Rule = pattern.signalRules.first { it.metricType == MetricType.FREE_T4 }
        assertFalse("free T4 is corroborating, not gating", ft4Rule.required)
        assertTrue(ft4Rule.isAbsolute)
        assertEquals(0.8, ft4Rule.absoluteBelow!!, 1e-9)
    }

    @Test
    fun hypothyroid_signature_corroborators_match_slow_metabolism_presentation() {
        val pattern = BiomarkerConditionPatterns.hypothyroidSignature
        val rhrRule = pattern.signalRules.first { it.metricType == MetricType.RESTING_HEART_RATE }
        // Bradycardia: BELOW baseline, not absolute (this is a deviation
        // rule, not a clinical-cutoff rule like the lab gates).
        assertFalse(rhrRule.isAbsolute)
        assertEquals(com.bios.app.alerts.DeviationDirection.BELOW, rhrRule.direction)

        val activityRule = pattern.signalRules.first { it.metricType == MetricType.ACTIVE_MINUTES }
        assertFalse(activityRule.isAbsolute)
        assertEquals(com.bios.app.alerts.DeviationDirection.BELOW, activityRule.direction)
    }

    @Test
    fun hypothyroid_signature_requires_at_least_one_corroborator() {
        assertEquals(2, BiomarkerConditionPatterns.hypothyroidSignature.minActiveSignals)
    }
}
