package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import java.time.Instant
import java.time.ZoneId

/**
 * Derives one resting-heart-rate (RHR) value per calendar day from raw
 * HEART_RATE samples gated by STEPS activity.
 *
 * Method: for each HR sample, decide whether it sits in a "quiet window"
 * — no STEPS bucket with `value > 0` overlapping the sample, and none
 * ending inside a post-exertion cooldown preceding it. The day's RHR is
 * the [PERCENTILE]-th percentile of HR values inside its quiet windows,
 * emitted only when at least [MIN_QUIET_SAMPLES] samples qualify.
 *
 * Why a low percentile rather than the raw minimum: single-beat artifacts
 * and momentary contact dropouts produce isolated bpm samples in the 30s
 * that don't reflect physiological RHR. The 10th-percentile smoothes
 * across those without overshooting the genuine resting band.
 *
 * Why a cooldown: HR remains elevated for several minutes after activity
 * ends (vagal reactivation is gradual). Including those samples would
 * bias the percentile upward on active days; [COOLDOWN_MS] strips them.
 *
 * Step convention: this engine assumes STEPS readings carry per-bucket
 * counts — `value` is the step count over `[timestamp, timestamp +
 * durationSec]`, matching how HealthConnectAdapter, PhoneSensorAdapter,
 * and the API adapters emit them. Adapters that emit cumulative
 * running totals would appear as constant non-zero values and produce
 * no quiet windows; those owners would need a sensor-native RHR feed
 * (Garmin, Oura, Whoop all provide one) or manual entry.
 *
 * Pure JVM-only object — DB orchestration lives in
 * [com.bios.app.ingest.RestingHeartRateBackfill].
 */
object RestingHeartRateInference {

    /** One HR sample reduced to (timestamp, bpm). */
    data class HrSample(val timestamp: Long, val bpm: Double)

    /**
     * One STEPS bucket. `durationSec` is optional — instantaneous step
     * readings (some direct-sensor paths) are treated as a point event
     * at `timestamp`, with no width.
     */
    data class StepsBucket(val timestamp: Long, val value: Double, val durationSec: Int?)

    /** One derived RHR value for a calendar day. */
    data class RhrCandidate(
        val dayStartMs: Long,
        val rhrBpm: Double,
        val sampleCount: Int,
        val confidence: ConfidenceTier,
    )

    /**
     * Derive RHR per local-zone day from [hrSamples] gated by [stepsBuckets].
     *
     * Returns one [RhrCandidate] per day that has at least
     * [MIN_QUIET_SAMPLES] HR samples in quiet windows. Days with sparse
     * HR coverage, all-day activity, or no HR at all produce nothing —
     * an absent value is more honest than a confidently wrong one.
     */
    fun infer(
        hrSamples: List<HrSample>,
        stepsBuckets: List<StepsBucket>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<RhrCandidate> {
        if (hrSamples.isEmpty()) return emptyList()
        val activeBuckets = stepsBuckets
            .filter { it.value > 0.0 }
            .map { ActiveInterval(it.timestamp, it.timestamp + (it.durationSec?.toLong() ?: 0L) * 1000L) }
            .sortedBy { it.startMs }

        val quietByDay = hrSamples
            .asSequence()
            .filter { isQuiet(it.timestamp, activeBuckets) }
            .groupBy { sample ->
                Instant.ofEpochMilli(sample.timestamp).atZone(zone).toLocalDate()
            }

        return quietByDay.entries
            .mapNotNull { (date, samples) ->
                if (samples.size < MIN_QUIET_SAMPLES) return@mapNotNull null
                val rhr = percentile(samples.map { it.bpm }, PERCENTILE)
                val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                RhrCandidate(
                    dayStartMs = dayStartMs,
                    rhrBpm = rhr,
                    sampleCount = samples.size,
                    confidence = ConfidenceTier.MEDIUM,
                )
            }
            .sortedBy { it.dayStartMs }
    }

    /** Closed activity interval [startMs, endMs] for one steps bucket. */
    private data class ActiveInterval(val startMs: Long, val endMs: Long)

    /**
     * True when `t` is neither inside an active steps interval nor inside
     * the [COOLDOWN_MS] window after one ends. The check is bounded to
     * one descending lookup per HR sample by walking active intervals
     * back from the position where `startMs > t`.
     */
    private fun isQuiet(t: Long, activeIntervalsSortedByStart: List<ActiveInterval>): Boolean {
        if (activeIntervalsSortedByStart.isEmpty()) return true
        // Walk back from intervals starting at-or-before t. Any interval
        // whose endMs + cooldown is still in the future relative to t
        // disqualifies the sample.
        var idx = activeIntervalsSortedByStart.size - 1
        while (idx >= 0) {
            val interval = activeIntervalsSortedByStart[idx]
            if (interval.startMs > t) {
                idx--
                continue
            }
            // First interval starting ≤ t. Anything earlier ends earlier,
            // so the cooldown check on this one is sufficient.
            return interval.endMs + COOLDOWN_MS <= t
        }
        return true
    }

    /**
     * Linear-interpolated percentile. Sorted input, fractional index
     * between adjacent samples — avoids the discontinuity of a nearest-
     * rank percentile when the sample count is small.
     */
    internal fun percentile(values: List<Double>, percent: Double): Double {
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted[0]
        val rank = percent / 100.0 * (sorted.size - 1)
        val lo = rank.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.size - 1)
        val frac = rank - lo
        return sorted[lo] * (1.0 - frac) + sorted[hi] * frac
    }

    /** Post-exertion cooldown stripped from HR samples. 15 min covers the
     *  steep portion of vagal reactivation; longer would over-strip days
     *  with frequent light activity and leave too few quiet samples. */
    internal const val COOLDOWN_MS: Long = 15L * 60L * 1000L

    /** Minimum HR samples in a day's quiet windows for a credible
     *  percentile. Below this the percentile is too unstable to publish. */
    internal const val MIN_QUIET_SAMPLES: Int = 30

    /** Percentile used as the RHR. 10th balances "low enough to reflect
     *  rest" with "high enough to ignore single-beat dropouts." */
    internal const val PERCENTILE: Double = 10.0
}
