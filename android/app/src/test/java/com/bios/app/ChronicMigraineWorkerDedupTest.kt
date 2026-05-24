package com.bios.app

import com.bios.app.alerts.ChronicMigraineEvaluator
import com.bios.app.alerts.ChronicMigraineWorker
import com.bios.app.alerts.ChronicMigraineWorker.Companion.FireDecision
import com.bios.app.alerts.ChronicMigraineWorker.Companion.PreviousState
import com.bios.app.alerts.HeadachePatterns
import com.bios.app.model.AlertTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of the chronic-migraine worker's dedup logic and
 * Anomaly construction (#283 Cut 3). Mirrors the MOH worker dedup
 * tests one-to-one; the chronic-migraine pattern shares the same
 * fire-on-transition / re-fire-after-ack semantics.
 */
class ChronicMigraineWorkerDedupTest {

    private val verdictTrue = ChronicMigraineEvaluator.Verdict(
        meetsScreeningThreshold = true,
        perMonthCounts = emptyList(),
    )
    private val verdictFalse = ChronicMigraineEvaluator.Verdict(
        meetsScreeningThreshold = false,
        perMonthCounts = emptyList(),
    )

    @Test
    fun verdict_false_always_suppresses_and_clears_state() {
        val decision = ChronicMigraineWorker.decideFireOrSkip(
            verdict = verdictFalse,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "a1"),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.SuppressVerdictFalse, decision)
    }

    @Test
    fun verdict_true_after_false_state_fires() {
        // The canonical false→true transition. Fire.
        val decision = ChronicMigraineWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = false, lastAnomalyId = null),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.Fire, decision)
    }

    @Test
    fun verdict_true_with_unacked_existing_alert_suppresses() {
        // The previously-fired alert is still on the dashboard waiting
        // for the owner to ack it. Don't double-fire.
        val decision = ChronicMigraineWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "a1"),
            previouslyAcked = false,
        )
        assertEquals(FireDecision.SuppressVerdictUnchanged, decision)
    }

    @Test
    fun verdict_true_after_ack_re_fires() {
        // Owner acked the previous alert and the pattern still holds —
        // re-surface so it doesn't silently disappear.
        val decision = ChronicMigraineWorker.decideFireOrSkip(
            verdict = verdictTrue,
            previousState = PreviousState(verdictTrue = true, lastAnomalyId = "a1"),
            previouslyAcked = true,
        )
        assertEquals(FireDecision.Fire, decision)
    }

    @Test
    fun buildAnomaly_emits_ADVISORY_severity_and_anchored_pattern_text() {
        val verdict = ChronicMigraineEvaluator.Verdict(
            meetsScreeningThreshold = true,
            perMonthCounts = listOf(
                ChronicMigraineEvaluator.MonthCount(2026, 2, 16, 9),
                ChronicMigraineEvaluator.MonthCount(2026, 1, 18, 10),
                ChronicMigraineEvaluator.MonthCount(2025, 12, 20, 12),
            ),
        )
        val anomaly = ChronicMigraineWorker.buildAnomaly(verdict, nowMillis = 1_700_000_000_000L)
        assertEquals(AlertTier.ADVISORY.level, anomaly.severity)
        assertEquals(HeadachePatterns.chronicMigraineThreshold.id, anomaly.patternId)
        assertEquals(HeadachePatterns.chronicMigraineThreshold.title, anomaly.title)
        // Per-month substrate appended so the detail surface can render
        // the actual counts that triggered the fire.
        assertTrue(anomaly.explanation.contains("16 headache days (9 migraine)"))
    }
}
