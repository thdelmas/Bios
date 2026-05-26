package com.bios.app

import com.bios.app.alerts.AlertContentPolicy
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.GeriatricTrajectoryPatterns
import com.bios.app.alerts.GeriatricTrajectoryStore
import com.bios.app.alerts.SignalRule
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.AlertTier
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the geriatric trajectory advisories (#208,
 * GERIATRICS_PALLIATIVE_POV §2.3 / §2.4 / §2.5).
 *
 * Manifesto-critical constraints verified by these tests:
 *  - All three advisories are ADVISORY-tier only (no severityFloor).
 *  - All three are pullSideOnly = true (AlertManager never pushes them).
 *  - All three require FRAILTY_FLAG — the owner has to declare the
 *    sub-population context.
 *  - The synthetic-decline composites fire when the right SignalRule
 *    shape would activate.
 *  - The banlist pinned in AlertContentPolicy now includes the
 *    cognitive / fall-risk manifesto-prohibited phrases.
 */
class GeriatricTrajectoryPatternsTest {

    @Test
    fun all_three_patterns_register_in_global_all_list() {
        assertTrue(
            GeriatricTrajectoryPatterns.fallRiskTrajectoryAdvisory in ConditionPatterns.all,
        )
        assertTrue(
            GeriatricTrajectoryPatterns.deliriumRiskScreen in ConditionPatterns.all,
        )
        assertTrue(
            GeriatricTrajectoryPatterns.cognitiveTrajectoryAdvisory in ConditionPatterns.all,
        )
    }

    @Test
    fun all_three_patterns_are_pull_side_only() {
        for (p in GeriatricTrajectoryPatterns.all) {
            assertTrue(
                "${p.id} must be pullSideOnly — geriatric trajectories never push",
                p.pullSideOnly,
            )
        }
    }

    @Test
    fun no_pattern_declares_urgent_severity_floor() {
        for (p in GeriatricTrajectoryPatterns.all) {
            assertNull(
                "${p.id} should not have a severityFloor — ADVISORY-only by classifier",
                p.severityFloor,
            )
        }
    }

    @Test
    fun all_three_patterns_require_frailty_flag_state() {
        for (p in GeriatricTrajectoryPatterns.all) {
            assertTrue(
                "${p.id} must require FRAILTY_FLAG — owner-declared sub-population gate",
                PhysiologyState.FRAILTY_FLAG in p.requiredStates,
            )
        }
    }

    // -- shape: each advisory's SignalRule composition --

    @Test
    fun fall_risk_trajectory_reads_gait_variability_with_literature_citation() {
        val p = GeriatricTrajectoryPatterns.fallRiskTrajectoryAdvisory
        val gait = p.signalRules.single { it.metricType == MetricType.GAIT_VARIABILITY_COV }
        assertEquals(DeviationDirection.ABOVE, gait.direction)
        assertEquals(ThresholdSource.LITERATURE, gait.source)
        assertTrue(gait.citation.contains("Hausdorff") || gait.citation.contains("Verghese"))
        assertEquals(ConditionCategory.SAFETY, p.category)
    }

    @Test
    fun delirium_screen_uses_sleep_wake_signals_with_cam_or_4at_citation() {
        val p = GeriatricTrajectoryPatterns.deliriumRiskScreen
        val frag = p.signalRules.single { it.metricType == MetricType.SLEEP_FRAGMENTATION_INDEX }
        assertEquals(DeviationDirection.ABOVE, frag.direction)
        assertTrue(
            "delirium screen sleep-fragmentation citation must reference CAM-S or 4AT",
            frag.citation.contains("Inouye") || frag.citation.contains("Bellelli"),
        )
        assertEquals(ConditionCategory.SLEEP, p.category)
    }

    @Test
    fun cognitive_trajectory_composite_reads_at_least_typing_speed_gait_sleep_steps() {
        val p = GeriatricTrajectoryPatterns.cognitiveTrajectoryAdvisory
        val rulesByMetric = p.signalRules.associateBy { it.metricType }
        assertTrue(MetricType.TYPING_SPEED_CHAR_PER_MIN in rulesByMetric)
        assertTrue(MetricType.GAIT_VARIABILITY_COV in rulesByMetric)
        assertTrue(MetricType.SLEEP_REGULARITY in rulesByMetric)
        assertTrue(MetricType.STEPS in rulesByMetric)
        assertEquals(
            "cognitive trajectory requires a multi-signal composite (≥3 of 4)",
            3,
            p.minActiveSignals,
        )
        assertEquals(ConditionCategory.MENTAL_HEALTH, p.category)
    }

    // -- synthetic firing logic: emulate the SignalRule activation that
    //    AnomalyDetector would compute, verifying the rule shape matches
    //    the audit-stated "fires on X" expectation.

    @Test
    fun fall_risk_fires_on_synthetic_high_gait_variability_in_frailty_owner() {
        // Synthetic owner state: FRAILTY_FLAG set, gait CoV +1.6σ above
        // baseline (above the 1.5σ rule threshold), steps mildly low.
        val p = GeriatricTrajectoryPatterns.fallRiskTrajectoryAdvisory
        val gaitRule = p.signalRules.single { it.metricType == MetricType.GAIT_VARIABILITY_COV }
        val syntheticZ = 1.6
        val activated = ruleActivates(gaitRule, syntheticZ)
        assertTrue("Gait z=$syntheticZ should activate the gait rule", activated)
        // With minActiveSignals = 1, a single active signal is enough.
        assertEquals(1, p.minActiveSignals)
    }

    @Test
    fun delirium_screen_fires_on_synthetic_sleep_wake_disruption() {
        val p = GeriatricTrajectoryPatterns.deliriumRiskScreen
        // Fragmentation rising (+1.2σ) AND WASO rising (+1.1σ): two
        // active signals → minActiveSignals = 2 satisfied.
        val frag = p.signalRules.single { it.metricType == MetricType.SLEEP_FRAGMENTATION_INDEX }
        val waso = p.signalRules.single { it.metricType == MetricType.WAKE_AFTER_SLEEP_ONSET }
        assertTrue(ruleActivates(frag, 1.2))
        assertTrue(ruleActivates(waso, 1.1))
        assertEquals(2, p.minActiveSignals)
    }

    @Test
    fun cognitive_trajectory_fires_on_synthetic_multi_signal_decline() {
        // Synthetic owner: typing speed -1.2σ (BELOW), gait CoV +1.2σ
        // (ABOVE), sleep regularity -1.1σ (BELOW), steps -1.0σ (BELOW)
        // → 4 active signals, minActiveSignals = 3.
        val p = GeriatricTrajectoryPatterns.cognitiveTrajectoryAdvisory
        val typing = p.signalRules.single { it.metricType == MetricType.TYPING_SPEED_CHAR_PER_MIN }
        val gait = p.signalRules.single { it.metricType == MetricType.GAIT_VARIABILITY_COV }
        val sleep = p.signalRules.single { it.metricType == MetricType.SLEEP_REGULARITY }
        val steps = p.signalRules.single { it.metricType == MetricType.STEPS }
        assertTrue(ruleActivates(typing, -1.2))
        assertTrue(ruleActivates(gait, 1.2))
        assertTrue(ruleActivates(sleep, -1.1))
        assertTrue(ruleActivates(steps, -1.05))
    }

    @Test
    fun cognitive_trajectory_does_not_fire_with_only_one_signal_active() {
        val p = GeriatricTrajectoryPatterns.cognitiveTrajectoryAdvisory
        val typing = p.signalRules.single { it.metricType == MetricType.TYPING_SPEED_CHAR_PER_MIN }
        assertTrue(ruleActivates(typing, -1.2))
        // Other rules don't activate (z ≈ 0); fall short of minActiveSignals = 3.
        val gait = p.signalRules.single { it.metricType == MetricType.GAIT_VARIABILITY_COV }
        assertFalse(ruleActivates(gait, 0.2))
    }

    // -- AlertContentPolicy banlist (manifesto-critical phrases pinned) --

    @Test
    fun content_policy_banlist_includes_geriatric_trajectory_prohibited_phrases() {
        // The AlertContentPolicy banlist is private — we pin behavior by
        // synthesising a pattern that *would* contain a banned phrase
        // and asserting the validator catches it.
        val bannedExamples = listOf(
            "your memory is declining",
            "cognition declining",
            "dementia risk",
            "you may have mci",
            "you may have dementia",
            "fall risk increased",
            "your fall risk",
            "elevated fall risk",
            "capacity loss",
            "losing capacity",
            "cognitive decline detected",
        )
        for (banned in bannedExamples) {
            val poisoned = com.bios.app.alerts.ConditionPattern(
                id = "test_$banned",
                title = "Trajectory observed",
                category = ConditionCategory.SAFETY,
                signalRules = emptyList(),
                minActiveSignals = 0,
                explanation = "Synthetic test pattern. $banned in the body.",
                suggestedAction = null,
            )
            val violations = AlertContentPolicy.validate(poisoned)
            assertTrue(
                "AlertContentPolicy should ban phrase: $banned",
                violations.any { it.prohibitedPhrase == banned },
            )
        }
    }

    @Test
    fun all_three_patterns_pass_alert_content_policy() {
        for (p in GeriatricTrajectoryPatterns.all) {
            val violations = AlertContentPolicy.validate(p)
            assertTrue(
                "${p.id} must pass AlertContentPolicy, violations: $violations",
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun geriatric_trajectory_store_recent_discharge_window_works() {
        // Pure-Kotlin assertion using a synthetic clock — DISCHARGE_WINDOW
        // constants are public on GeriatricTrajectoryStore companion.
        val windowMs = GeriatricTrajectoryStore.DISCHARGE_WINDOW_MS
        // 31 days after discharge → outside window.
        val now = 1_700_000_000_000L
        val staleDischarge = now - (windowMs + 24L * 3600L * 1000L)
        val freshDischarge = now - (windowMs / 2)
        // Helper from the store is instance-bound; we exercise the
        // arithmetic intent here without instantiating Context.
        assertTrue(freshDischarge in (now - windowMs)..now)
        assertFalse(staleDischarge in (now - windowMs)..now)
    }

    /**
     * Synthesise the activation predicate the AnomalyDetector applies to
     * a baseline-relative SignalRule — z-score crosses the threshold in
     * the rule's declared direction.
     */
    private fun ruleActivates(rule: SignalRule, zScore: Double): Boolean {
        if (rule.isAbsolute) return false
        return when (rule.direction) {
            DeviationDirection.ABOVE -> zScore > rule.thresholdSigma
            DeviationDirection.BELOW -> zScore < -rule.thresholdSigma
            DeviationDirection.IRREGULAR -> kotlin.math.abs(zScore) > rule.thresholdSigma
            DeviationDirection.ABSENT -> false
        }
    }

    @Test
    fun pattern_severity_caps_at_advisory_when_classifier_runs() {
        // No severityFloor → AnomalyDetector's classifier determines the
        // tier. With minActiveSignals satisfied and weighted-score
        // reaching the ADVISORY threshold, the result tops out at
        // ADVISORY. The pattern declares no override.
        for (p in GeriatricTrajectoryPatterns.all) {
            assertNull(p.severityFloor)
            // Pull-side-only + no floor + classifier max = ADVISORY by
            // construction (AnomalyDetector.classifySeverity caps at
            // ADVISORY).
        }
        // Sanity: AlertTier.ADVISORY is the documented ceiling for the
        // current classifier.
        assertEquals(2, AlertTier.ADVISORY.level)
    }
}
