package com.bios.app.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns a raw fingertip-PPG luminance waveform (one sample per camera frame)
 * into inter-beat intervals (IBIs) for [HrvAnalyzer], plus a signal-quality
 * judgement the UI can surface.
 *
 * Pipeline: detrend (remove baseline drift) → smooth (low-pass) → adaptive-
 * threshold peak detection → RR intervals → SQI scoring. Bandpass is
 * approximated by the detrend/smooth pair rather than a Butterworth biquad:
 * the moving-average stack is robust, branchless, and matches what HeartPy
 * uses by default. Upgrade to a proper biquad if field data shows we need
 * sharper rolloff.
 *
 * References:
 * - van Gent et al. (2019) — HeartPy preprocessing defaults
 * - Elgendi (2013) — optimal peak detection in PPG waveforms
 * - Orphanidou et al. (2015) — SQI design for PPG in ambulatory settings
 */
object PpgSignalProcessor {

    /** Hard floor on recording length. Under this the SQI is unreliable. */
    const val MIN_RECORDING_SECONDS = 30.0

    /** Minimum peaks needed for a credible HRV reading (~40 bpm × 30 s). */
    const val MIN_PEAKS_REQUIRED = 20

    /** Refractory period between peaks (ms). Physiological floor at ~240 bpm. */
    private const val PEAK_REFRACTORY_MS = 250.0

    /** Detrend window (seconds). Must exceed the slowest expected heartbeat
     *  period (~1.5 s at 40 bpm) so we subtract baseline drift, not the beat. */
    private const val DETREND_WINDOW_SEC = 2.0

    /** Smoothing window (samples). Suppresses high-frequency noise without
     *  blurring the systolic peak. */
    private const val SMOOTH_WINDOW_SAMPLES = 5

    /** Peak threshold: local-max must exceed rolling mean + K × rolling std. */
    private const val PEAK_THRESHOLD_K = 0.5

    /** Rolling window for the adaptive threshold (seconds). */
    private const val THRESHOLD_WINDOW_SEC = 2.0

    /** SQI sub-scores below which we reject outright with a reason. */
    private const val MIN_LUMINANCE_VARIANCE = 1.0      // < 1 = no finger
    private const val MAX_SATURATION_RATIO = 0.30       // >30% saturated = overexposed
    private const val MAX_PEAK_AMPLITUDE_COV = 0.60     // higher = motion
    private const val MAX_RR_COV = 0.30                 // higher = irregular

    /**
     * Extract RR intervals and quality from a luminance waveform.
     * [luminanceSamples] is one Y-channel mean per camera frame, uniformly
     * sampled at [samplingRateHz]. Returns a [PpgResult] with either clean
     * RR intervals or a rejection reason.
     */
    fun extract(luminanceSamples: List<Double>, samplingRateHz: Double): PpgResult {
        val durationSec = luminanceSamples.size / samplingRateHz

        if (durationSec < MIN_RECORDING_SECONDS) {
            return PpgResult.rejected(
                RejectionReason.INSUFFICIENT_RECORDING_TIME,
                durationSec = durationSec
            )
        }

        val saturation = saturationRatio(luminanceSamples)
        if (saturation > MAX_SATURATION_RATIO) {
            return PpgResult.rejected(
                RejectionReason.SATURATION,
                durationSec = durationSec,
                sqiScore = 10
            )
        }

        val variance = sampleVariance(luminanceSamples)
        if (variance < MIN_LUMINANCE_VARIANCE) {
            return PpgResult.rejected(
                RejectionReason.INSUFFICIENT_SIGNAL,
                durationSec = durationSec,
                sqiScore = 5
            )
        }

        val detrendWindow = (DETREND_WINDOW_SEC * samplingRateHz).toInt().coerceAtLeast(3)
        val detrended = detrend(luminanceSamples, detrendWindow)
        val smoothed = smooth(detrended, SMOOTH_WINDOW_SAMPLES)

        val thresholdWindow = (THRESHOLD_WINDOW_SEC * samplingRateHz).toInt().coerceAtLeast(3)
        val refractorySamples = (PEAK_REFRACTORY_MS * samplingRateHz / 1000.0).toInt().coerceAtLeast(1)
        val peakIndices = detectPeaks(smoothed, thresholdWindow, refractorySamples, PEAK_THRESHOLD_K)

        if (peakIndices.size < MIN_PEAKS_REQUIRED) {
            return PpgResult.rejected(
                RejectionReason.TOO_FEW_BEATS,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 20
            )
        }

        val peakAmpCov = coefficientOfVariation(peakIndices.map { smoothed[it] })
        if (peakAmpCov > MAX_PEAK_AMPLITUDE_COV) {
            return PpgResult.rejected(
                RejectionReason.MOTION_ARTIFACT,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 30
            )
        }

        val rrMs = peakIndices.zipWithNext { a, b -> (b - a) / samplingRateHz * 1000.0 }
        val rrCov = coefficientOfVariation(rrMs)
        if (rrCov > MAX_RR_COV) {
            return PpgResult.rejected(
                RejectionReason.IRREGULAR_RHYTHM,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 40
            )
        }

        val sqi = compositeSqi(variance, saturation, peakAmpCov, rrCov)
        return PpgResult(
            rrIntervalsMs = rrMs,
            sqiScore = sqi,
            rejectionReason = null,
            peakCount = peakIndices.size,
            durationSec = durationSec
        )
    }

    /** Subtract a symmetric moving-average (window in samples). */
    internal fun detrend(samples: List<Double>, windowSamples: Int): DoubleArray {
        val out = DoubleArray(samples.size)
        val half = windowSamples / 2
        for (i in samples.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(samples.size - 1)
            var sum = 0.0
            for (j in lo..hi) sum += samples[j]
            out[i] = samples[i] - sum / (hi - lo + 1)
        }
        return out
    }

    /** Symmetric moving-average smoother. */
    internal fun smooth(samples: DoubleArray, windowSamples: Int): DoubleArray {
        val out = DoubleArray(samples.size)
        val half = windowSamples / 2
        for (i in samples.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(samples.size - 1)
            var sum = 0.0
            for (j in lo..hi) sum += samples[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    /**
     * Adaptive-threshold local-max detector with refractory enforcement.
     * Picks indices i where samples[i] is the maximum in a ±1-sample window,
     * exceeds the rolling mean + K × rolling std, and is separated from the
     * previous accepted peak by at least [refractorySamples].
     */
    internal fun detectPeaks(
        samples: DoubleArray,
        thresholdWindow: Int,
        refractorySamples: Int,
        thresholdK: Double
    ): List<Int> {
        val peaks = mutableListOf<Int>()
        val half = thresholdWindow / 2
        for (i in 1 until samples.size - 1) {
            if (samples[i] <= samples[i - 1] || samples[i] <= samples[i + 1]) continue

            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(samples.size - 1)
            val n = hi - lo + 1
            var sum = 0.0
            for (j in lo..hi) sum += samples[j]
            val mean = sum / n
            var sq = 0.0
            for (j in lo..hi) sq += (samples[j] - mean) * (samples[j] - mean)
            val std = sqrt(sq / n)

            if (samples[i] < mean + thresholdK * std) continue
            if (peaks.isNotEmpty() && i - peaks.last() < refractorySamples) continue

            peaks.add(i)
        }
        return peaks
    }

    internal fun sampleVariance(samples: List<Double>): Double {
        if (samples.size < 2) return 0.0
        val mean = samples.average()
        return samples.sumOf { (it - mean) * (it - mean) } / samples.size
    }

    internal fun saturationRatio(samples: List<Double>): Double {
        if (samples.isEmpty()) return 0.0
        val saturated = samples.count { it >= 250.0 }
        return saturated.toDouble() / samples.size
    }

    internal fun coefficientOfVariation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean == 0.0) return Double.POSITIVE_INFINITY
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        return abs(sd / mean)
    }

    /** Composite 0–100 quality score. Higher is better. */
    internal fun compositeSqi(
        variance: Double,
        saturationRatio: Double,
        peakAmpCov: Double,
        rrCov: Double
    ): Int {
        // Each term 0..1 (1 = perfect); combine by geometric mean, rescale to 100.
        val varianceTerm = (variance / 50.0).coerceIn(0.0, 1.0)
        val saturationTerm = (1.0 - saturationRatio / MAX_SATURATION_RATIO).coerceIn(0.0, 1.0)
        val ampTerm = (1.0 - peakAmpCov / MAX_PEAK_AMPLITUDE_COV).coerceIn(0.0, 1.0)
        val rrTerm = (1.0 - rrCov / MAX_RR_COV).coerceIn(0.0, 1.0)
        val geomMean = (varianceTerm * saturationTerm * ampTerm * rrTerm)
        return (sqrt(sqrt(geomMean)) * 100.0).toInt().coerceIn(0, 100)
    }
}

/** Outcome of processing a PPG waveform. */
data class PpgResult(
    /** RR intervals in ms between successive peaks. Empty when rejected. */
    val rrIntervalsMs: List<Double>,
    /** 0–100 composite signal quality. 0 when rejected. */
    val sqiScore: Int,
    /** Non-null when the recording was rejected; carries the user-facing reason. */
    val rejectionReason: RejectionReason?,
    /** Number of detected peaks (informational, even when rejected). */
    val peakCount: Int,
    /** Recording length the processor saw. */
    val durationSec: Double
) {
    val accepted: Boolean get() = rejectionReason == null

    companion object {
        fun rejected(
            reason: RejectionReason,
            durationSec: Double,
            peakCount: Int = 0,
            sqiScore: Int = 0
        ) = PpgResult(
            rrIntervalsMs = emptyList(),
            sqiScore = sqiScore,
            rejectionReason = reason,
            peakCount = peakCount,
            durationSec = durationSec
        )
    }
}

enum class RejectionReason(val userMessage: String) {
    INSUFFICIENT_RECORDING_TIME("Recording was too short — try again and hold for the full countdown."),
    INSUFFICIENT_SIGNAL("Finger not detected — place fingertip fully over the rear camera and flash."),
    SATURATION("Too much light — lighten finger pressure or move away from bright light."),
    MOTION_ARTIFACT("Motion detected — rest your hand on a surface and hold still."),
    TOO_FEW_BEATS("Signal too weak to extract a heartbeat — check contact and retry."),
    IRREGULAR_RHYTHM("Signal too irregular to score — retry with a steadier hand."),
    HARDWARE_UNAVAILABLE("Camera or flash unavailable — this device may not support fingertip-PPG capture.")
}
