package com.bios.app.engine

import kotlin.math.abs

/**
 * PVC-burden estimator from a PPG-derived RR-interval series.
 *
 * Premature ventricular contractions (PVCs) produce a characteristic
 * RR signature: a short interval (the ectopic beat arriving early)
 * followed by a longer interval (the compensatory pause as the SA node
 * resets). This is the same pattern automated Holter algorithms key off
 * and the smartwatch-PPG literature (Han 2020, Sološenko 2017) confirms
 * the PPG-IBI series preserves it for non-fusion PVCs.
 *
 * The detector iterates the series and counts pairs where:
 *
 * ```
 * RR[i]   ≤ shortRatio × median(RR)
 * RR[i+1] ≥ longRatio  × median(RR)
 * ```
 *
 * Burden is `100 × candidate_pairs / analysable_beats`, returned as a
 * percent ∈ [0, 100]. The shipping cutoffs follow Niwano 2009 (10 % ≥
 * threshold for PVC-induced cardiomyopathy concern) and Park 2018 (15 %
 * stricter cutoff). The downstream pattern in
 * [com.bios.app.alerts.AdvancedCardiologyPatterns] reads the 10 %
 * literature anchor.
 *
 * ## Honest scope
 *
 * The detector cannot distinguish a single ectopic focus from
 * non-respiratory sinus arrhythmia, atrial premature complexes with
 * concealed conduction, or PPG motion artefacts that mimic the
 * short-long signature. It is a **screening surrogate**, not a Holter
 * substitute. The pattern text recommends 12-lead / Holter confirmation
 * before any clinical conclusion. AFib's irregularly-irregular signature
 * dominates this short-long signal; the existing [RhythmClassifier] runs
 * upstream and the [com.bios.app.ingest.CameraPpgAdapter] should suppress
 * ECTOPY_BURDEN_ESTIMATE emission on windows the rhythm classifier scores
 * IRREGULARLY_IRREGULAR (handled by the adapter, not this detector).
 *
 * ## References
 *
 * - Baman TS et al. (2010) — Relationship between burden of premature
 *   ventricular complexes and left ventricular function. Heart Rhythm
 *   7(7):865-869.
 * - Niwano S et al. (2009) — Prognostic significance of frequent
 *   premature ventricular contractions originating from the ventricular
 *   outflow tract in patients with normal LV function. Heart 95(15):1230-7.
 * - Park KM et al. (2018) — Frequent premature ventricular complex–
 *   induced cardiomyopathy. Korean Circulation Journal 48(8):1-19.
 * - Sološenko A et al. (2017) — Detection of atrial fibrillation using
 *   short-term ECGs and PPG. Annual Int. Conf. IEEE EMBC.
 */
object EctopyDetector {

    /** Hard floor on the RR series length; below this the median anchor is unreliable. */
    const val MIN_RR_INTERVALS: Int = 30

    /** Short-beat ratio threshold: RR ≤ 0.80 × median is the canonical "premature" anchor. */
    const val SHORT_RATIO_CUTOFF: Double = 0.80

    /** Compensatory-pause ratio threshold: RR ≥ 1.15 × median is the canonical "pause" anchor. */
    const val LONG_RATIO_CUTOFF: Double = 1.15

    data class EctopyResult(
        val burdenPercent: Double,
        val candidatePairs: Int,
        val beatsAnalysed: Int,
    )

    /**
     * Compute the burden estimate from an RR-interval series (milliseconds).
     * Returns null when the series is too short to anchor against the
     * median — picking a candidate without a robust mean rate would
     * surface noise. The caller (CameraPpgAdapter) emits the value as a
     * MetricReading on `MetricType.ECTOPY_BURDEN_ESTIMATE`.
     */
    fun analyse(rrIntervalsMs: List<Double>): EctopyResult? {
        if (rrIntervalsMs.size < MIN_RR_INTERVALS) return null

        val median = medianOf(rrIntervalsMs)
        if (median <= 0.0) return null

        val shortFloor = SHORT_RATIO_CUTOFF * median
        val longFloor = LONG_RATIO_CUTOFF * median

        var candidates = 0
        // Iterate pairs (i, i+1). The last interval has no successor and is
        // excluded from the candidate count but still contributes to the
        // analysable-beats denominator below.
        for (i in 0 until rrIntervalsMs.size - 1) {
            val current = rrIntervalsMs[i]
            val next = rrIntervalsMs[i + 1]
            if (current <= shortFloor && next >= longFloor) {
                candidates++
            }
        }

        val analysable = rrIntervalsMs.size
        val burden = 100.0 * candidates / analysable
        return EctopyResult(
            burdenPercent = burden.coerceIn(0.0, 100.0),
            candidatePairs = candidates,
            beatsAnalysed = analysable,
        )
    }

    /** Median of a list of doubles. Internal helper; mean would be biased by ectopics themselves. */
    internal fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    /** Convenience: absolute deviation, used by sibling tests; kept here so this object is self-contained. */
    internal fun absDevFromMedian(values: List<Double>): List<Double> {
        val med = medianOf(values)
        return values.map { abs(it - med) }
    }
}
