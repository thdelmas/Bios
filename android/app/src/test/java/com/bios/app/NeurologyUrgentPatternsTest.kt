package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.NeurologyUrgentPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.model.SeizureEventFields
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the neurology event-triggered patterns wired in #24 / #268
 * (NEUROLOGY_POV.md §2.1 + §2.3). Same shape as `EmergencyVitalPatternsTest`:
 * every pattern fires on a single required absolute rule, cites literature,
 * and passes [AlertContentPolicy]. Severity floor is URGENT for single-event
 * acute patterns and ADVISORY for [NeurologyUrgentPatterns.seizureCluster].
 */
class NeurologyUrgentPatternsTest {

    @Test
    fun all_neurology_urgent_patterns_register_in_global_all_list() {
        assertTrue(NeurologyUrgentPatterns.statusEpilepticusConvulsive in ConditionPatterns.all)
        assertTrue(NeurologyUrgentPatterns.seizureCluster in ConditionPatterns.all)
        assertTrue(NeurologyUrgentPatterns.acuteAlteredConsciousnessLowGcs in ConditionPatterns.all)
        assertTrue(NeurologyUrgentPatterns.thunderclapHeadache in ConditionPatterns.all)
    }

    @Test
    fun every_neurology_pattern_declares_the_expected_severity_floor() {
        val expected = mapOf(
            NeurologyUrgentPatterns.statusEpilepticusConvulsive.id to AlertTier.URGENT,
            NeurologyUrgentPatterns.seizureCluster.id to AlertTier.ADVISORY,
            NeurologyUrgentPatterns.acuteAlteredConsciousnessLowGcs.id to AlertTier.URGENT,
            NeurologyUrgentPatterns.thunderclapHeadache.id to AlertTier.URGENT,
        )
        for (pattern in NeurologyUrgentPatterns.all) {
            assertEquals(
                "${pattern.id} severity floor",
                expected[pattern.id],
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

    // -- status_epilepticus_convulsive --

    @Test
    fun status_epilepticus_convulsive_keys_on_a_5min_seizure_within_the_last_hour() {
        val rule = NeurologyUrgentPatterns.statusEpilepticusConvulsive.signalRules.single()
        assertEquals(MetricType.SEIZURE_EVENT, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(0.0, rule.absoluteAbove!!, 1e-9)
        assertNull(rule.absoluteBelow)
        assertEquals(1, rule.absoluteWindowHours)
        assertEquals(1, rule.absoluteMinReadings)
        // ILAE 2015 t1 = 300 s — the duration discriminator the v1 pattern
        // could not express.
        assertEquals(300, rule.durationAtLeastSec)
    }

    // -- seizure_cluster --

    @Test
    fun seizure_cluster_keys_on_three_or_more_events_in_24h_regardless_of_duration() {
        val rule = NeurologyUrgentPatterns.seizureCluster.signalRules.single()
        assertEquals(MetricType.SEIZURE_EVENT, rule.metricType)
        assertEquals(DeviationDirection.ABOVE, rule.direction)
        assertEquals(0.0, rule.absoluteAbove!!, 1e-9)
        assertEquals(24, rule.absoluteWindowHours)
        assertEquals(3, rule.absoluteMinReadings)
        // No duration filter — Haut 2007 cluster definition is count-based.
        assertNull(rule.durationAtLeastSec)
    }

    // -- #269 Cut 2 safety gate: both seizure patterns exclude
    // wearable-inferred rows from the escalation paths --

    @Test
    fun status_epilepticus_excludes_wearable_inferred_detection_source() {
        val rule = NeurologyUrgentPatterns.statusEpilepticusConvulsive.signalRules.single()
        val gate = rule.excludePayloadFieldValue
        assertNotNull(
            "URGENT seizure pattern must drop wearable-inferred rows",
            gate,
        )
        assertEquals(SeizureEventFields.DETECTION_SOURCE, gate!!.first)
        assertEquals(SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED, gate.second)
    }

    @Test
    fun seizure_cluster_excludes_wearable_inferred_detection_source() {
        val rule = NeurologyUrgentPatterns.seizureCluster.signalRules.single()
        val gate = rule.excludePayloadFieldValue
        assertNotNull(
            "Cluster pattern must drop wearable-inferred rows",
            gate,
        )
        assertEquals(SeizureEventFields.DETECTION_SOURCE, gate!!.first)
        assertEquals(SeizureEventFields.DETECTION_SOURCE_WEARABLE_INFERRED, gate.second)
    }

    @Test
    fun non_seizure_neurology_patterns_do_not_apply_the_payload_gate() {
        // The payload gate is seizure-specific. GCS / thunderclap have no
        // wearable-inferred equivalent (yet) and adding the field on them
        // would silently lock out future detectors that haven't shipped.
        val gcsRule = NeurologyUrgentPatterns.acuteAlteredConsciousnessLowGcs
            .signalRules.single()
        val thunderclapRule = NeurologyUrgentPatterns.thunderclapHeadache
            .signalRules.single()
        assertNull(gcsRule.excludePayloadFieldValue)
        assertNull(thunderclapRule.excludePayloadFieldValue)
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
