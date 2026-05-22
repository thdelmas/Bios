package com.bios.app

import com.bios.app.alerts.ConditionPattern
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.alerts.HeartFailureDecompensationPattern
import com.bios.app.alerts.SignalRule
import com.bios.app.alerts.ThresholdSource
import com.bios.app.model.ConditionCategory
import com.bios.app.physiology.PhysiologyState
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the HeartLogic-lite heart-failure decompensation prodrome pattern
 * (CARDIOLOGY_POV.md §2.4 — closes issue #187). Covers:
 *
 *  - **Registration** — pattern is in [ConditionPatterns.all] so the
 *    [com.bios.app.engine.AnomalyDetector] actually runs it (guards against
 *    the Phase 4 regression where four patterns were defined but never
 *    listed).
 *  - **Shape** — 4 SignalRules, `minActiveSignals = 3`, ADVISORY default
 *    (no `severityFloor` per cardiology audit §3.2), CARDIOVASCULAR
 *    category, every rule literature-cited.
 *  - **Gating** — `requiredStates = {KNOWN_HF}` so the pattern only runs
 *    for owners who have affirmed a HF diagnosis. Asserts via the shared
 *    [appliesIn] helper that the pattern is applicable in [KNOWN_HF] and
 *    silent in every other state (STANDARD, pregnancy, athlete, etc.).
 *  - **Synthetic-input convergence** — pure-logic simulation of the
 *    AnomalyDetector's rule-firing semantics over four scenarios:
 *      1. 4-of-4 signals positive (full prodrome) → fires.
 *      2. 3-of-4 signals positive (minimum trigger) → fires.
 *      3. 1 signal positive (single-signal noise) → doesn't fire.
 *      4. 4-of-4 signals positive but state ≠ KNOWN_HF → doesn't fire
 *         (the requiredStates gate makes the substrate invisible).
 */
class HeartFailureDecompensationPatternTest {

    private val pattern: ConditionPattern =
        HeartFailureDecompensationPattern.decompensationProdrome

    // -- registration --

    @Test
    fun pattern_registers_in_global_all_list() {
        assertTrue(
            "HF decompensation prodrome must be listed in ConditionPatterns.all " +
                "or the AnomalyDetector will silently never run it (Phase 4 regression shape).",
            HeartFailureDecompensationPattern.decompensationProdrome in ConditionPatterns.all,
        )
    }

    @Test
    fun pattern_is_in_HeartFailureDecompensationPattern_all_list() {
        assertTrue(pattern in HeartFailureDecompensationPattern.all)
    }

    // -- shape --

    @Test
    fun pattern_id_is_stable_kebab_id() {
        assertEquals("hf_decompensation_prodrome", pattern.id)
    }

    @Test
    fun pattern_category_is_cardiovascular() {
        assertEquals(ConditionCategory.CARDIOVASCULAR, pattern.category)
    }

    @Test
    fun pattern_has_four_signal_rules() {
        assertEquals(4, pattern.signalRules.size)
    }

    @Test
    fun pattern_requires_at_least_3_of_4_signals() {
        assertEquals(3, pattern.minActiveSignals)
    }

    @Test
    fun pattern_declares_no_severity_floor() {
        // Cardiology audit §3.2 + Emergency Medicine audit: HF
        // decompensation prodrome is ADVISORY-tier (data-statement +
        // referral), not URGENT — a 7-day window isn't a minute-scale
        // emergency. The classifier output drives severity.
        assertNull(
            "HF prodrome must not declare a severity floor; classifier output drives ADVISORY",
            pattern.severityFloor,
        )
    }

    // -- gating (PhysiologyState.KNOWN_HF) --

    @Test
    fun pattern_required_state_is_known_hf() {
        assertEquals(setOf(PhysiologyState.KNOWN_HF), pattern.requiredStates)
    }

    @Test
    fun pattern_applies_only_when_owner_has_set_known_hf() {
        // The single state where the pattern is meaningful.
        assertTrue(pattern.appliesIn(PhysiologyState.KNOWN_HF))
    }

    @Test
    fun pattern_does_not_apply_in_default_standard_adult_state() {
        // The default state for every owner who has never opened the
        // physiology-state picker — the false-positive case that the
        // gate exists to prevent.
        assertFalse(pattern.appliesIn(PhysiologyState.STANDARD))
    }

    @Test
    fun pattern_does_not_apply_in_any_non_hf_state() {
        // Sweep every non-KNOWN_HF state and confirm the gate silences
        // the pattern in all of them.
        for (state in PhysiologyState.entries) {
            if (state == PhysiologyState.KNOWN_HF) continue
            assertFalse(
                "Pattern must not apply in $state — gating is the manifesto guard against inferred HF",
                pattern.appliesIn(state),
            )
        }
    }

    // -- citation hygiene (issue #187: cite Boehmer 2017, AHA/HFSA 2017, Mishra 2020) --

    @Test
    fun every_signal_rule_is_literature_sourced_with_citation() {
        for (rule in pattern.signalRules) {
            assertEquals(
                "${pattern.id}/${rule.metricType.key} should be LITERATURE-sourced",
                ThresholdSource.LITERATURE,
                rule.source,
            )
            assertTrue(
                "${pattern.id}/${rule.metricType.key} should ship a non-blank citation",
                rule.citation.isNotBlank(),
            )
        }
    }

    @Test
    fun references_cite_boehmer_2017_aha_hfsa_2017_and_mishra_2020() {
        val refs = pattern.references.joinToString(" ")
        assertTrue(
            "Boehmer 2017 MultiSENSE / HeartLogic must appear in references",
            refs.contains("Boehmer", ignoreCase = true) ||
                refs.contains("MultiSENSE", ignoreCase = true) ||
                refs.contains("HeartLogic", ignoreCase = true),
        )
        // AHA/HFSA 2017 HF guideline ships under the Yancy 2017 citation
        // (ACC/AHA/HFSA Focused Update); either label is acceptable.
        assertTrue(
            "AHA/HFSA 2017 weight-gain guideline must appear in references",
            refs.contains("HFSA", ignoreCase = true) ||
                refs.contains("Yancy", ignoreCase = true) ||
                refs.contains("2017", ignoreCase = true),
        )
        assertTrue(
            "Mishra 2020 RHR prodrome primitive must appear in references",
            refs.contains("Mishra", ignoreCase = true),
        )
    }

    // -- per-rule shape --

    @Test
    fun rhr_rule_above_baseline_over_7_day_window() {
        val rhr = pattern.signalRules.first { it.metricType == MetricType.RESTING_HEART_RATE }
        assertEquals(DeviationDirection.ABOVE, rhr.direction)
        assertEquals(168, rhr.minDurationHours) // 7 * 24
        assertEquals(1.0, rhr.thresholdSigma, 1e-9)
    }

    @Test
    fun respiratory_rate_rule_above_baseline_over_5_day_window() {
        val rr = pattern.signalRules.first { it.metricType == MetricType.RESPIRATORY_RATE }
        assertEquals(DeviationDirection.ABOVE, rr.direction)
        assertEquals(120, rr.minDurationHours) // 5 * 24
        assertEquals(1.0, rr.thresholdSigma, 1e-9)
    }

    @Test
    fun activity_rule_below_baseline_over_7_day_window() {
        val activity = pattern.signalRules.first { it.metricType == MetricType.ACTIVE_MINUTES }
        assertEquals(DeviationDirection.BELOW, activity.direction)
        assertEquals(168, activity.minDurationHours)
        assertEquals(1.0, activity.thresholdSigma, 1e-9)
    }

    @Test
    fun body_mass_rule_above_baseline_over_72_hour_window() {
        // AHA/HFSA 2017: +1.5 kg in 3 days. Approximated by a +1.5σ rise
        // against the rolling BW baseline over 72 h.
        val weight = pattern.signalRules.first { it.metricType == MetricType.BODY_MASS }
        assertEquals(DeviationDirection.ABOVE, weight.direction)
        assertEquals(72, weight.minDurationHours)
        assertEquals(1.5, weight.thresholdSigma, 1e-9)
    }

    // -- AlertContentPolicy compliance: handled at registry level by
    //    AlertContentPolicyTest.kt; this test guards the policy holds for the
    //    new pattern in isolation too.

    @Test
    fun pattern_content_passes_alert_content_policy() {
        val violations = com.bios.app.alerts.AlertContentPolicy.validate(pattern)
        assertTrue(
            "HF prodrome pattern violates AlertContentPolicy: " +
                violations.joinToString("\n") { "${it.field}: '${it.prohibitedPhrase}' @ ${it.context}" },
            violations.isEmpty(),
        )
    }

    @Test
    fun suggested_action_references_hf_clinician_not_directive_phrasing() {
        val action = pattern.suggestedAction
        assertNotNull(action)
        assertTrue(
            "Suggested action must refer the reader to their HF clinician (data + referral, not directive)",
            action!!.contains("clinician", ignoreCase = true) ||
                action.contains("HF team", ignoreCase = true),
        )
    }

    // -- synthetic-input convergence: pure-function simulation --

    /**
     * Mirrors [com.bios.app.engine.AnomalyDetector.evaluatePattern]'s rule-
     * firing semantics for baseline-relative rules. Returns the number of
     * active signals given a map of `metricType → z-score`. Pattern fires
     * when `activeCount >= pattern.minActiveSignals` (the
     * AnomalyDetector also requires `requiredStates` to admit the pattern;
     * the synthetic test calls out the gating axis separately).
     */
    private fun simulateActiveSignals(
        rules: List<SignalRule>,
        zScores: Map<MetricType, Double>,
    ): Int {
        var active = 0
        for (rule in rules) {
            val z = zScores[rule.metricType] ?: continue
            val fires = when (rule.direction) {
                DeviationDirection.ABOVE -> z > rule.thresholdSigma
                DeviationDirection.BELOW -> z < -rule.thresholdSigma
                DeviationDirection.IRREGULAR -> abs(z) > rule.thresholdSigma
                DeviationDirection.ABSENT -> false // not used by this pattern
            }
            if (fires) active++
        }
        return active
    }

    @Test
    fun synthetic_4_of_4_signals_fires_when_state_is_known_hf() {
        // All four signals firmly above their thresholds.
        val zScores = mapOf(
            MetricType.RESTING_HEART_RATE to 1.5,   // > 1.0σ ABOVE
            MetricType.RESPIRATORY_RATE to 1.4,     // > 1.0σ ABOVE
            MetricType.ACTIVE_MINUTES to -1.6,      // < -1.0σ BELOW
            MetricType.BODY_MASS to 1.8,            // > 1.5σ ABOVE (~AHA/HFSA 1.5 kg threshold)
        )
        val active = simulateActiveSignals(pattern.signalRules, zScores)
        assertEquals("All four signals should be active", 4, active)
        assertTrue(
            "4-of-4 must satisfy minActiveSignals=3",
            active >= pattern.minActiveSignals,
        )
        // Owner is in KNOWN_HF → pattern is allowed to run.
        assertTrue(pattern.appliesIn(PhysiologyState.KNOWN_HF))
    }

    @Test
    fun synthetic_3_of_4_signals_fires_when_state_is_known_hf() {
        // Three signals active, one quiet (RR holding baseline).
        val zScores = mapOf(
            MetricType.RESTING_HEART_RATE to 1.3,   // > 1.0σ ABOVE → fires
            MetricType.RESPIRATORY_RATE to 0.2,     // baseline noise → quiet
            MetricType.ACTIVE_MINUTES to -1.4,      // < -1.0σ BELOW → fires
            MetricType.BODY_MASS to 1.7,            // > 1.5σ ABOVE → fires
        )
        val active = simulateActiveSignals(pattern.signalRules, zScores)
        assertEquals("Exactly three signals should be active", 3, active)
        assertTrue(
            "3-of-4 must satisfy minActiveSignals=3 (minimum trigger)",
            active >= pattern.minActiveSignals,
        )
        assertTrue(pattern.appliesIn(PhysiologyState.KNOWN_HF))
    }

    @Test
    fun synthetic_single_signal_noise_does_not_fire() {
        // Only one signal nudges the threshold — typical day-to-day noise.
        // The 3-signal gate is what protects against firing on
        // post-meal-fluctuation weight blips, single bad sleep nights, etc.
        val zScores = mapOf(
            MetricType.RESTING_HEART_RATE to 0.4,   // sub-threshold
            MetricType.RESPIRATORY_RATE to 0.6,     // sub-threshold
            MetricType.ACTIVE_MINUTES to -0.5,      // sub-threshold
            MetricType.BODY_MASS to 1.6,            // > 1.5σ ABOVE → only this one fires
        )
        val active = simulateActiveSignals(pattern.signalRules, zScores)
        assertEquals("Exactly one signal should be active", 1, active)
        assertFalse(
            "1-of-4 must not satisfy minActiveSignals=3",
            active >= pattern.minActiveSignals,
        )
    }

    @Test
    fun synthetic_4_of_4_with_no_known_hf_state_is_silenced_by_gate() {
        // Same convergent signals as the 4-of-4 case above — but the
        // owner has NOT set KNOWN_HF. The requiredStates gate must
        // silence the pattern regardless of how strong the signals are.
        // This is the false-positive case that the gate exists to
        // prevent: a 32-year-old with viral illness would otherwise
        // light up every signal and get a clinically irrelevant alert.
        val zScores = mapOf(
            MetricType.RESTING_HEART_RATE to 1.5,
            MetricType.RESPIRATORY_RATE to 1.4,
            MetricType.ACTIVE_MINUTES to -1.6,
            MetricType.BODY_MASS to 1.8,
        )
        val active = simulateActiveSignals(pattern.signalRules, zScores)
        assertTrue("Signal substrate is 4-of-4 active", active == 4)
        // But the gate suppresses the pattern in every non-KNOWN_HF state.
        for (state in PhysiologyState.entries) {
            if (state == PhysiologyState.KNOWN_HF) continue
            assertFalse(
                "Pattern must not apply in $state even with 4/4 signals — gate is non-negotiable",
                pattern.appliesIn(state),
            )
        }
    }
}
