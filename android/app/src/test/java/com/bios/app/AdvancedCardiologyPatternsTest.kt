package com.bios.app

import com.bios.app.alerts.AdvancedCardiologyPatterns
import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.ThresholdSource
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [AdvancedCardiologyPatterns]. HRR and ectopy-burden are
 * shipped; POTS and QT-prolongation remain deferred.
 */
class AdvancedCardiologyPatternsTest {

    @Test
    fun hr_recovery_pattern_is_registered_in_global_all_list() {
        assertTrue(AdvancedCardiologyPatterns.hrRecoveryImpaired in ConditionPatterns.all)
    }

    @Test
    fun hr_recovery_fires_at_or_below_12_bpm_cutoff() {
        val rule = AdvancedCardiologyPatterns.hrRecoveryImpaired.signalRules.single()
        assertEquals(MetricType.HR_RECOVERY_1MIN, rule.metricType)
        assertEquals(DeviationDirection.BELOW, rule.direction)
        assertEquals(12.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
        // Median across recent sessions, not a single bad workout.
        assertEquals(720, rule.absoluteWindowHours)
        assertEquals(3, rule.absoluteMinReadings)
        assertEquals(ThresholdSource.LITERATURE, rule.source)
        assertTrue("citation must be non-empty", rule.citation.isNotBlank())
        assertTrue("rule must be required", rule.required)
    }

    @Test
    fun hr_recovery_is_advisory_not_urgent() {
        // Chronic prognostic signal — NEJM 1999 was a 6-year mortality
        // study, not an acute medical event. URGENT escalation would
        // be a category mistake.
        assertNull(AdvancedCardiologyPatterns.hrRecoveryImpaired.severityFloor)
    }

    @Test
    fun hr_recovery_passes_alert_content_policy() {
        val violations = AlertContentPolicy.validate(AdvancedCardiologyPatterns.hrRecoveryImpaired)
        assertTrue(
            "HRR pattern violates AlertContentPolicy: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun hr_recovery_carries_explanation_action_and_references() {
        val p = AdvancedCardiologyPatterns.hrRecoveryImpaired
        assertTrue("explanation blank", p.explanation.isNotBlank())
        assertTrue("suggested action blank", p.suggestedAction!!.isNotBlank())
        assertTrue("≥2 references", p.references.size >= 2)
    }

    // -- ectopy_burden_elevated --

    @Test
    fun ectopy_burden_pattern_is_registered_in_global_all_list() {
        assertTrue(AdvancedCardiologyPatterns.ectopyBurdenElevated in ConditionPatterns.all)
    }

    @Test
    fun ectopy_burden_fires_at_or_above_10_percent_cutoff() {
        val rule = AdvancedCardiologyPatterns.ectopyBurdenElevated.signalRules.single()
        assertEquals(MetricType.ECTOPY_BURDEN_ESTIMATE, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(10.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        // 7-day rolling window with ≥ 3 captures — matches the higher
        // PPG-session cadence and Holter-burden averaging convention.
        assertEquals(168, rule.absoluteWindowHours)
        assertEquals(3, rule.absoluteMinReadings)
        assertEquals(ThresholdSource.LITERATURE, rule.source)
        assertTrue("citation must be non-empty", rule.citation.isNotBlank())
        assertTrue("rule must be required", rule.required)
    }

    @Test
    fun ectopy_burden_is_advisory_not_urgent() {
        // Holter-referral trigger, not an acute event.
        assertNull(AdvancedCardiologyPatterns.ectopyBurdenElevated.severityFloor)
    }

    @Test
    fun ectopy_burden_passes_alert_content_policy() {
        val violations = AlertContentPolicy.validate(AdvancedCardiologyPatterns.ectopyBurdenElevated)
        assertTrue("$violations", violations.isEmpty())
    }

    @Test
    fun ectopy_burden_carries_explanation_action_and_references() {
        val p = AdvancedCardiologyPatterns.ectopyBurdenElevated
        assertTrue("explanation blank", p.explanation.isNotBlank())
        assertTrue("suggested action blank", p.suggestedAction!!.isNotBlank())
        assertTrue("≥2 references", p.references.size >= 2)
    }
}
