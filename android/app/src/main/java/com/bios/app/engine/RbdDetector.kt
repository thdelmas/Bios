package com.bios.app.engine

import com.bios.app.model.SleepStage
import kotlin.math.abs

/**
 * REM Sleep Behaviour Disorder (RBD) wearable-proxy detector
 * (#206, NEUROLOGY_POV §2.7).
 *
 * RBD is "REM without atonia" — the muscle paralysis that normally
 * accompanies REM sleep fails, and the sleeper acts out their dreams.
 * Clinically diagnosed via polysomnography with surface EMG; idiopathic RBD
 * is the single highest-leverage neurology prodrome currently visible to
 * wearables, because ~80 % of polysomnogram-confirmed iRBD cases convert to
 * a synucleinopathy (Parkinson's disease, dementia with Lewy bodies, or
 * multiple system atrophy) within 10–15 years (Postuma et al. 2019;
 * Schenck 2002 / 2013).
 *
 * The wearable proxy in the published literature is **accelerometer bursts
 * during detected REM sleep windows** — REM should be characterised by
 * atonia (very low movement), so unusually large bursts within REM are
 * candidates for RBD events. The signal is noisier than PSG (positional
 * shifts, sleeping partner contact, bed-partner movement, mattress
 * coupling), so detection thresholds are conservative and the surface is
 * advisory pull-side only.
 *
 * **What this detector does NOT do**:
 *  - It does not diagnose RBD. The pull-side advisory pattern
 *    ([com.bios.app.alerts.RbdScreenPattern]) explicitly recommends a
 *    PSG-with-EMG evaluation as the diagnostic step.
 *  - It does not push-notify the owner about synucleinopathy risk.
 *    [com.bios.app.alerts.AlertContentPolicy] bans the prognostic
 *    language in push surfaces.
 *  - It does not infer REM from accelerometer alone; the REM windows are
 *    drawn from already-classified [com.bios.contracts.MetricType.SLEEP_STAGE]
 *    rows (Oura, WHOOP, Health Connect, etc.) or from the existing phone
 *    sleep inference. RBD detection without REM-window context is not
 *    something the literature supports.
 *
 * Algorithm:
 *  1. Filter the [sleepStages] input to REM-only sessions (intervals where
 *     the stage row is [SleepStage.REM]).
 *  2. For each REM interval, scan the matching accelerometer-magnitude
 *     samples and count "bursts" — samples whose gravity-removed magnitude
 *     exceeds [BURST_THRESHOLD_M_PER_S_SQUARED] for at least
 *     [MIN_BURST_DURATION_SAMPLES] consecutive samples.
 *  3. Aggregate per-night: total burst count is the REM_MOVEMENT_INDEX.
 *
 * Each burst is an [RbdCandidateEvent]; the night's [RbdNightSummary] holds
 * the count and the total REM duration scanned, which the pattern then reads
 * over a 4-week window.
 *
 * References:
 *  - Postuma RB et al. (2019) — Risk and predictors of dementia and
 *    parkinsonism in idiopathic REM sleep behaviour disorder: a multicentre
 *    study. Brain 142(3):744–759.
 *  - Schenck CH et al. (2013) — Delayed emergence of a parkinsonian disorder
 *    or dementia in 81 % of older men initially diagnosed with idiopathic
 *    RBD: a 16-year update. Sleep Medicine 14(8):744–748.
 *  - Iranzo A et al. (2017) — Neurodegenerative disorder risk in idiopathic
 *    REM sleep behaviour disorder. PLoS One 9(2):e89741.
 *  - Louter M et al. (2014) — Accelerometer-based quantitative analysis of
 *    sleep movements as a screening tool for RBD. Movement Disorders 29(1):
 *    150–155.
 */
object RbdDetector {

    /** REM samples below this magnitude (m/s², gravity removed) are
     *  considered atonic. The conservative threshold rejects bed-partner
     *  contact and mattress coupling. Louter et al. 2014 used a similar
     *  magnitude cutoff on wrist actigraphy. */
    const val BURST_THRESHOLD_M_PER_S_SQUARED = 0.5

    /** Minimum consecutive samples above the threshold to count as a single
     *  burst. At 50 Hz this is ~200 ms — enough to reject single-sample
     *  spikes and short positional shifts, short enough to catch the
     *  rapid limb twitches the RBD literature describes. */
    const val MIN_BURST_DURATION_SAMPLES = 10

    /** Maximum gap (in samples) between above-threshold samples that the
     *  scanner still treats as one burst. 5 samples at 50 Hz is ~100 ms,
     *  short enough to bridge the sample-to-sample dropouts a noisy
     *  accelerometer produces. */
    const val MAX_INTRA_BURST_GAP_SAMPLES = 5

    /** A REM interval — start / end timestamps (epoch millis) of a window
     *  the upstream sleep stager has classified as REM. */
    data class RemInterval(val startMillis: Long, val endMillis: Long) {
        val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
    }

    /** One detected burst within a REM window. */
    data class RbdCandidateEvent(
        /** Epoch millis at the start of the burst. */
        val timestampMillis: Long,
        /** Sample count the burst spanned. */
        val durationSamples: Int,
        /** Peak gravity-removed acceleration during the burst (m/s²). */
        val peakAmplitude: Double,
    )

    /** Per-night aggregate: REM movement index = count of bursts. */
    data class RbdNightSummary(
        val nightStartMillis: Long,
        val nightEndMillis: Long,
        /** Number of bursts detected across all REM intervals this night. */
        val burstCount: Int,
        /** Total REM time scanned (millis). When zero, the night has no
         *  REM data and the burst count is uninformative. */
        val remDurationMillis: Long,
        /** All bursts detected, in start-time order. */
        val events: List<RbdCandidateEvent>,
    ) {
        /** True when this night has at least one detected burst within
         *  scanned REM time. The RbdScreenPattern composes these flags
         *  over its 4-week window. */
        val hasRbdEvent: Boolean get() = burstCount > 0 && remDurationMillis > 0
    }

    /**
     * Compose REM intervals from a list of [SleepStage]-tagged segments.
     * Callers typically obtain stage rows from sleep-architecture metric
     * readings (one row per stage interval, with start timestamp and
     * duration). This helper makes the boundary semantics explicit so
     * downstream tests stay readable.
     */
    fun remIntervalsFromStages(
        stages: List<StageSegment>,
    ): List<RemInterval> =
        stages
            .filter { it.stage == SleepStage.REM }
            .map { RemInterval(it.startMillis, it.endMillis) }
            .filter { it.durationMillis > 0 }

    /** A single stage segment — used to compose [RemInterval]s. */
    data class StageSegment(
        val stage: SleepStage,
        val startMillis: Long,
        val endMillis: Long,
    )

    /**
     * Scans accelerometer magnitudes (gravity-removed, m/s²) for bursts
     * within the supplied REM intervals.
     *
     * @param remIntervals REM windows from the sleep stager.
     * @param accelerometerMagnitudes gravity-removed magnitudes sampled at
     *        [sampleRateHz]. Indexed by [accelerometerStartMillis] +
     *        i / [sampleRateHz] seconds.
     * @param accelerometerStartMillis epoch millis at the first sample.
     * @param sampleRateHz effective sample rate (samples / second).
     */
    fun detectBursts(
        remIntervals: List<RemInterval>,
        accelerometerMagnitudes: DoubleArray,
        accelerometerStartMillis: Long,
        sampleRateHz: Double,
    ): List<RbdCandidateEvent> {
        if (accelerometerMagnitudes.isEmpty() || sampleRateHz <= 0.0) return emptyList()
        if (remIntervals.isEmpty()) return emptyList()

        val msPerSample = 1000.0 / sampleRateHz
        val events = mutableListOf<RbdCandidateEvent>()

        for (interval in remIntervals) {
            val startIdx = sampleIndexAt(interval.startMillis, accelerometerStartMillis, msPerSample)
                .coerceAtLeast(0)
            val endIdx = sampleIndexAt(interval.endMillis, accelerometerStartMillis, msPerSample)
                .coerceAtMost(accelerometerMagnitudes.size)
            if (endIdx <= startIdx) continue

            events += scanBursts(
                magnitudes = accelerometerMagnitudes,
                startIdx = startIdx,
                endIdx = endIdx,
                accelerometerStartMillis = accelerometerStartMillis,
                msPerSample = msPerSample,
            )
        }
        return events
    }

    /** Aggregate detected events into a per-night summary. */
    fun summariseNight(
        nightStartMillis: Long,
        nightEndMillis: Long,
        remIntervals: List<RemInterval>,
        events: List<RbdCandidateEvent>,
    ): RbdNightSummary {
        val remDuration = remIntervals.sumOf { it.durationMillis }
        return RbdNightSummary(
            nightStartMillis = nightStartMillis,
            nightEndMillis = nightEndMillis,
            burstCount = events.size,
            remDurationMillis = remDuration,
            events = events.sortedBy { it.timestampMillis },
        )
    }

    /**
     * One-shot convenience that runs the full pipeline: REM intervals →
     * burst detection → night summary. Useful for the night-finalisation
     * worker and for tests.
     */
    fun analyseNight(
        nightStartMillis: Long,
        nightEndMillis: Long,
        stages: List<StageSegment>,
        accelerometerMagnitudes: DoubleArray,
        accelerometerStartMillis: Long,
        sampleRateHz: Double,
    ): RbdNightSummary {
        val remIntervals = remIntervalsFromStages(stages)
        val events = detectBursts(
            remIntervals = remIntervals,
            accelerometerMagnitudes = accelerometerMagnitudes,
            accelerometerStartMillis = accelerometerStartMillis,
            sampleRateHz = sampleRateHz,
        )
        return summariseNight(nightStartMillis, nightEndMillis, remIntervals, events)
    }

    private fun sampleIndexAt(
        atMillis: Long,
        startMillis: Long,
        msPerSample: Double,
    ): Int = ((atMillis - startMillis) / msPerSample).toInt()

    /**
     * Linear scan that turns above-threshold runs into discrete bursts,
     * tolerating short sub-threshold gaps so a single burst with mild
     * sample-to-sample dropouts doesn't fragment into many.
     */
    private fun scanBursts(
        magnitudes: DoubleArray,
        startIdx: Int,
        endIdx: Int,
        accelerometerStartMillis: Long,
        msPerSample: Double,
    ): List<RbdCandidateEvent> {
        val out = mutableListOf<RbdCandidateEvent>()
        var inBurst = false
        var burstStartIdx = -1
        var burstPeak = 0.0
        var gap = 0

        for (i in startIdx until endIdx) {
            val v = abs(magnitudes[i])
            val above = v >= BURST_THRESHOLD_M_PER_S_SQUARED
            if (above) {
                if (!inBurst) {
                    inBurst = true
                    burstStartIdx = i
                    burstPeak = v
                } else if (v > burstPeak) {
                    burstPeak = v
                }
                gap = 0
            } else if (inBurst) {
                gap++
                if (gap > MAX_INTRA_BURST_GAP_SAMPLES) {
                    // Close the burst (subtract the trailing gap from end).
                    val burstEndIdx = i - gap
                    val span = burstEndIdx - burstStartIdx + 1
                    if (span >= MIN_BURST_DURATION_SAMPLES) {
                        out += RbdCandidateEvent(
                            timestampMillis = (accelerometerStartMillis + burstStartIdx * msPerSample).toLong(),
                            durationSamples = span,
                            peakAmplitude = burstPeak,
                        )
                    }
                    inBurst = false
                    burstPeak = 0.0
                    gap = 0
                }
            }
        }
        // Close any open burst at the end of the window.
        if (inBurst) {
            val burstEndIdx = endIdx - 1 - gap
            val span = burstEndIdx - burstStartIdx + 1
            if (span >= MIN_BURST_DURATION_SAMPLES) {
                out += RbdCandidateEvent(
                    timestampMillis = (accelerometerStartMillis + burstStartIdx * msPerSample).toLong(),
                    durationSamples = span,
                    peakAmplitude = burstPeak,
                )
            }
        }
        return out
    }
}
