package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.SleepApneaPattern
import com.bios.app.alerts.ThresholdSource
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the sleep-apnea screening pattern (#157, audit gap §2.8).
 * Same lab-anchor + wearable-corroborator shape as the biomarker patterns:
 * AHI gates the pattern via an absolute clinical threshold; SpO2 / RHR /
 * fragmentation corroborators stay baseline-relative.
 */
class SleepApneaPatternTest {

    // -- registration --

    @Test
    fun pattern_is_registered_in_global_all_list() {
        assertTrue(SleepApneaPattern.sleepApneaScreen in ConditionPatterns.all)
    }

    // -- AHI gate --

    @Test
    fun gates_on_AHI_at_or_above_5_per_hour() {
        val rule = SleepApneaPattern.sleepApneaScreen.signalRules
            .first { it.metricType == MetricType.AHI }
        assertTrue("AHI rule must anchor the pattern", rule.required)
        assertTrue(rule.isAbsolute)
        // AASM mild-OSA screening threshold.
        assertEquals(5.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        assertEquals(ThresholdSource.LITERATURE, rule.source)
        assertTrue("AHI rule must ship a citation", rule.citation.isNotBlank())
    }

    // -- wearable corroborators --

    @Test
    fun carries_SpO2_RHR_and_fragmentation_corroborators() {
        val pattern = SleepApneaPattern.sleepApneaScreen
        val corroborators = pattern.signalRules.filter { !it.required }.map { it.metricType }.toSet()
        assertTrue(MetricType.BLOOD_OXYGEN in corroborators)
        assertTrue(MetricType.RESTING_HEART_RATE in corroborators)
        assertTrue(MetricType.SLEEP_FRAGMENTATION_INDEX in corroborators)
    }

    @Test
    fun corroborators_are_baseline_relative_not_absolute() {
        // The wearable signatures of OSA (nocturnal desaturation, RHR drift,
        // micro-arousal-driven fragmentation) are individual-baseline-
        // relative deviations, not hard clinical cutoffs.
        for (rule in SleepApneaPattern.sleepApneaScreen.signalRules.filter { !it.required }) {
            assertFalse(
                "${rule.metricType.key} should be baseline-relative, not absolute",
                rule.isAbsolute
            )
            assertEquals(168, rule.minDurationHours)
        }
    }

    @Test
    fun spo2_corroborator_uses_below_direction() {
        val spo2 = SleepApneaPattern.sleepApneaScreen.signalRules
            .first { it.metricType == MetricType.BLOOD_OXYGEN }
        assertEquals(DeviationDirection.BELOW, spo2.direction)
    }

    // -- aggregation --

    @Test
    fun requires_at_least_one_corroborator() {
        // AHI alone (1 active signal) is insufficient — minActiveSignals=2
        // forces at least one wearable signature to corroborate.
        assertEquals(2, SleepApneaPattern.sleepApneaScreen.minActiveSignals)
    }

    @Test
    fun every_rule_carries_a_literature_citation() {
        for (rule in SleepApneaPattern.sleepApneaScreen.signalRules) {
            assertEquals(ThresholdSource.LITERATURE, rule.source)
            assertTrue("rule for ${rule.metricType.key} should ship a citation", rule.citation.isNotBlank())
        }
    }

    // -- contract surface for the new MetricType keys --

    @Test
    fun ahi_is_a_respiratory_count_metric_with_manual_entry() {
        assertEquals(MetricDomain.RESPIRATORY, MetricType.AHI.domain)
        assertEquals(MetricUnit.COUNT, MetricType.AHI.unit)
        assertTrue("AHI must allow manual entry (PSG / HSAT reports)", MetricType.AHI.allowsManualEntry)
    }

    @Test
    fun sleep_apnea_event_is_a_respiratory_event_metric_without_manual_entry() {
        assertEquals(MetricDomain.RESPIRATORY, MetricType.SLEEP_APNEA_EVENT.domain)
        assertEquals(MetricUnit.EVENT, MetricType.SLEEP_APNEA_EVENT.unit)
        assertFalse(
            "Apnea events are vendor-passthrough (Apple/Samsung detection); owner doesn't manually log them",
            MetricType.SLEEP_APNEA_EVENT.allowsManualEntry
        )
    }
}
