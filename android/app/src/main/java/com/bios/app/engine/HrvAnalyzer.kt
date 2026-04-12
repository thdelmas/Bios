package com.bios.app.engine

import kotlin.math.abs
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

        return HrvResult(
            rmssd = rmssd,
            sdnn = sdnn,
            pnn50 = pnn50,
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

    data class HrvResult(
        /** Root Mean Square of Successive Differences (ms). Primary metric. */
        val rmssd: Double,
        /** Standard Deviation of NN intervals (ms). Overall variability. */
        val sdnn: Double,
        /** Percentage of successive diffs > 50ms. Parasympathetic marker. */
        val pnn50: Double,
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
