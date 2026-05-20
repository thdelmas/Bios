package com.bios.app

import com.bios.app.engine.AnomalyModelComparison
import com.bios.app.engine.TFLiteAnomalyModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Pins the A/B comparison recorder (#1). Singleton in production, so
 * tests reset between cases via the internal entry point.
 *
 * Covers: empty-snapshot semantics, per-quadrant tallying around
 * [TFLiteAnomalyModel.ANOMALY_THRESHOLD], delta mean / stddev
 * accumulation, last-seen scores survive across calls, disagreement
 * rate calculation.
 */
class AnomalyModelComparisonTest {

    private val threshold = TFLiteAnomalyModel.ANOMALY_THRESHOLD

    @Before
    fun setUp() {
        AnomalyModelComparison.reset()
    }

    @After
    fun tearDown() {
        AnomalyModelComparison.reset()
    }

    @Test
    fun `empty snapshot has zero totals and zero disagreement`() {
        val s = AnomalyModelComparison.snapshot()
        assertEquals(0L, s.total)
        assertEquals(0L, s.agreeBelow)
        assertEquals(0L, s.agreeAbove)
        assertEquals(0L, s.onlyMlAbove)
        assertEquals(0L, s.onlyHeuristicAbove)
        assertEquals(0.0, s.disagreementRate, 1e-9)
        assertNull(s.lastMlScore)
        assertNull(s.lastHeuristicScore)
        assertNull(s.lastRecordedAtMs)
    }

    @Test
    fun `both below threshold count as agree below`() {
        AnomalyModelComparison.record(mlScore = 0.1f, heuristicScore = 0.2f)
        AnomalyModelComparison.record(mlScore = 0.3f, heuristicScore = 0.4f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(2L, s.total)
        assertEquals(2L, s.agreeBelow)
        assertEquals(0L, s.agreeAbove)
        assertEquals(0L, s.onlyMlAbove)
        assertEquals(0L, s.onlyHeuristicAbove)
        assertEquals(0.0, s.disagreementRate, 1e-9)
    }

    @Test
    fun `both above threshold count as agree above`() {
        AnomalyModelComparison.record(mlScore = 0.9f, heuristicScore = 0.8f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(1L, s.total)
        assertEquals(1L, s.agreeAbove)
        assertEquals(0L, s.agreeBelow)
        assertEquals(0L, s.onlyMlAbove)
        assertEquals(0L, s.onlyHeuristicAbove)
    }

    @Test
    fun `only ML above is recorded when ML fires alone`() {
        // ML score >= threshold, heuristic < threshold ⇒ onlyMlAbove.
        AnomalyModelComparison.record(mlScore = threshold + 0.05f, heuristicScore = threshold - 0.1f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(1L, s.total)
        assertEquals(1L, s.onlyMlAbove)
        assertEquals(0L, s.onlyHeuristicAbove)
        assertEquals(0L, s.agreeAbove)
    }

    @Test
    fun `only heuristic above is recorded when heuristic fires alone`() {
        AnomalyModelComparison.record(mlScore = threshold - 0.05f, heuristicScore = threshold + 0.1f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(1L, s.total)
        assertEquals(1L, s.onlyHeuristicAbove)
        assertEquals(0L, s.onlyMlAbove)
    }

    @Test
    fun `exact threshold counts as above`() {
        // Boundary semantics: >= threshold is "above". Matches the
        // decision rule the AnomalyDetector applies to the live score.
        AnomalyModelComparison.record(mlScore = threshold, heuristicScore = threshold)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(1L, s.agreeAbove)
        assertEquals(0L, s.agreeBelow)
    }

    @Test
    fun `disagreement rate is the disagreement count over total`() {
        // 4 records: 2 agree, 1 only-ML, 1 only-heuristic ⇒ rate = 0.5.
        AnomalyModelComparison.record(0.9f, 0.9f)
        AnomalyModelComparison.record(0.1f, 0.1f)
        AnomalyModelComparison.record(threshold + 0.1f, threshold - 0.1f)
        AnomalyModelComparison.record(threshold - 0.1f, threshold + 0.1f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(4L, s.total)
        assertEquals(0.5, s.disagreementRate, 1e-9)
    }

    @Test
    fun `delta mean and stddev accumulate across samples`() {
        // Deltas: +0.10, +0.20, +0.30 ⇒ mean 0.20, population stddev
        // sqrt((0.01 + 0 + 0.01) / 3) ≈ 0.0816.
        AnomalyModelComparison.record(mlScore = 0.10f, heuristicScore = 0.00f)
        AnomalyModelComparison.record(mlScore = 0.30f, heuristicScore = 0.10f)
        AnomalyModelComparison.record(mlScore = 0.50f, heuristicScore = 0.20f)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(3L, s.total)
        assertTrue(
            "mean delta ${s.meanDelta} should be near 0.20",
            abs(s.meanDelta - 0.20) < 1e-3
        )
        assertTrue(
            "stddev delta ${s.stddevDelta} should be near 0.0816",
            abs(s.stddevDelta - 0.0816) < 1e-3
        )
    }

    @Test
    fun `last seen scores expose the most recent comparison`() {
        AnomalyModelComparison.record(mlScore = 0.10f, heuristicScore = 0.20f)
        AnomalyModelComparison.record(mlScore = 0.70f, heuristicScore = 0.40f, nowMs = 12345L)
        val s = AnomalyModelComparison.snapshot()
        assertEquals(0.70f, s.lastMlScore!!, 1e-6f)
        assertEquals(0.40f, s.lastHeuristicScore!!, 1e-6f)
        assertEquals(12345L, s.lastRecordedAtMs)
    }

    @Test
    fun `reset clears every counter and the last-seen state`() {
        AnomalyModelComparison.record(mlScore = 0.9f, heuristicScore = 0.1f)
        AnomalyModelComparison.reset()
        val s = AnomalyModelComparison.snapshot()
        assertEquals(0L, s.total)
        assertEquals(0L, s.onlyMlAbove)
        assertEquals(0.0, s.meanDelta, 1e-9)
        assertEquals(0.0, s.stddevDelta, 1e-9)
        assertNull(s.lastMlScore)
        assertNull(s.lastRecordedAtMs)
    }

    @Test
    fun `snapshot is a value type and survives subsequent records`() {
        AnomalyModelComparison.record(0.9f, 0.9f)
        val first = AnomalyModelComparison.snapshot()
        AnomalyModelComparison.record(0.1f, 0.1f)
        // The first snapshot captures the state at the time it was
        // taken — the data class is immutable.
        assertEquals(1L, first.total)
        val second = AnomalyModelComparison.snapshot()
        assertEquals(2L, second.total)
        assertNotNull(second.lastMlScore)
    }
}
