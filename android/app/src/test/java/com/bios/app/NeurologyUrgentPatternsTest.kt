package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.NeurologyUrgentPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the neurology URGENT patterns wired in #24 (NEUROLOGY_POV.md §2.1
 * + §2.3). Same shape as `EmergencyVitalPatternsTest`: every pattern must
 * declare `severityFloor = URGENT`, fire on a single required absolute
 * rule, cite literature, and pass [AlertContentPolicy].
 */
class NeurologyUrgentPatternsTest {

    @Test
    fun all_neurology_urgent_patterns_register_in_global_all_list() {
        assertTrue(NeurologyUrgentPatterns.seizureEventLogged in ConditionPatterns.all)
        assertTrue(NeurologyUrgentPatterns.acuteAlteredConsciousnessLowGcs in ConditionPatterns.all)
        assertTrue(NeurologyUrgentPatterns.thunderclapHeadache in ConditionPatterns.all)
    }

    @Test
    fun every_neurology_pattern_declares_urgent_severity_floor() {
        for (pattern in NeurologyUrgentPatterns.all) {
            assertEquals(
                "${pattern.id} should declare severityFloor = URGENT",
                AlertTier.URGENT,
                pattern.severityFloor,
            )
        }
    }

    @Test
    fun every_neurology_pattern_is_a_single_rule_required_absolute_gate() {
        for (pattern in NeurologyUrgentPatterns.all) {
            assertEquals(
                "${pattern.id} should fire on a single absolute rule",
                1,
                pattern.signalRules.size,
            )
            assertEquals(1, pattern.minActiveSignals)
            val rule = pattern.signalRules.single()
            assertTrue("${pattern.id} rule should be required", rule.required)
            assertTrue("${pattern.id} rule should be absolute", rule.isAbsolute)
            assertEquals(ThresholdSource.LITERATURE, rule.source)
            assertTrue("${pattern.id} should ship a citation", rule.citation.isNotBlank())
        }
    }

    @Test
    fun every_neurology_pattern_passes_alert_content_policy() {
        for (pattern in NeurologyUrgentPatterns.all) {
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                "${pattern.id} must comply with AlertContentPolicy. Violations: $violations",
                violations.isEmpty(),
            )
        }
    }

    // -- seizure_event_logged --

    @Test
    fun seizure_event_logged_fires_on_any_event_in_last_hour() {
        val rule = NeurologyUrgentPatterns.seizureEventLogged.signalRules.single()
        assertEquals(MetricType.SEIZURE_EVENT, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(0.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        assertEquals(1, rule.absoluteWindowHours)
        assertEquals(1, rule.absoluteMinReadings)
    }

    // -- acute_altered_consciousness_gcs_le_8 --

    @Test
    fun acute_altered_consciousness_fires_at_gcs_le_8() {
        val rule = NeurologyUrgentPatterns.acuteAlteredConsciousnessLowGcs.signalRules.single()
        assertEquals(MetricType.CONSCIOUSNESS_LEVEL, rule.metricType)
        assertEquals(DeviationDirection.BELOW, rule.direction)
        // GCS integer semantics: cutoff of 9.0 (below) catches the 8 / 7 / …
        // / 3 readings while leaving 9–15 above-threshold.
        assertEquals(9.0, rule.absoluteBelow!!, 1e-9)
        assertNull(rule.absoluteAbove)
    }

    // -- thunderclap_headache --

    @Test
    fun thunderclap_headache_fires_on_owner_logged_event() {
        val rule = NeurologyUrgentPatterns.thunderclapHeadache.signalRules.single()
        assertEquals(MetricType.THUNDERCLAP_HEADACHE_SUSPECTED, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(0.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        assertEquals(1, rule.absoluteWindowHours)
    }

    // -- text quality --

    @Test
    fun every_neurology_pattern_has_explanation_action_and_references() {
        for (pattern in NeurologyUrgentPatterns.all) {
            assertTrue("${pattern.id} needs an explanation", pattern.explanation.isNotBlank())
            assertNotNull("${pattern.id} needs a suggested action", pattern.suggestedAction)
            assertTrue("${pattern.id} suggested action is blank", pattern.suggestedAction!!.isNotBlank())
            assertTrue(
                "${pattern.id} should ship ≥2 references",
                pattern.references.size >= 2,
            )
        }
    }
}
