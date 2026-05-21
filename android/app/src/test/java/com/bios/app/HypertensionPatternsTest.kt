package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.HypertensionPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the BP-anchored condition patterns from audit gap §2.4. Two
 * shapes:
 *
 *  - [HypertensionPatterns.hypertensionEmerging] — multi-reading absolute
 *    median check (white-coat-robust). Verifies the new
 *    `absoluteWindowHours` / `absoluteMinReadings` SignalRule fields are
 *    set as intended and that the pattern allows isolated systolic /
 *    isolated diastolic presentations to fire (neither BP rule required).
 *  - [HypertensionPatterns.hypertensiveUrgency] — single-reading crisis
 *    pattern. Verifies it declares `severityFloor = URGENT` and uses OR
 *    semantics across SBP/DBP (`minActiveSignals = 1`).
 */
class HypertensionPatternsTest {

    // -- registration --

    @Test
    fun both_patterns_register_in_global_all_list() {
        assertTrue(HypertensionPatterns.hypertensionEmerging in ConditionPatterns.all)
        assertTrue(HypertensionPatterns.hypertensiveUrgency in ConditionPatterns.all)
    }

    // -- hypertension_emerging --

    @Test
    fun emerging_systolic_rule_uses_7d_median_with_3_reading_floor() {
        val pattern = HypertensionPatterns.hypertensionEmerging
        val sbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertTrue(sbp.isAbsolute)
        assertEquals(130.0, sbp.absoluteAbove!!, 1e-9)
        assertEquals(168, sbp.absoluteWindowHours)
        assertEquals(3, sbp.absoluteMinReadings)
        assertEquals(ThresholdSource.LITERATURE, sbp.source)
        assertFalse(
            "Neither BP rule required, so isolated systolic / isolated diastolic patterns can fire",
            sbp.required
        )
    }

    @Test
    fun emerging_diastolic_rule_uses_7d_median_with_3_reading_floor() {
        val pattern = HypertensionPatterns.hypertensionEmerging
        val dbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC }
        assertTrue(dbp.isAbsolute)
        assertEquals(80.0, dbp.absoluteAbove!!, 1e-9)
        assertEquals(168, dbp.absoluteWindowHours)
        assertEquals(3, dbp.absoluteMinReadings)
        assertFalse(dbp.required)
    }

    @Test
    fun emerging_carries_RHR_baseline_relative_corroborator() {
        val pattern = HypertensionPatterns.hypertensionEmerging
        val rhr = pattern.signalRules.first { it.metricType == MetricType.RESTING_HEART_RATE }
        assertFalse("RHR is baseline-relative corroborator, not absolute", rhr.isAbsolute)
        assertEquals(DeviationDirection.ABOVE, rhr.direction)
        assertEquals(168, rhr.minDurationHours)
    }

    @Test
    fun emerging_requires_at_least_two_signals_to_fire() {
        // Isolated systolic + RHR corroborator = 2 active = fires.
        // Isolated systolic alone (1 active) doesn't fire — white-coat guard
        // beyond the median check.
        assertEquals(2, HypertensionPatterns.hypertensionEmerging.minActiveSignals)
    }

    @Test
    fun emerging_does_not_declare_a_severity_floor() {
        // Trend pattern — uses the classifier output, not the URGENT escalation.
        assertNull(HypertensionPatterns.hypertensionEmerging.severityFloor)
    }

    // -- hypertensive_urgency --

    @Test
    fun urgency_systolic_rule_uses_single_reading_180_cutoff() {
        val pattern = HypertensionPatterns.hypertensiveUrgency
        val sbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_SYSTOLIC }
        assertTrue(sbp.isAbsolute)
        assertEquals(180.0, sbp.absoluteAbove!!, 1e-9)
        assertEquals("Single-reading semantics — no window", 0, sbp.absoluteWindowHours)
        assertNull(sbp.absoluteBelow)
    }

    @Test
    fun urgency_diastolic_rule_uses_single_reading_120_cutoff() {
        val pattern = HypertensionPatterns.hypertensiveUrgency
        val dbp = pattern.signalRules.first { it.metricType == MetricType.BLOOD_PRESSURE_DIASTOLIC }
        assertTrue(dbp.isAbsolute)
        assertEquals(120.0, dbp.absoluteAbove!!, 1e-9)
        assertEquals(0, dbp.absoluteWindowHours)
    }

    @Test
    fun urgency_uses_OR_semantics_via_minActiveSignals_one() {
        // SBP ≥180 OR DBP ≥120 — either fires the pattern alone.
        assertEquals(1, HypertensionPatterns.hypertensiveUrgency.minActiveSignals)
    }

    @Test
    fun urgency_declares_URGENT_severity_floor() {
        assertEquals(
            "Hypertensive urgency escalates to URGENT regardless of classifier output",
            AlertTier.URGENT,
            HypertensionPatterns.hypertensiveUrgency.severityFloor
        )
    }

    @Test
    fun urgency_suggested_action_carries_recheck_and_emergency_referral() {
        val action = HypertensionPatterns.hypertensiveUrgency.suggestedAction
        assertNotNull(action)
        // The clinical protocol is re-check, then act — must be reflected.
        assertTrue("Should prompt re-check", action!!.contains("re-check", ignoreCase = true))
        assertTrue(
            "Should reference emergency / immediate medical attention",
            action.contains("emergency", ignoreCase = true) ||
                action.contains("immediate medical attention", ignoreCase = true)
        )
    }

    // -- citation hygiene --

    @Test
    fun every_BP_rule_carries_a_literature_citation() {
        for (pattern in HypertensionPatterns.all) {
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
}
