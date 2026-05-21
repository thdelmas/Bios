package com.bios.app

import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.EmergencyVitalPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the emergency vital-sign patterns that wire [AlertTier.URGENT].
 * These were the audit's §2.1 finding: the URGENT tier existed in the enum
 * and the notification channel but no code path produced it. Each pattern
 * declares `severityFloor = URGENT` and uses a single `required = true`
 * absolute-threshold rule — both contracts the [com.bios.app.engine.AnomalyDetector]
 * relies on for escalation.
 */
class EmergencyVitalPatternsTest {

    // -- registration --

    @Test
    fun all_emergency_patterns_register_in_global_all_list() {
        assertTrue(EmergencyVitalPatterns.spo2Critical in ConditionPatterns.all)
        assertTrue(EmergencyVitalPatterns.hypoglycemiaCritical in ConditionPatterns.all)
        assertTrue(EmergencyVitalPatterns.tachycardiaCritical in ConditionPatterns.all)
        assertTrue(EmergencyVitalPatterns.bradycardiaCritical in ConditionPatterns.all)
    }

    @Test
    fun every_emergency_pattern_declares_urgent_severity_floor() {
        for (pattern in EmergencyVitalPatterns.all) {
            assertEquals(
                "${pattern.id} should declare severityFloor = URGENT so a single hard-cutoff crossing escalates above ADVISORY",
                AlertTier.URGENT,
                pattern.severityFloor
            )
        }
    }

    @Test
    fun every_emergency_pattern_is_a_single_rule_required_absolute_gate() {
        for (pattern in EmergencyVitalPatterns.all) {
            assertEquals(
                "${pattern.id} should fire on a single absolute rule — emergency patterns don't need corroborators",
                1,
                pattern.signalRules.size
            )
            assertEquals(1, pattern.minActiveSignals)
            val rule = pattern.signalRules.single()
            assertTrue("${pattern.id} rule should be required", rule.required)
            assertTrue("${pattern.id} rule should be absolute", rule.isAbsolute)
            assertEquals(ThresholdSource.LITERATURE, rule.source)
            assertTrue("${pattern.id} should ship a citation", rule.citation.isNotBlank())
        }
    }

    // -- spo2_critical --

    @Test
    fun spo2_critical_fires_at_or_below_85_percent() {
        val rule = EmergencyVitalPatterns.spo2Critical.signalRules.single()
        assertEquals(MetricType.BLOOD_OXYGEN, rule.metricType)
        assertEquals(DeviationDirection.BELOW, rule.direction)
        assertEquals(85.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    // -- hypoglycemia_critical --

    @Test
    fun hypoglycemia_critical_fires_at_or_below_54_mg_per_dL() {
        val rule = EmergencyVitalPatterns.hypoglycemiaCritical.signalRules.single()
        assertEquals(MetricType.BLOOD_GLUCOSE, rule.metricType)
        assertEquals(DeviationDirection.BELOW, rule.direction)
        // ADA "Level 2" hypoglycemia — the clinically significant cutoff.
        assertEquals(54.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    // -- tachycardia_critical --

    @Test
    fun tachycardia_critical_fires_at_or_above_130_bpm() {
        val rule = EmergencyVitalPatterns.tachycardiaCritical.signalRules.single()
        assertEquals(MetricType.RESTING_HEART_RATE, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(130.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
    }

    // -- bradycardia_critical --

    @Test
    fun bradycardia_critical_fires_at_or_below_35_bpm() {
        val rule = EmergencyVitalPatterns.bradycardiaCritical.signalRules.single()
        assertEquals(MetricType.RESTING_HEART_RATE, rule.metricType)
        assertEquals(DeviationDirection.BELOW, rule.direction)
        assertEquals(35.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    // -- content / citation hygiene --

    @Test
    fun every_emergency_pattern_carries_a_suggested_action_with_professional_referral_language() {
        for (pattern in EmergencyVitalPatterns.all) {
            val action = pattern.suggestedAction
            assertNotNull("${pattern.id} should ship a suggestedAction", action)
            assertTrue(
                "${pattern.id} suggestedAction should reference professional / emergency care (not pure self-management)",
                action!!.contains("medical", ignoreCase = true) ||
                    action.contains("emergency", ignoreCase = true) ||
                    action.contains("healthcare", ignoreCase = true) ||
                    action.contains("provider", ignoreCase = true)
            )
        }
    }
}
