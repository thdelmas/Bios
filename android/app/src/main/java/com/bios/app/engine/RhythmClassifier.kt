package com.bios.app.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device rhythm classifier over an RR-interval series.
 *
 * Closes the cardiology audit's §2.1 gap: the previous `atrialFibrillationScreen`
 * pattern was a personal-baseline z-score over HRV, not a rhythm-strip classifier.
 * Apple Heart Study (Perez 2019 NEJM), Huawei mAFA-II (Guo 2019 JACC), Fitbit
 * Heart Study (Lubitz 2022 Circulation), and the underlying Tateno-Glass 2001
 * algorithm all classify the **RR series itself** as regular or irregularly
 * irregular — the defining electrocardiographic signature of atrial
 * fibrillation. `PpgSignalProcessor.extract` already returns `rrIntervalsMs`;
 * this object turns that series into a per-window verdict.
 *
 * Features computed per window:
 *  - **Poincaré dispersion (SD1, SD2)** — short- and long-term variability of
 *    the (RR_i, RR_{i+1}) scatter, the canonical "Lorenz plot" of HRV. AFib
 *    flattens the cigar into a cloud, so SD1/SD2 → 1.
 *  - **Sample entropy (m=2, r=0.2·SD)** — irregularity / randomness of the
 *    series. Markedly elevated in AFib (>1.4 is the literature anchor).
 *  - **ΔRR turning-point ratio** — fraction of successive-difference signs
 *    that flip; for an i.i.d. random series the expectation is ~2/3.
 *
 * Classification rule (cutoffs from Tateno-Glass 2001 IEEE TBE and follow-on
 * literature, adapted for the PPG-derived RR series rather than ECG R-R):
 *  - SD1/SD2 > 0.5
 *  - sample entropy > 1.4
 *  - turning-point ratio > 0.55
 *
 * All three must cross simultaneously for the window to be scored
 * IRREGULARLY_IRREGULAR. Otherwise REGULAR. Insufficient-data windows
 * (< [MIN_RR_INTERVALS]) return INSUFFICIENT_DATA without a verdict.
 *
 * Returns the binary verdict and the underlying features. Downstream code
 * (CameraPpgAdapter, the IRREGULAR_RHYTHM_BURDEN writer) collapses many
 * window verdicts into a rolling-percent metric; the
 * `paroxysmal_afib_screen` ConditionPattern fires when that rolling
 * fraction exceeds the literature-derived burden threshold.
 *
 * **Manifesto stance**: this classifier never claims a diagnosis. The output
 * is a per-window observation; the pull-side detail view surfaces the burden;
 * the alert text always recommends clinical ECG confirmation. AFib is the
 * dominant cardio-embolic stroke source, so an honest screening surface is
 * a protection-of-owner feature — but only when the framing stays calibrated.
 *
 * References:
 *  - Tateno K & Glass L (2001) — Automatic detection of atrial fibrillation
 *    using the coefficient of variation and density histograms of RR and
 *    ΔRR intervals. IEEE Trans. Biomed. Eng. 48(2):169–179.
 *  - Perez MV et al. (2019) — Large-scale assessment of a smartwatch to
 *    identify atrial fibrillation (Apple Heart Study). N. Engl. J. Med.
 *    381:1909–1917.
 *  - Guo Y et al. (2019) — Mobile photoplethysmographic technology to detect
 *    atrial fibrillation (mAFA-II). J. Am. Coll. Cardiol. 74:2365–2375.
 *  - Lubitz SA et al. (2022) — Detection of atrial fibrillation in a large
 *    population using wearable devices: the Fitbit Heart Study. Circulation
 *    146:1415–1424.
 *  - Richman JS & Moorman JR (2000) — Physiological time-series analysis
 *    using approximate entropy and sample entropy. Am. J. Physiol. Heart
 *    Circ. Physiol. 278:H2039–H2049.
 */
object RhythmClassifier {

    /** Hard floor: below this the entropy / Poincaré estimates aren't credible. */
    const val MIN_RR_INTERVALS = 30

    /** Tateno-Glass 2001 / Sarkar 2008 SD1/SD2 cutoff for irregularly
     *  irregular rhythms on PPG-derived RR series. ECG-anchored studies
     *  typically use 0.6; PPG adds noise so the operating point is slightly
     *  lower in the published smartwatch literature. */
    const val SD1_OVER_SD2_CUTOFF = 0.5

    /** Sample-entropy cutoff. Tateno-Glass 2001 and Lake 2002 anchor AFib
     *  sample entropy well above 1.4 on RR series (m=2, r=0.2·SD). */
    const val SAMPLE_ENTROPY_CUTOFF = 1.4

    /** Turning-point ratio cutoff. The Brock 1996 / Kendall test for series
     *  randomness places i.i.d. expectation at ~2/3; AFib pushes RR ΔRR
     *  signs further toward random. 0.55 is the operating point that
     *  separates sinus-rhythm noise from AFib in Sarkar 2008. */
    const val TURNING_POINT_RATIO_CUTOFF = 0.55

    /** Sample-entropy tolerance ratio (Richman-Moorman convention). */
    private const val ENTROPY_R_RATIO = 0.2

    /** Sample-entropy embedding dimension. */
    private const val ENTROPY_M = 2

    /** Verdict per evaluated RR-interval window. */
    enum class WindowVerdict {
        REGULAR,
        IRREGULARLY_IRREGULAR,
        INSUFFICIENT_DATA,
    }

    /** Classifier output: verdict + the feature values that produced it. */
    data class WindowResult(
        val verdict: WindowVerdict,
        val sd1: Double,
        val sd2: Double,
        val sd1OverSd2: Double,
        val sampleEntropy: Double,
        val turningPointRatio: Double,
        val intervalCount: Int,
    ) {
        val irregular: Boolean get() = verdict == WindowVerdict.IRREGULARLY_IRREGULAR
    }

    /**
     * Classify a single RR-interval series. Caller passes the PPG-derived
     * `rrIntervalsMs` from [PpgSignalProcessor.extract]. Returns
     * INSUFFICIENT_DATA when the series is shorter than [MIN_RR_INTERVALS]
     * or degenerate (all-equal values produce zero variance, which is itself
     * a regular-rhythm signature — handled here by treating SD-zero series
     * as REGULAR rather than crashing on a div-by-zero).
     */
    fun classify(rrIntervalsMs: List<Double>): WindowResult {
        val n = rrIntervalsMs.size
        if (n < MIN_RR_INTERVALS) {
            return WindowResult(
                verdict = WindowVerdict.INSUFFICIENT_DATA,
                sd1 = 0.0,
                sd2 = 0.0,
                sd1OverSd2 = 0.0,
                sampleEntropy = 0.0,
                turningPointRatio = 0.0,
                intervalCount = n,
            )
        }

        val (sd1, sd2) = poincareSd(rrIntervalsMs)
        val ratio = if (sd2 > 1e-9) sd1 / sd2 else 0.0
        val entropy = sampleEntropy(rrIntervalsMs, ENTROPY_M, ENTROPY_R_RATIO)
        val tpr = turningPointRatio(rrIntervalsMs)

        val irregular = ratio > SD1_OVER_SD2_CUTOFF &&
            entropy > SAMPLE_ENTROPY_CUTOFF &&
            tpr > TURNING_POINT_RATIO_CUTOFF

        return WindowResult(
            verdict = if (irregular) WindowVerdict.IRREGULARLY_IRREGULAR else WindowVerdict.REGULAR,
            sd1 = sd1,
            sd2 = sd2,
            sd1OverSd2 = ratio,
            sampleEntropy = entropy,
            turningPointRatio = tpr,
            intervalCount = n,
        )
    }

    /**
     * Rolling burden across a sequence of per-window verdicts: the percentage
     * of evaluable windows scored irregularly irregular. INSUFFICIENT_DATA
     * windows are excluded from both numerator and denominator. Returns 0.0
     * when no evaluable windows are present.
     */
    fun burdenPercent(windows: List<WindowResult>): Double {
        val evaluable = windows.filter { it.verdict != WindowVerdict.INSUFFICIENT_DATA }
        if (evaluable.isEmpty()) return 0.0
        val irregularCount = evaluable.count { it.irregular }
        return 100.0 * irregularCount / evaluable.size
    }

    // -- Feature implementations --

    /**
     * Poincaré-plot dispersion axes (SD1, SD2) for the successive-pair
     * scatter of RR intervals. SD1 = √(½ · Var(ΔRR)), SD2 = √(2·Var(RR) − ½·Var(ΔRR)).
     * Brennan 2001 derivation; equivalent to a 45°-rotated ellipse fit.
     *
     * AFib flattens the cigar into a circular cloud (SD1 ≈ SD2 → ratio → 1).
     * Sinus rhythm with respiratory sinus arrhythmia gives SD1 << SD2.
     */
    internal fun poincareSd(rr: List<Double>): Pair<Double, Double> {
        if (rr.size < 2) return 0.0 to 0.0
        val deltas = rr.zipWithNext { a, b -> b - a }
        val sdsd = standardDeviation(deltas)
        val sdrr = standardDeviation(rr)
        val sd1 = sdsd / sqrt(2.0)
        val sd2Sq = 2.0 * sdrr * sdrr - 0.5 * sdsd * sdsd
        val sd2 = if (sd2Sq > 0.0) sqrt(sd2Sq) else 0.0
        return sd1 to sd2
    }

    /**
     * Sample entropy SampEn(m, r, N) on a series. Richman-Moorman 2000.
     * m = embedding dimension, r = tolerance as a fraction of the series SD.
     *
     * Computes the number of template-vector pairs at length m and m+1 that
     * fall within tolerance r·SD under Chebyshev distance, then returns
     * −ln(A/B). A higher SampEn means a more irregular (less predictable)
     * series; AFib's chaotic RR sequence pushes SampEn well above sinus
     * rhythm's. We clamp to a finite ceiling when A=0 (no length-(m+1)
     * matches) so a perfectly random short series doesn't produce −ln(0).
     */
    internal fun sampleEntropy(series: List<Double>, m: Int, rRatio: Double): Double {
        val n = series.size
        if (n < m + 1) return 0.0
        val sd = standardDeviation(series)
        if (sd < 1e-9) return 0.0  // constant series → maximally regular
        val r = rRatio * sd

        // Build vectors of length m and m+1.
        val templatesM = (0..n - m).map { i -> series.subList(i, i + m) }
        val templatesM1 = (0..n - m - 1).map { i -> series.subList(i, i + m + 1) }

        val matchesM = countMatchingPairs(templatesM, r)
        val matchesM1 = countMatchingPairs(templatesM1, r)

        if (matchesM == 0) return 0.0
        if (matchesM1 == 0) {
            // Finite ceiling rather than +∞. Matches the convention used by
            // PhysioNet's sampen reference implementation.
            return ln(matchesM.toDouble())
        }
        return -ln(matchesM1.toDouble() / matchesM.toDouble())
    }

    /**
     * Number of i<j template-vector pairs whose Chebyshev distance is ≤ r.
     * Vectors of equal length only; caller ensures that.
     */
    private fun countMatchingPairs(vectors: List<List<Double>>, r: Double): Int {
        var count = 0
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                if (chebyshevDistance(vectors[i], vectors[j]) <= r) count++
            }
        }
        return count
    }

    private fun chebyshevDistance(a: List<Double>, b: List<Double>): Double {
        var maxDelta = 0.0
        for (k in a.indices) {
            val d = abs(a[k] - b[k])
            if (d > maxDelta) maxDelta = d
        }
        return maxDelta
    }

    /**
     * Turning-point ratio over the ΔRR series. Counts the fraction of
     * interior points that are a local extremum (strict turning point)
     * in the successive-difference sequence — a Kendall-style randomness
     * test. For i.i.d. Gaussian noise the expected ratio is 2/3.
     *
     * Sinus rhythm with respiratory modulation gives a much lower ratio
     * (RSA is smooth, not chaotic); AFib pushes the ratio toward the
     * random-series expectation.
     */
    internal fun turningPointRatio(rr: List<Double>): Double {
        if (rr.size < 4) return 0.0
        val deltas = rr.zipWithNext { a, b -> b - a }
        if (deltas.size < 3) return 0.0
        var turns = 0
        for (i in 1 until deltas.size - 1) {
            val prev = deltas[i - 1]
            val curr = deltas[i]
            val next = deltas[i + 1]
            // Strict local max or local min.
            if ((curr > prev && curr > next) || (curr < prev && curr < next)) {
                turns++
            }
        }
        return turns.toDouble() / (deltas.size - 2)
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val sumSq = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumSq / values.size)
    }
}
