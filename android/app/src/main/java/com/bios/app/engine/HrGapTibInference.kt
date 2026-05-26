package com.bios.app.engine

import com.bios.app.model.ConfidenceTier
import java.time.Instant
import java.time.ZoneId

/**
 * Rule-based `TIME_IN_BED` inference from gaps in the heart-rate stream.
 *
 * Motivation: owners who remove their wearable to sleep leave a multi-hour
 * absence of HR samples overnight. That absence is honestly a "lying down
 * with watch off" signal — never claim it as `SLEEP_DURATION`, because the
 * watch could equally be on the nightstand charger while the owner reads,
 * watches a movie, or has insomnia. Time-in-bed is the honest framing
 * (matches the in-bed-but-awake semantics of TIB in the existing
 * sleep-efficiency derivation in [SleepDerivations.deriveSleepEfficiency]).
 *
 * Confidence ladder:
 *  - LOW when only the HR-gap is present
 *  - MEDIUM when a same-night companion-written `sleep_duration` window
 *    (e.g. W2F's screen-off derivation) overlaps the gap
 *
 * Nocturnal anchor: a gap is only emitted as a TIB candidate if it spans
 * local-time 02:00–05:00 on some day inside the gap. This is the cheapest
 * filter that catches "watch off for sleep" without producing TIB rows
 * for a 4-hour afternoon meeting or a long drive. It misses owners whose
 * sleep window never crosses 02:00–05:00 local — primarily night-shift
 * workers — for whom the toggle surface is the escape hatch (disable
 * BIOS_INFERRED→TIME_IN_BED until the engine learns their schedule).
 *
 * Pure JVM-only object so unit tests don't need an Android device. The
 * DB-aware orchestration lives in [com.bios.app.ingest.HrGapTibBackfill].
 */
object HrGapTibInference {

    /** Heart-rate sample reduced to just its timestamp — gap detection
     *  doesn't care about the bpm value. */
    data class HrSample(val timestamp: Long)

    /** Companion-reported sleep window. Companions (W2F today) write
     *  `sleep_duration` at the window midpoint with `durationSec` covering
     *  the full sleep span, so the start/end of the in-bed period are
     *  derived as `midpoint ± durationSec/2`. */
    data class SleepWindow(val midpointMs: Long, val durationSec: Int)

    /** One inferred TIB window. */
    data class TibCandidate(
        val startMs: Long,
        val endMs: Long,
        val confidence: ConfidenceTier,
        val corroboratedByCompanionSleep: Boolean,
    ) {
        val durationSec: Int get() = ((endMs - startMs) / 1000L).toInt()
    }

    /**
     * Scan [hrSamples] for gaps ≥ [minGapMs] that overlap the nocturnal
     * anchor. For each surviving gap, emit a [TibCandidate]. Confidence is
     * MEDIUM when [companionSleepWindows] corroborates the gap, otherwise
     * LOW.
     *
     * Idempotent: re-running with the same inputs produces the same set of
     * candidates. Combined with the `(sourceId, metricType, timestamp)`
     * unique index on `metric_readings`, repeated backfills collapse to
     * in-place updates rather than parallel rows.
     */
    fun infer(
        hrSamples: List<HrSample>,
        companionSleepWindows: List<SleepWindow> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        minGapMs: Long = MIN_GAP_MS,
    ): List<TibCandidate> {
        if (hrSamples.size < 2) return emptyList()
        val sorted = hrSamples.sortedBy { it.timestamp }
        val out = mutableListOf<TibCandidate>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1].timestamp
            val curr = sorted[i].timestamp
            val gap = curr - prev
            if (gap < minGapMs) continue
            if (!gapOverlapsNocturnalAnchor(prev, curr, zone)) continue
            val corroborated = companionSleepWindows.any { w ->
                val halfMs = (w.durationSec * 1000L) / 2L
                val ws = w.midpointMs - halfMs
                val we = w.midpointMs + halfMs
                ws < curr && we > prev
            }
            out += TibCandidate(
                startMs = prev,
                endMs = curr,
                confidence = if (corroborated) ConfidenceTier.MEDIUM else ConfidenceTier.LOW,
                corroboratedByCompanionSleep = corroborated,
            )
        }
        return out
    }

    /** True when [startMs, endMs] overlaps the [NOCTURNAL_ANCHOR_START_HOUR,
     *  NOCTURNAL_ANCHOR_END_HOUR] window on any local-zone day inside the
     *  interval. Iterates at most a few days per call. */
    private fun gapOverlapsNocturnalAnchor(
        startMs: Long, endMs: Long, zone: ZoneId,
    ): Boolean {
        val startDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
        var d = startDate
        while (!d.isAfter(endDate)) {
            val anchorStart = d.atTime(NOCTURNAL_ANCHOR_START_HOUR, 0)
                .atZone(zone).toInstant().toEpochMilli()
            val anchorEnd = d.atTime(NOCTURNAL_ANCHOR_END_HOUR, 0)
                .atZone(zone).toInstant().toEpochMilli()
            if (anchorStart < endMs && anchorEnd > startMs) return true
            d = d.plusDays(1)
        }
        return false
    }

    // Below this, a gap is more likely a long meeting / commute than sleep.
    // Matches `PhoneSleepInference.MIN_SLEEP_WINDOW_MS` so the two engines
    // agree on what counts as a viable overnight window.
    internal const val MIN_GAP_MS: Long = 4L * 60L * 60L * 1000L

    // Local-time anchor window. Picked narrow (02:00–05:00) so a 4h gap
    // ending at 02:30 ("late dinner, watch off till 02:30") doesn't count
    // but a 4h gap from 23:00 to 03:00 does.
    internal const val NOCTURNAL_ANCHOR_START_HOUR = 2
    internal const val NOCTURNAL_ANCHOR_END_HOUR = 5
}
