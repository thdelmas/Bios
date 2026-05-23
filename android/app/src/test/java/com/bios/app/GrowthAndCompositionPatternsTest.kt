package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.GrowthAndCompositionPatterns
import com.bios.app.alerts.ThresholdSource
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the three trajectory patterns added by audit gap §2.7
 * (PAEDIATRICS_POV.md + Geriatrics sarcopenia + Oncology cachexia).
 *
 * Each pattern is checked for:
 *  - registration in the global pattern registry
 *  - the expected SignalRule shape (metric, direction, citation source)
 *  - PhysiologyState gating (paediatric-only for FTT; non-paediatric for
 *    the adult screens)
 *  - AlertContentPolicy compliance on all push-side text fields
 *  - severity floor (ADVISORY default — no URGENT promotion on
 *    screening signals per the manifesto)
 */
class GrowthAndCompositionPatternsTest {

    // -- registration --

    @Test
    fun all_three_patterns_register_in_global_all_list() {
        assertTrue(GrowthAndCompositionPatterns.failureToThriveScreen in ConditionPatterns.all)
        assertTrue(GrowthAndCompositionPatterns.sarcopeniaTrajectoryScreen in ConditionPatterns.all)
        assertTrue(GrowthAndCompositionPatterns.cachexiaScreen in ConditionPatterns.all)
    }

    // -- failure-to-thrive --

    @Test
    fun ftt_fires_only_in_paediatric_state() {
        val pattern = GrowthAndCompositionPatterns.failureToThriveScreen
        // Should exclude every state except PAEDIATRIC.
        assertTrue(
            "FTT pattern must include STANDARD adult in excludedStates",
            PhysiologyState.STANDARD in pattern.excludedStates
        )
        assertFalse(
            "FTT pattern must NOT exclude paediatric state",
            PhysiologyState.PAEDIATRIC in pattern.excludedStates
        )
    }

    @Test
    fun ftt_carries_a_paediatric_activity_corroborator() {
        val pattern = GrowthAndCompositionPatterns.failureToThriveScreen
        val steps = pattern.signalRules.first { it.metricType == MetricType.STEPS }
        assertEquals(DeviationDirection.BELOW, steps.direction)
        assertEquals(ThresholdSource.LITERATURE, steps.source)
        assertTrue(steps.citation.isNotBlank())
    }

    @Test
    fun ftt_carries_no_severity_floor() {
        // Screening signal — ADVISORY by default, not URGENT.
        assertNull(GrowthAndCompositionPatterns.failureToThriveScreen.severityFloor)
    }

    // -- sarcopenia --

    @Test
    fun sarcopenia_requires_lean_body_mass_decline() {
        val pattern = GrowthAndCompositionPatterns.sarcopeniaTrajectoryScreen
        val lbm = pattern.signalRules.first { it.metricType == MetricType.LEAN_BODY_MASS_KG }
        assertEquals(DeviationDirection.BELOW, lbm.direction)
        assertTrue("LBM must be the required anchor for sarcopenia", lbm.required)
        assertTrue(lbm.citation.contains("EWGSOP2", ignoreCase = true) ||
            lbm.citation.contains("Cruz-Jentoft", ignoreCase = true))
    }

    @Test
    fun sarcopenia_carries_activity_corroborator() {
        val pattern = GrowthAndCompositionPatterns.sarcopeniaTrajectoryScreen
        val active = pattern.signalRules.first { it.metricType == MetricType.ACTIVE_MINUTES }
        assertEquals(DeviationDirection.BELOW, active.direction)
        assertEquals(ThresholdSource.LITERATURE, active.source)
    }

    @Test
    fun sarcopenia_excludes_paediatric_state() {
        val pattern = GrowthAndCompositionPatterns.sarcopeniaTrajectoryScreen
        assertTrue(
            "Sarcopenia is an adult/geriatric screen; paediatric owners must not see it",
            PhysiologyState.PAEDIATRIC in pattern.excludedStates
        )
    }

    @Test
    fun sarcopenia_carries_no_severity_floor() {
        assertNull(GrowthAndCompositionPatterns.sarcopeniaTrajectoryScreen.severityFloor)
    }

    // -- cachexia --

    @Test
    fun cachexia_requires_body_mass_decline() {
        val pattern = GrowthAndCompositionPatterns.cachexiaScreen
        val mass = pattern.signalRules.first { it.metricType == MetricType.BODY_MASS }
        assertEquals(DeviationDirection.BELOW, mass.direction)
        assertTrue("Body-mass loss must be the required anchor", mass.required)
        assertTrue(mass.citation.contains("Fearon", ignoreCase = true))
    }

    @Test
    fun cachexia_carries_RHR_metabolic_inflammatory_corroborator() {
        val pattern = GrowthAndCompositionPatterns.cachexiaScreen
        val rhr = pattern.signalRules.first { it.metricType == MetricType.RESTING_HEART_RATE }
        assertEquals(DeviationDirection.ABOVE, rhr.direction)
    }

    @Test
    fun cachexia_excludes_paediatric_state() {
        val pattern = GrowthAndCompositionPatterns.cachexiaScreen
        assertTrue(
            "Cachexia screen is adult-and-up; paediatric owners use the failure-to-thrive screen instead",
            PhysiologyState.PAEDIATRIC in pattern.excludedStates
        )
    }

    @Test
    fun cachexia_carries_no_severity_floor() {
        // ADVISORY by default — escalation belongs to the clinical team.
        assertNull(GrowthAndCompositionPatterns.cachexiaScreen.severityFloor)
    }

    // -- alert-content-policy compliance --

    @Test
    fun every_growth_and_composition_pattern_passes_alert_content_policy() {
        GrowthAndCompositionPatterns.all.forEach { pattern ->
            val violations = AlertContentPolicy.validate(pattern)
            assertTrue(
                "Pattern ${pattern.id} violates AlertContentPolicy: " +
                    violations.joinToString { "${it.field}:'${it.prohibitedPhrase}'" },
                violations.isEmpty()
            )
        }
    }

    @Test
    fun citation_hygiene_every_rule_carries_citation() {
        for (pattern in GrowthAndCompositionPatterns.all) {
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
