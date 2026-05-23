package com.bios.app

import com.bios.app.alerts.HeadachePatterns
import com.bios.app.alerts.MedicationOveruseHeadacheEvaluator
import com.bios.app.alerts.MohScreeningWorker
import com.bios.app.alerts.MohScreeningWorker.Companion.FireDecision
import com.bios.app.alerts.MohScreeningWorker.Companion.PreviousState
import com.bios.app.model.AlertTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [MohScreeningWorker] dedup decision and Anomaly-shape
 * helpers. Covers the four transitions called out in #276:
 *
 *   - verdict false → no fire (state reset)
 *   - verdict false→true → fire
 *   - verdict true→true without ack → no fire (already surfaced)
 *   - verdict true→true after ack → fire (re-surface)
 *
 * Plus the [MohScreeningWorker.buildAnomaly] shape so the alert text
 * surface stays anchored to the pattern declaration.
 */
class MohScreeningWorkerTest {

    private val verdictTrue = MedicationOveruseHeadacheEvaluator.Verdict(
        meetsScreeningThreshold = true,
        perMonthCounts = listOf(
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 5, 14),
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 4, 12),
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 3, 11),
        ),
    )

    private val verdictFalse = MedicationOveruseHeadacheEvaluator.Verdict(
        meetsScreeningThreshold = false,
        perMonthCounts = listOf(
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 5, 5),
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 4, 8),
            MedicationOveruseHeadacheEvaluator.MonthCount(2026, 3, 4),
        ),
    )

    @Test
    fun false_verdict_suppresses_fire_and_signals_state_reset() {
        val decision = MohScreeningWorker.decideFireOrSkip(
            verdict = verdictFalse,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "prev"),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.SuppressVerdictFalse, decision)
    }

    @Test
    fun first_true_verdict_fires() {
        val decision = MohScreeningWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = false, lastAnomalyId = null),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.Fire, decision)
    }

    @Test
    fun continued_true_verdict_does_not_refire_without_ack() {
        val decision = MohScreeningWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "prev"),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.SuppressVerdictUnchanged, decision)
    }

    @Test
    fun continued_true_verdict_refires_after_ack() {
        val decision = MohScreeningWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "prev"),
            previouslyAcked = true,
        )
        assertEquals(FireDecision.Fire, decision)
    }

    @Test
    fun false_to_true_transition_fires_after_state_reset() {
        // Mimics the lifecycle: verdict went true, then false (state
        // reset), then back to true on a later tick.
        val decision = MohScreeningWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = false, lastAnomalyId = null),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.Fire, decision)
    }

    @Test
    fun built_anomaly_matches_pattern_declaration() {
        val now = 1_716_000_000_000L
        val anomaly = MohScreeningWorker.buildAnomaly(verdictTrue, now)
        val pattern = HeadachePatterns.medicationOveruseHeadacheScreen
        assertEquals(pattern.id, anomaly.patternId)
        assertEquals(pattern.title, anomaly.title)
        assertEquals(pattern.suggestedAction, anomaly.suggestedAction)
        assertEquals(AlertTier.ADVISORY.level, anomaly.severity)
        assertEquals(now, anomaly.detectedAt)
        // The verdict substrate (per-month counts + IHS threshold) lands
        // appended to the static pattern explanation so the alert detail
        // surface can render both.
        assertTrue(anomaly.explanation.startsWith(pattern.explanation))
        assertTrue(anomaly.explanation.contains("May 2026: 14 acute-treatment days"))
        assertTrue(anomaly.explanation.contains("IHS ICHD-3 §8.2 screening threshold"))
        // No metric streams; the JSON columns stay empty.
        assertEquals("[]", anomaly.metricTypes)
        assertEquals("{}", anomaly.deviationScores)
    }
}
