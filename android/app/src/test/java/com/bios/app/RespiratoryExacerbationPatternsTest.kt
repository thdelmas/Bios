package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.RespiratoryExacerbationPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the COPD / asthma exacerbation patterns (#161, audit gap §2.8).
 * Both patterns gate on multi-signal convergence of baseline-relative
 * respiratory signals — distinct from the existing
 * [com.bios.app.alerts.ConditionPatterns.respiratoryInfection] pattern
 * which uses acute thresholds and temperature.
 */
class RespiratoryExacerbationPatternsTest {

    @Test
    fun both_patterns_register_in_global_all_list() {
        assertTrue(RespiratoryExacerbationPatterns.copdExacerbationSignature in ConditionPatterns.all)
        assertTrue(RespiratoryExacerbationPatterns.asthmaExacerbationSignature in ConditionPatterns.all)
    }

    @Test
    fun every_rule_carries_a_literature_citation() {
        for (pattern in RespiratoryExacerbationPatterns.all) {
            for (rule in pattern.signalRules) {
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                    ThresholdSource.LITERATURE,
                    rule.source
                )
                assertTrue(rule.citation.isNotBlank())
            }
        }
    }

    // -- copd_exacerbation --

    @Test
    fun copd_signature_uses_baseline_relative_SpO2_below_not_absolute_cutoff() {
        // Key clinical point: COPD owners have lower SpO2 baselines than
        // non-COPD owners. The pattern fires on baseline-relative drift,
        // not absolute SpO2 cutoffs — that's the differentiator from the
        // emergency-vital absolute-cutoff path.
        val spo2 = RespiratoryExacerbationPatterns.copdExacerbationSignature.signalRules
            .first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertFalse(
            "SpO2 rule must be baseline-relative, not absolute — COPD owners have lower baselines",
            spo2.isAbsolute
        )
        assertEquals(DeviationDirection.BELOW, spo2.direction)
        assertEquals(168, spo2.minDurationHours)
    }

    @Test
    fun copd_signature_carries_RR_activity_fragmentation_corroborators() {
        val pattern = RespiratoryExacerbationPatterns.copdExacerbationSignature
        val metrics = pattern.signalRules.map { it.metricType }.toSet()
        assertTrue(MetricType.BLOOD_OXYGEN in metrics)
        assertTrue(MetricType.RESPIRATORY_RATE in metrics)
        assertTrue(MetricType.ACTIVE_MINUTES in metrics)
        assertTrue(MetricType.SLEEP_FRAGMENTATION_INDEX in metrics)
    }

    @Test
    fun copd_signature_requires_at_least_three_signals_to_fire() {
        // 4-signal pattern with 3-of-4 requirement — single-signal noise
        // doesn't fire (a 1-day flu, a strenuous-hike SpO2 drop).
        assertEquals(3, RespiratoryExacerbationPatterns.copdExacerbationSignature.minActiveSignals)
    }

    @Test
    fun copd_signature_does_not_declare_a_severity_floor() {
        // Trend / screening pattern, not an emergency — uses the
        // classifier output. The emergency SpO2 path lives in
        // EmergencyVitalPatterns.spo2Critical with absolute cutoff.
        assertNull(RespiratoryExacerbationPatterns.copdExacerbationSignature.severityFloor)
    }

    // -- asthma_exacerbation --

    @Test
    fun asthma_signature_weights_RR_and_fragmentation_higher_than_SpO2() {
        // SpO2 is a LATE sign in asthma — symptom cluster (nocturnal
        // symptoms via fragmentation, RR up) leads. The pattern weights
        // the symptom-cluster signals higher than SpO2.
        val pattern = RespiratoryExacerbationPatterns.asthmaExacerbationSignature
        val rr = pattern.signalRules.first { it.metricType == MetricType.RESPIRATORY_RATE }
        val fragmentation = pattern.signalRules.first { it.metricType == MetricType.SLEEP_FRAGMENTATION_INDEX }
        val spo2 = pattern.signalRules.first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertTrue(
            "RR (${rr.weight}) should be weighted ≥ SpO2 (${spo2.weight}) — SpO2 is a late sign in asthma",
            rr.weight >= spo2.weight,
        )
        assertTrue(
            "Fragmentation (${fragmentation.weight}) should be weighted ≥ SpO2 (${spo2.weight})",
            fragmentation.weight >= spo2.weight,
        )
    }

    @Test
    fun asthma_signature_requires_at_least_three_signals_to_fire() {
        assertEquals(3, RespiratoryExacerbationPatterns.asthmaExacerbationSignature.minActiveSignals)
    }

    @Test
    fun every_rule_is_baseline_relative_over_a_seven_day_window() {
        // Both patterns are trend-based; nothing should be a single-
        // reading absolute cutoff (those live in EmergencyVitalPatterns).
        for (pattern in RespiratoryExacerbationPatterns.all) {
            for (rule in pattern.signalRules) {
                assertFalse(
                    "${pattern.id}/${rule.metricType.key} should be baseline-relative",
                    rule.isAbsolute
                )
                assertEquals(
                    "${pattern.id}/${rule.metricType.key} should use 7-day window",
                    168, rule.minDurationHours
                )
            }
        }
    }
}
