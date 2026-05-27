package com.bios.app.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns a raw fingertip-PPG luminance waveform (one sample per camera frame)
 * into inter-beat intervals (IBIs) for [HrvAnalyzer], plus a signal-quality
 * judgement the UI can surface.
 *
 * Pipeline: zero-phase Butterworth bandpass [HR_BAND_LOW_HZ, HR_BAND_HIGH_HZ]
 * → adaptive-threshold peak detection → RR intervals → SQI scoring. The
 * bandpass replaces an earlier detrend(2s) + smooth(5-sample) moving-average
 * stack: on low-SNR captures (observed Pixel 9a) the moving-average pair let
 * baseline drift and motion-burst variance inflate the rolling-std term in the
 * peak detector, masking real systolic peaks during the recovery window and
 * producing ~20 % missed beats. A proper Butterworth concentrates energy in
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

    /** Bandpass low cutoff (Hz). 0.7 Hz = 42 bpm — slightly below the slowest
     *  resting HR we expect, so genuine bradycardia isn't filtered out while
     *  baseline drift (respiration, vasomotor) is. */
    private const val HR_BAND_LOW_HZ = 0.7

    /** Bandpass high cutoff (Hz). 3.5 Hz = 210 bpm — well above peak HR; the
     *  dichrotic-notch second harmonic is still let through so morphology
     *  analysis (notch position, AIx) still has signal. */
    private const val HR_BAND_HIGH_HZ = 3.5

    /** Default peak threshold: a local-max must exceed
     *  `rolling mean + K × rolling std` to be accepted. Per-device overrides
     *  live on [PpgDeviceProfile.peakThresholdK] — see Pixel 9a, where the
     *  default lets dichrotic-notch inflections cross the bar in quiet
     *  windows. */
    const val PEAK_THRESHOLD_K_DEFAULT = 0.5

    /** Rolling window for the adaptive threshold (seconds). */
    private const val THRESHOLD_WINDOW_SEC = 2.0

    /** SQI sub-scores below which we reject outright with a reason. */
    private const val MIN_LUMINANCE_VARIANCE = 1.0      // < 1 = no finger
    private const val MAX_SATURATION_RATIO = 0.30       // >30% saturated = overexposed
    /**
     * Default coefficient-of-variation cap on peak amplitudes after
     * trimming the lowest-amplitude quartile (those are dichrotic
     * notches and noise the adaptive-threshold detector lets
     * through). 0.80 is consistent with the amplitude variability
     * seen in clean fingertip PPG due to respiratory modulation and
     * vasomotor activity; tighter than that rejected real recordings
     * in on-device testing.
     *
     * Per-device overrides ([PpgDeviceProfile.maxPeakAmplitudeCov])
     * land via [PpgDeviceProfiles] once the [PpgCalibrationLogger]
     * distribution data is in (#266 Cut 2). The default preserves
     * pre-Cut-2 behaviour for devices not yet in
     * [PpgDeviceProfiles.PROFILES].
     */
    const val MAX_PEAK_AMPLITUDE_COV_DEFAULT = 0.80     // higher = motion
    /**
     * Fraction of the smallest-amplitude peaks dropped before computing the
     * amplitude CoV — the adaptive-threshold detector picks up dichrotic
     * notches and small noise peaks that inflate the CoV without indicating
     * motion.
     */
    private const val PEAK_AMPLITUDE_TRIM_FRACTION = 0.25
    /**
     * Default RR-interval coefficient-of-variation cap. Above this, the
     * processor rejects as IRREGULAR_RHYTHM. Per-device overrides land via
     * [PpgDeviceProfile.maxRrCov] for hardware whose adaptive-threshold
     * peak detector jitters RR intervals beyond this on regular rhythms
     * (low absolute PPG amplitude + small noise peaks the detector accepts).
     */
    const val MAX_RR_COV_DEFAULT = 0.30                 // higher = irregular

    /**
     * Minimum number of complete beats (foot → peak → foot triples) needed
     * before per-beat morphology features are surfaced. Below this, sample
     * variance washes out the means/CoVs and the field stays null so callers
     * don't render noise as morphology.
     */
    private const val MIN_BEATS_FOR_MORPHOLOGY = 10

    /**
     * Extract RR intervals and quality from a luminance waveform.
     * [luminanceSamples] is one Y-channel mean per camera frame, uniformly
     * sampled at [samplingRateHz]. Returns a [PpgResult] with either clean
     * RR intervals or a rejection reason.
     */
    fun extract(
        luminanceSamples: List<Double>,
        samplingRateHz: Double,
        profile: PpgDeviceProfile = PpgDeviceProfiles.DEFAULT,
    ): PpgResult {
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

        val smoothed = Bandpass.apply(
            samples = luminanceSamples.toDoubleArray(),
            lowHz = HR_BAND_LOW_HZ,
            highHz = HR_BAND_HIGH_HZ,
            samplingHz = samplingRateHz,
        )

        val thresholdWindow = (THRESHOLD_WINDOW_SEC * samplingRateHz).toInt().coerceAtLeast(3)
        val refractorySamples = (PEAK_REFRACTORY_MS * samplingRateHz / 1000.0).toInt().coerceAtLeast(1)
        val peakIndices = detectPeaks(smoothed, thresholdWindow, refractorySamples, profile.peakThresholdK)

        if (peakIndices.size < MIN_PEAKS_REQUIRED) {
            return PpgResult.rejected(
                RejectionReason.TOO_FEW_BEATS,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 20
            )
        }

        val peakAmpCov = trimmedAmplitudeCov(
            peakIndices.map { smoothed[it] },
            PEAK_AMPLITUDE_TRIM_FRACTION
        )
        if (peakAmpCov > profile.maxPeakAmplitudeCov) {
            return PpgResult.rejected(
                RejectionReason.MOTION_ARTIFACT,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 30,
                peakAmplitudeCov = peakAmpCov,
            )
        }

        val rrMs = peakIndices.zipWithNext { a, b -> (b - a) / samplingRateHz * 1000.0 }
        val rrCov = coefficientOfVariation(rrMs)
        if (rrCov > profile.maxRrCov) {
            return PpgResult.rejected(
                RejectionReason.IRREGULAR_RHYTHM,
                durationSec = durationSec,
                peakCount = peakIndices.size,
                sqiScore = 40,
                peakAmplitudeCov = peakAmpCov,
            )
        }

        val sqi = compositeSqi(
            variance, saturation, peakAmpCov, rrCov,
            profile.maxPeakAmplitudeCov, profile.maxRrCov,
        )
        val features = computeWaveformFeatures(smoothed, peakIndices, samplingRateHz, peakAmpCov)
        return PpgResult(
            rrIntervalsMs = rrMs,
            sqiScore = sqi,
            rejectionReason = null,
            peakCount = peakIndices.size,
            durationSec = durationSec,
            waveformFeatures = features,
            peakAmplitudeCov = peakAmpCov,
        )
    }

    /**
     * Statistical morphology summaries derived from the smoothed waveform and
     * the accepted peak indices. Produces no raw waveform — only
     * mean/CoV-style scalars suitable for persistence as derived metrics.
     *
     * Returns null when fewer than [MIN_BEATS_FOR_MORPHOLOGY] complete beats
     * can be measured (avoids surfacing noise as morphology).
     *
     * See CARDIOLOGY_POV.md §2.2 and the TCM / Sowa Rigpa / Kampo / Korean /
     * Siddha / Unani / Ayurveda pulse-quality audits for clinical context.
     */
    internal fun computeWaveformFeatures(
        smoothed: DoubleArray,
        peakIndices: List<Int>,
        samplingRateHz: Double,
        peakAmpCov: Double
    ): PulseWaveformFeatures? {
        if (peakIndices.size < MIN_BEATS_FOR_MORPHOLOGY) return null

        val peakAmps = peakIndices.map { smoothed[it] }
        val trimmedAmpMean = trimmedMean(peakAmps, PEAK_AMPLITUDE_TRIM_FRACTION)

        val riseTimesSec = mutableListOf<Double>()
        val decayTimesSec = mutableListOf<Double>()
        val notchRelativePositions = mutableListOf<Double>()
        val augmentationIndices = mutableListOf<Double>()

        for (i in peakIndices.indices) {
            val peakIdx = peakIndices[i]
            val prevPeakIdx = if (i == 0) 0 else peakIndices[i - 1]
            val nextPeakIdx = if (i == peakIndices.size - 1) smoothed.size - 1 else peakIndices[i + 1]

            val footBefore = findTrough(smoothed, prevPeakIdx, peakIdx)
            val footAfter = findTrough(smoothed, peakIdx, nextPeakIdx)
            if (footBefore >= peakIdx || footAfter <= peakIdx) continue

            val riseSamples = peakIdx - footBefore
            val decaySamples = footAfter - peakIdx
            if (riseSamples <= 0 || decaySamples <= 0) continue

            riseTimesSec.add(riseSamples / samplingRateHz)
            decayTimesSec.add(decaySamples / samplingRateHz)

            val notchOffsetSamples = findDichroticNotch(smoothed, peakIdx, footAfter)
            if (notchOffsetSamples != null) {
                val beatPeriodSamples = (footAfter - footBefore).toDouble()
                if (beatPeriodSamples > 0) {
                    notchRelativePositions.add((notchOffsetSamples + (peakIdx - footBefore)) / beatPeriodSamples)
                }
                augmentationIndexForBeat(
                    smoothed, peakIdx, footBefore, footAfter, notchOffsetSamples
                )?.let { augmentationIndices.add(it) }
            }
        }

        if (riseTimesSec.size < MIN_BEATS_FOR_MORPHOLOGY) return null

        val riseMean = riseTimesSec.average()
        val riseCov = coefficientOfVariation(riseTimesSec)
        val decayMean = decayTimesSec.average()
        // Decay-asymmetry index ∈ (-1, 1). >0 means decay is slower than rise
        // (typical healthy pulse); near 0 means symmetric (stiffer arterial bed).
        val decayAsymmetry = if (riseMean + decayMean > 0) {
            (decayMean - riseMean) / (riseMean + decayMean)
        } else {
            0.0
        }
        val notchPosition = if (notchRelativePositions.size >= MIN_BEATS_FOR_MORPHOLOGY) {
            notchRelativePositions.average()
        } else {
            null
        }
        // Augmentation-index quorum is intentionally half the morphology quorum:
        // the notch is the limiting factor (not always detectable beat-to-beat
        // on peripheral PPG) so insisting on MIN_BEATS_FOR_MORPHOLOGY would
        // suppress the metric on perfectly usable recordings.
        val augmentationIndex = if (augmentationIndices.size * 2 >= MIN_BEATS_FOR_MORPHOLOGY) {
            augmentationIndices.average()
        } else {
            null
        }

        return PulseWaveformFeatures(
            peakAmplitudeTrimmedMean = trimmedAmpMean,
            peakAmplitudeCov = peakAmpCov,
            riseTimeMeanSec = riseMean,
            riseTimeCov = riseCov,
            decayAsymmetryIndex = decayAsymmetry,
            dichroticNotchRelativePosition = notchPosition,
            augmentationIndexPercent = augmentationIndex,
            beatsMeasured = riseTimesSec.size
        )
    }

    /**
     * Per-beat augmentation index from the diastolic rebound height.
     * Locates the local maximum on the decay limb that follows the dichrotic
     * notch (the diastolic peak / reflected wave) and returns
     * (1 − h_diastolic / h_systolic) × 100, clamped to [0, 100]. Returns null
     * when no rebound rises above the notch within the search window — a
     * collapsed pulse contour or a noisy beat the caller should skip rather
     * than fold into the average.
     *
     * Heights are measured from the beat's foot baseline so DC offset and
     * detrend artefacts cancel. The result is *not* clinical SphygmoCor AIx
     * — it is a peripheral-PPG proxy whose absolute calibration drifts with
     * sensor distance from the heart. Useful for trend tracking against the
     * owner's own baseline.
     */
    internal fun augmentationIndexForBeat(
        smoothed: DoubleArray,
        peakIdx: Int,
        footBefore: Int,
        footAfter: Int,
        notchOffsetSamples: Int
    ): Double? {
        val notchIdx = peakIdx + notchOffsetSamples
        if (notchIdx >= footAfter - 1) return null

        var diastolicIdx = notchIdx + 1
        var diastolicVal = smoothed[diastolicIdx]
        for (j in notchIdx + 2 until footAfter) {
            if (smoothed[j] > diastolicVal) {
                diastolicVal = smoothed[j]
                diastolicIdx = j
            }
        }
        // Reject beats whose "rebound" does not actually rise above the notch
        // amplitude — those are smooth decays, not a reflected wave.
        if (diastolicVal <= smoothed[notchIdx]) return null

        val systolicHeight = smoothed[peakIdx] - smoothed[footBefore]
        if (systolicHeight <= 0.0) return null
        val diastolicHeight = diastolicVal - smoothed[footBefore]
        if (diastolicHeight <= 0.0) return null

        return ((1.0 - diastolicHeight / systolicHeight) * 100.0).coerceIn(0.0, 100.0)
    }

    /** Index of the minimum sample in [from, to). Falls back to [from] when range empty. */
    internal fun findTrough(samples: DoubleArray, from: Int, to: Int): Int {
        if (to <= from) return from
        var minIdx = from
        var minVal = samples[from]
        for (j in from + 1 until to) {
            if (samples[j] < minVal) {
                minVal = samples[j]
                minIdx = j
            }
        }
        return minIdx
    }

    /**
     * Locate the dichrotic notch on the decay limb between [peakIdx] and
     * [footAfter]. The notch is the local minimum that precedes a small
     * secondary inflection (diastolic wave). Returns the offset *from peakIdx*
     * or null if no notch-like inflection is found. Searches only the first
     * 60 % of the decay limb (notches always sit before mid-diastole).
     */
    internal fun findDichroticNotch(samples: DoubleArray, peakIdx: Int, footAfter: Int): Int? {
        val span = footAfter - peakIdx
        if (span < 4) return null
        val searchEnd = peakIdx + (span * 0.6).toInt().coerceAtLeast(3)
        // Look for a local minimum: samples[i] < both neighbours.
        for (i in peakIdx + 2 until searchEnd - 1) {
            if (samples[i] < samples[i - 1] && samples[i] <= samples[i + 1]) {
                // Confirm a small rebound follows (the diastolic wave).
                val rebound = samples.slice(i..(i + 2).coerceAtMost(footAfter)).maxOrNull() ?: samples[i]
                if (rebound > samples[i]) return i - peakIdx
            }
        }
        return null
    }

    /** Mean after dropping the lowest-[trimFraction] of [values]. */
    internal fun trimmedMean(values: List<Double>, trimFraction: Double): Double {
        if (values.isEmpty()) return 0.0
        if (values.size < 4) return values.average()
        val sorted = values.sorted()
        val dropCount = (sorted.size * trimFraction).toInt().coerceAtLeast(1)
        return sorted.drop(dropCount).average()
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

    /**
     * CoV computed after dropping the lowest-[trimFraction] of [values].
     * Used on peak amplitudes so dichrotic notches and noise peaks the
     * detector picks up don't inflate the motion-judgement metric.
     */
    internal fun trimmedAmplitudeCov(values: List<Double>, trimFraction: Double): Double {
        if (values.size < 4) return coefficientOfVariation(values)
        val sorted = values.sorted()
        val dropCount = (sorted.size * trimFraction).toInt().coerceAtLeast(1)
        return coefficientOfVariation(sorted.drop(dropCount))
    }

    /** Composite 0–100 quality score. Higher is better. */
    internal fun compositeSqi(
        variance: Double,
        saturationRatio: Double,
        peakAmpCov: Double,
        rrCov: Double,
        maxPeakAmplitudeCov: Double = MAX_PEAK_AMPLITUDE_COV_DEFAULT,
        maxRrCov: Double = MAX_RR_COV_DEFAULT,
    ): Int {
        // Each term 0..1 (1 = perfect); combine by geometric mean, rescale to 100.
        val varianceTerm = (variance / 50.0).coerceIn(0.0, 1.0)
        val saturationTerm = (1.0 - saturationRatio / MAX_SATURATION_RATIO).coerceIn(0.0, 1.0)
        val ampTerm = (1.0 - peakAmpCov / maxPeakAmplitudeCov).coerceIn(0.0, 1.0)
        val rrTerm = (1.0 - rrCov / maxRrCov).coerceIn(0.0, 1.0)
        val geomMean = (varianceTerm * saturationTerm * ampTerm * rrTerm)
        return (sqrt(sqrt(geomMean)) * 100.0).toInt().coerceIn(0, 100)
    }
}

