package com.bios.app.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Computes HRV (Heart Rate Variability) metrics from inter-beat intervals (IBIs).
 *
 * Ported from:
 * - Aura hrv-analysis: RMSSD, SDNN, pNN50 time-domain metrics
 * - HeartPy: IBI artifact rejection (Malik threshold method)
 * - NeuroKit2: signal quality index for HRV
 *
 * References:
 * - Shaffer & Ginsberg (2017) - Overview of HRV metrics and norms
 * - Malik et al. (1996) - Standards of measurement for HRV (ESC/NASPE)
 * - van Gent et al. (2019) - HeartPy noise-resistant analysis
 */
object HrvAnalyzer {

    /** Physiological IBI bounds (ms). IBIs outside are artifacts. */
    private const val MIN_IBI_MS = 300.0   // ~200 bpm
    private const val MAX_IBI_MS = 2000.0  // ~30 bpm

    /** Malik threshold: successive IBI change > 20% is artifact. */
    private const val MALIK_THRESHOLD = 0.20

    /** Minimum clean IBIs required for meaningful HRV. */
    const val MIN_IBIS = 5

    /**
     * Full HRV analysis from raw inter-beat intervals (in milliseconds).
     * Returns null if insufficient clean data.
     */
    fun analyze(rawIbisMs: List<Double>): HrvResult? {
        val clean = rejectArtifacts(rawIbisMs)
        if (clean.size < MIN_IBIS) return null

        val rmssd = computeRmssd(clean)
        val sdnn = computeSdnn(clean)
        val pnn50 = computePnn50(clean)
        val meanIbi = clean.average()
        val meanHr = 60_000.0 / meanIbi
        val powers = Spectral.lfHfPowers(clean)

        return HrvResult(
            rmssd = rmssd,
            sdnn = sdnn,
            pnn50 = pnn50,
            lnRmssd = if (rmssd > 0.0) ln(rmssd) else 0.0,
            stressIndex = computeStressIndex(clean),
            lfHfRatio = powers.ratio,
            lfPowerMs2 = powers.lfMs2,
            hfPowerMs2 = powers.hfMs2,
            meanIbiMs = meanIbi,
            meanHrBpm = meanHr,
            cleanIbiCount = clean.size,
            artifactsRejected = rawIbisMs.size - clean.size
        )
    }

    /**
     * Reject artifact IBIs using Malik threshold method (HeartPy).
     * An IBI is rejected if it differs from the previous clean IBI by more than 20%.
     */
    internal fun rejectArtifacts(ibis: List<Double>): List<Double> {
        if (ibis.isEmpty()) return emptyList()

        val clean = mutableListOf<Double>()

        for (ibi in ibis) {
            // Hard physiological bounds
            if (ibi < MIN_IBI_MS || ibi > MAX_IBI_MS) continue

            // Malik threshold: compare to last accepted IBI
            if (clean.isNotEmpty()) {
                val prev = clean.last()
                val changeFraction = abs(ibi - prev) / prev
                if (changeFraction > MALIK_THRESHOLD) continue
            }

            clean.add(ibi)
        }

        return clean
    }

    /**
     * RMSSD: Root Mean Square of Successive Differences.
     * Primary parasympathetic (vagal) HRV metric. Most robust for short recordings.
     * (Malik et al. 1996, ESC/NASPE standard)
     */
    internal fun computeRmssd(ibis: List<Double>): Double {
        if (ibis.size < 2) return 0.0
        val diffs = ibis.zipWithNext { a, b -> b - a }
        return sqrt(diffs.map { it * it }.average())
    }

    /**
     * SDNN: Standard Deviation of NN (normal-to-normal) intervals.
     * Reflects overall HRV including sympathetic and parasympathetic.
     * (Malik et al. 1996)
     */
    internal fun computeSdnn(ibis: List<Double>): Double {
        if (ibis.size < 2) return 0.0
        val mean = ibis.average()
        return sqrt(ibis.map { (it - mean) * (it - mean) }.average())
    }

    /**
     * pNN50: Percentage of successive IBI differences > 50ms.
     * Parasympathetic marker. Correlates strongly with RMSSD.
     * (Malik et al. 1996)
     */
    internal fun computePnn50(ibis: List<Double>): Double {
        if (ibis.size < 2) return 0.0
        val diffs = ibis.zipWithNext { a, b -> abs(b - a) }
        val nn50 = diffs.count { it > 50.0 }
        return (nn50.toDouble() / diffs.size) * 100.0
    }

    /** RR histogram bin width for Baevsky stress index. 50ms is the standard. */
    private const val STRESS_BIN_MS = 50.0

    /**
     * Baevsky's Stress Index (SI, "Index of Tension") — a geometric autonomic
     * derivation over the RR tachogram. Rises with sympathetic activation and
     * parasympathetic withdrawal.
     *
     *   SI = AMo / (2 × Mo × MxDMn)
     *
     * with Mo and MxDMn in seconds and AMo in percent. Bins are 50ms wide
     * (the canonical width in Baevsky's work).
     *
     * Typical resting values are ~50–150; >200 indicates elevated sympathetic
     * tone. References: Baevsky & Berseneva 2008; Kobelev et al. 2024 review.
     *
     * Returns 0.0 when MxDMn is zero (constant-IBI degenerate case — no
     * variability, SI undefined).
     */
    internal fun computeStressIndex(ibis: List<Double>): Double {
        if (ibis.size < 2) return 0.0
        val maxMs = ibis.max()
        val minMs = ibis.min()
        val mxDMnSec = (maxMs - minMs) / 1000.0
        if (mxDMnSec <= 0.0) return 0.0

        val bins = HashMap<Int, Int>()
        for (ibi in ibis) {
            val bin = (ibi / STRESS_BIN_MS).toInt()
            bins[bin] = (bins[bin] ?: 0) + 1
        }
        val modeEntry = bins.maxByOrNull { it.value } ?: return 0.0
        val moSec = ((modeEntry.key + 0.5) * STRESS_BIN_MS) / 1000.0
        val amoPct = (modeEntry.value.toDouble() / ibis.size) * 100.0

        return amoPct / (2.0 * moSec * mxDMnSec)
    }

    data class HrvResult(
        /** Root Mean Square of Successive Differences (ms). Primary metric. */
        val rmssd: Double,
        /** Standard Deviation of NN intervals (ms). Overall variability. */
        val sdnn: Double,
        /** Percentage of successive diffs > 50ms. Parasympathetic marker. */
        val pnn50: Double,
        /**
         * Natural log of RMSSD — the standard time-domain proxy for HF spectral
         * power (parasympathetic tone). Correlates ~0.9 with ln(HF) in healthy
         * adults, and is approximately normally distributed across individuals
         * (Shaffer & Ginsberg 2017, Kleiger 2005). Zero when rmssd is zero.
         */
        val lnRmssd: Double,
        /**
         * Baevsky's Stress Index (SI, "Index of Tension"). Geometric autonomic
         * derivation over the RR tachogram; rises with sympathetic activation
         * and parasympathetic withdrawal. Typical rest range ~50–150; >200
         * indicates elevated sympathetic tone. Zero in the degenerate
         * constant-IBI case (Baevsky & Berseneva 2008).
         */
        val stressIndex: Double,
        /**
         * LF/HF ratio — power in the low-frequency band (0.04–0.15 Hz)
         * divided by power in the high-frequency band (0.15–0.40 Hz),
         * computed via FFT on the IBI tachogram resampled at 4 Hz with
         * a Hann window (Task Force 1996, Shaffer & Ginsberg 2017).
         * Reflects sympathovagal balance: typical resting range 0.5–2.0,
         * values >5 indicate sympathetic dominance. Zero when the
         * recording is shorter than 60 seconds, the series is
         * degenerate, or the HF band carries no power.
         */
        val lfHfRatio: Double,
        /**
         * Absolute power in the LF band (0.04–0.15 Hz), in ms². Useful as
         * an engine input for cross-correlation patterns where the ratio
         * alone is ambiguous (sympathetic *up* and parasympathetic *down*
         * can produce the same ratio). Zero in the same degenerate cases
         * as [lfHfRatio]. (Task Force 1996, Shaffer & Ginsberg 2017.)
         */
        val lfPowerMs2: Double,
        /**
         * Absolute power in the HF band (0.15–0.40 Hz), in ms². Strong
         * vagal-tone correlate; tracks respiratory sinus arrhythmia.
         * Zero in the same degenerate cases as [lfHfRatio]. (Task Force
         * 1996, Shaffer & Ginsberg 2017.)
         */
        val hfPowerMs2: Double,
        /** Mean inter-beat interval (ms). */
        val meanIbiMs: Double,
        /** Mean heart rate derived from IBIs (bpm). */
        val meanHrBpm: Double,
        /** Number of clean IBIs used in computation. */
        val cleanIbiCount: Int,
        /** Number of IBIs rejected as artifacts. */
        val artifactsRejected: Int
    )
}
