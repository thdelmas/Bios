package com.bios.app.engine

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * A/B comparison harness between the trained TFLite anomaly model and
 * the heuristic z-score fallback (#1).
 *
 * The heuristic stayed alongside the trained model as a safety net; this
 * recorder is how Bios catches the case where they disagree. Every call
 * to [record] keeps both scores in an in-memory tally and the snapshot
 * exposed on the pipeline-health surface reports:
 *
 *  - `total`: how many detections compared the two
 *  - `agreeBelow` / `agreeAbove`: both classifiers landed on the same
 *    side of [TFLiteAnomalyModel.ANOMALY_THRESHOLD]
 *  - `onlyMlAbove` / `onlyHeuristicAbove`: one fires while the other
 *    doesn't — the rows worth a human look
 *  - mean and stddev of `(ml − heuristic)`: drift between the two
 *
 * In-memory only; resets on process restart. Persistence and a "review
 * disagreements" log are open follow-ups — the goal here is the
 * comparison signal itself.
 *
 * Thread-safety: counters are atomic longs; the running sums are
 * synchronized under [lock] because mean/stddev need a consistent
 * snapshot across the four totals. Detection runs are infrequent (one
 * per sync) so contention is a non-issue.
 */
object AnomalyModelComparison {

    private val total = AtomicLong(0)
    private val agreeBelowCount = AtomicLong(0)
    private val agreeAboveCount = AtomicLong(0)
    private val onlyMlAboveCount = AtomicLong(0)
    private val onlyHeuristicAboveCount = AtomicLong(0)

    private val lock = Any()
    private var deltaSum = 0.0
    private var deltaSqSum = 0.0
    private var lastMlScore: Float? = null
    private var lastHeuristicScore: Float? = null
    private var lastRecordedAtMs: Long? = null

    /** Snapshot of the comparison at a point in time. */
    data class Snapshot(
        val total: Long,
        val agreeBelow: Long,
        val agreeAbove: Long,
        val onlyMlAbove: Long,
        val onlyHeuristicAbove: Long,
        val meanDelta: Double,
        val stddevDelta: Double,
        val lastMlScore: Float?,
        val lastHeuristicScore: Float?,
        val lastRecordedAtMs: Long?,
    ) {
        /** Fraction (0..1) of recorded detections where the two scores
         *  landed on opposite sides of the threshold. */
        val disagreementRate: Double
            get() = if (total == 0L) 0.0 else (onlyMlAbove + onlyHeuristicAbove).toDouble() / total
    }

    /**
     * Record one pair of scores. Both are interpreted at
     * [TFLiteAnomalyModel.ANOMALY_THRESHOLD] for the agreement / disagree
     * tallies; the raw scores feed the delta statistics.
     */
    fun record(mlScore: Float, heuristicScore: Float, nowMs: Long = System.currentTimeMillis()) {
        val threshold = TFLiteAnomalyModel.ANOMALY_THRESHOLD
        val mlAbove = mlScore >= threshold
        val heurAbove = heuristicScore >= threshold

        total.incrementAndGet()
        when {
            !mlAbove && !heurAbove -> agreeBelowCount.incrementAndGet()
            mlAbove && heurAbove -> agreeAboveCount.incrementAndGet()
            mlAbove && !heurAbove -> onlyMlAboveCount.incrementAndGet()
            !mlAbove && heurAbove -> onlyHeuristicAboveCount.incrementAndGet()
        }

        val delta = (mlScore - heuristicScore).toDouble()
        synchronized(lock) {
            deltaSum += delta
            deltaSqSum += delta * delta
            lastMlScore = mlScore
            lastHeuristicScore = heuristicScore
            lastRecordedAtMs = nowMs
        }
    }

    /** Current snapshot. Cheap; safe to call from the UI. */
    fun snapshot(): Snapshot {
        val n = total.get()
        val (mean, stddev) = synchronized(lock) {
            if (n == 0L) 0.0 to 0.0
            else {
                val m = deltaSum / n
                val variance = (deltaSqSum / n - m * m).coerceAtLeast(0.0)
                m to sqrt(variance)
            }
        }
        return Snapshot(
            total = n,
            agreeBelow = agreeBelowCount.get(),
            agreeAbove = agreeAboveCount.get(),
            onlyMlAbove = onlyMlAboveCount.get(),
            onlyHeuristicAbove = onlyHeuristicAboveCount.get(),
            meanDelta = mean,
            stddevDelta = stddev,
            lastMlScore = lastMlScore,
            lastHeuristicScore = lastHeuristicScore,
            lastRecordedAtMs = lastRecordedAtMs,
        )
    }

    /** Reset everything. Test-only entry point — production never resets. */
    internal fun reset() {
        total.set(0)
        agreeBelowCount.set(0)
        agreeAboveCount.set(0)
        onlyMlAboveCount.set(0)
        onlyHeuristicAboveCount.set(0)
        synchronized(lock) {
            deltaSum = 0.0
            deltaSqSum = 0.0
            lastMlScore = null
            lastHeuristicScore = null
            lastRecordedAtMs = null
        }
    }
}
