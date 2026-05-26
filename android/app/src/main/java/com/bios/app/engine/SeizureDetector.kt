package com.bios.app.engine

import kotlin.math.sqrt

/**
 * Wearable convulsive-seizure detector (issue #269 / NEUROLOGY_POV §2.12).
 *
 * Closest analogue in published literature: the Empatica Embrace2 /
 * EmbracePlus FDA-cleared GTCS detector, which corroborates rhythmic
 * accelerometer motion with ictal-tachycardia (autonomic surge during a
 * generalised tonic-clonic seizure). This detector keeps the same two
 * pillars without the proprietary classifier:
 *
 *   1. **Rhythmic-motion gate.** Per-second power-spectrum check over the
 *      accelerometer-norm series. A second is "convulsive-like" when at
 *      least [RHYTHMIC_BAND_RATIO] of its broadband power falls in the
 *      2–8 Hz band — the canonical clonic-phase frequency range
 *      (Beniczky 2013, Conradsen 2012). Sustained ≥ [MIN_SUSTAINED_SEC]
 *      consecutive convulsive-like seconds is the candidate window.
 *   2. **Ictal-tachycardia corroborator.** Median HR inside the candidate
 *      window must be ≥ [HR_RISE_FRACTION] above the median of the
 *      [PRE_EVENT_BASELINE_SEC]-second window immediately preceding the
 *      candidate. Without HR substrate the detector returns no event
 *      (rather than firing on accelerometer alone) — specificity, not
 *      sensitivity, is the priority for an automated path.
 *
 * Output is intentionally minimal: one [Detection] per qualifying window,
 * with `durationSec` carrying the detection window length. The caller
 * (see [SeizureEvent]) wraps each detection in a SEIZURE_EVENT
 * MetricReading with `detection_source = "wearable_inferred"` in
 * event_payloads so the pull-side timeline can distinguish wearable-
 * inferred events from owner-logged ones.
 *
 * ## Honest scope
 *
 * - Focal and absence seizures are not wearable-detectable from this
 *   signal pair — they lack the bilateral rhythmic motor signature.
 *   Documented in NEUROLOGY_POV §2.12.
 * - The detector cannot discriminate non-epileptic psychogenic seizures
 *   (PNES) from convulsive epileptic seizures from autonomic substrate
 *   alone; per NEUROLOGY_POV §3.3 the peri-event autonomic signature
 *   stays in the timeline for clinician review but is not surfaced as a
 *   separate alert.
 * - Push-side notification stays gated on owner confirmation (manifesto
 *   guard from #269). Wearable-inferred events default to ADVISORY in the
 *   timeline; the URGENT escalation path is the existing
 *   [com.bios.app.alerts.NeurologyUrgentPatterns.statusEpilepticusConvulsive]
 *   pattern keyed on owner-confirmed SEIZURE_EVENT rows.
 *
 * ## References
 *
 * - Beniczky S et al. (2013) — Detection of generalized tonic-clonic
 *   seizures by a wireless wrist accelerometer: a prospective,
 *   multicenter study. *Epilepsia* 54(4):e58-e61.
 * - Conradsen I et al. (2012) — Automatic multi-modal intelligent seizure
 *   acquisition (MISA) system for detection of motor seizures from
 *   electromyographic data and motion data. *Computer Methods and
 *   Programs in Biomedicine* 107(1):97-110.
 * - Onorati F et al. (2017) — Multicenter clinical assessment of
 *   improved wearable multimodal convulsive seizure detectors.
 *   *Epilepsia* 58(11):1870-1879. (Embrace2 corroborator design.)
 * - Empatica EmbracePlus — FDA 510(k) K203626. Convulsive seizure
 *   detection from wrist accelerometer + EDA + PPG corroboration.
 */
object SeizureDetector {

    /**
     * Single time-stamped HR sample used by the corroborator. Real-time
     * HR is produced by the camera-PPG path and wearable adapters; the
     * detector accepts a flat series with epoch-ms timestamps so any
     * source can feed it without an adapter-specific wrapper.
     */
    data class HrSample(val timestampMs: Long, val bpm: Double)

    /**
     * One qualifying detection. `startTimestampMs` is the epoch-ms of
     * the first convulsive-like second; `durationSec` is the count of
     * sustained convulsive-like seconds; `medianHrBpm` and
     * `baselineHrBpm` are exposed so the caller can record the actual
     * peri-event substrate (clinician review may want to see the ratio).
     */
    data class Detection(
        val startTimestampMs: Long,
        val durationSec: Int,
        val medianHrBpm: Double,
        val baselineHrBpm: Double,
    )

    /** Sustained-rhythm gate (seconds). 10 s = Beniczky 2013 minimum window. */
    const val MIN_SUSTAINED_SEC: Int = 10

    /** Clonic-phase low edge (Hz). Below 2 Hz is non-seizure rhythmic motion (walking, breathing). */
    const val RHYTHMIC_BAND_LOW_HZ: Double = 2.0

    /** Clonic-phase high edge (Hz). Above 8 Hz is too fast for clonic motor activity. */
    const val RHYTHMIC_BAND_HIGH_HZ: Double = 8.0

    /** Per-second power ratio in the rhythmic band required to mark a second "convulsive-like". */
    const val RHYTHMIC_BAND_RATIO: Double = 0.60

    /** Minimum total per-second amplitude before the ratio gate applies — quiet seconds never trigger. */
    const val MIN_RMS_M_PER_S2: Double = 1.0

    /** Ictal-tachycardia corroborator: median HR rise above pre-event baseline. */
    const val HR_RISE_FRACTION: Double = 0.20

    /** Pre-event HR baseline window (seconds). 30 s per #269 spec. */
    const val PRE_EVENT_BASELINE_SEC: Int = 30

    /** Minimum samples per 1-s window. Below this the per-second FFT is undefined. */
    private const val MIN_SAMPLES_PER_SECOND: Int = 8

    /**
     * Analyse a contiguous accelerometer-norm series for convulsive-seizure
     * candidates.
     *
     * @param accelNormMagnitudes Per-sample acceleration vector magnitude
     *   minus gravity (`sqrt(x²+y²+z²) - g`), so the DC component is
     *   already removed. Same convention as [com.bios.app.ingest.PhoneSensorAdapter].
     * @param startTimestampMs Epoch-ms of the first sample in
     *   [accelNormMagnitudes]. Used to time-align HR.
     * @param sampleRateHz Sampling rate of the accelerometer series. The
     *   detector chunks the series into 1-second windows of
     *   `sampleRateHz` samples each.
     * @param hrSeries Heart-rate samples covering at least the
     *   [PRE_EVENT_BASELINE_SEC] seconds immediately before
     *   `startTimestampMs` plus the duration of [accelNormMagnitudes].
     *   Without HR substrate spanning the baseline window the
     *   corroborator cannot fire and the detector returns an empty list.
     */
    fun analyse(
        accelNormMagnitudes: List<Double>,
        startTimestampMs: Long,
        sampleRateHz: Double,
        hrSeries: List<HrSample>,
    ): List<Detection> {
        val samplesPerSecond = sampleRateHz.toInt()
        if (samplesPerSecond < MIN_SAMPLES_PER_SECOND) return emptyList()
        if (accelNormMagnitudes.size < samplesPerSecond * MIN_SUSTAINED_SEC) return emptyList()

        val convulsiveLikeBySecond = markConvulsiveLikeSeconds(
            accelNormMagnitudes, samplesPerSecond
        )

        val detections = mutableListOf<Detection>()
        var runStart = -1
        var i = 0
        while (i < convulsiveLikeBySecond.size) {
            if (convulsiveLikeBySecond[i]) {
                if (runStart < 0) runStart = i
            } else {
                if (runStart >= 0 && i - runStart >= MIN_SUSTAINED_SEC) {
                    corroborate(runStart, i, startTimestampMs, hrSeries)?.let(detections::add)
                }
                runStart = -1
            }
            i++
        }
        if (runStart >= 0 && convulsiveLikeBySecond.size - runStart >= MIN_SUSTAINED_SEC) {
            corroborate(runStart, convulsiveLikeBySecond.size, startTimestampMs, hrSeries)
                ?.let(detections::add)
        }
        return detections
    }

    /**
     * Per-second pass over the magnitude series: each second is marked
     * convulsive-like when the share of broadband power inside 2–8 Hz
     * meets [RHYTHMIC_BAND_RATIO] and the per-second RMS clears the
     * quiet-second floor.
     */
    private fun markConvulsiveLikeSeconds(
        accelNormMagnitudes: List<Double>,
        samplesPerSecond: Int,
    ): BooleanArray {
        val secondsAvailable = accelNormMagnitudes.size / samplesPerSecond
        val flags = BooleanArray(secondsAvailable)
        val nfft = nextPowerOfTwo(samplesPerSecond)
        val real = DoubleArray(nfft)
        val imag = DoubleArray(nfft)
        val df = samplesPerSecond.toDouble() / nfft
        val halfN = nfft / 2

        for (s in 0 until secondsAvailable) {
            // Copy this second's samples; the rest of the FFT buffer is
            // zero-padded (and reset each iteration).
            var rms2 = 0.0
            for (k in 0 until samplesPerSecond) {
                val v = accelNormMagnitudes[s * samplesPerSecond + k]
                real[k] = v
                rms2 += v * v
            }
            for (k in samplesPerSecond until nfft) real[k] = 0.0
            for (k in 0 until nfft) imag[k] = 0.0
            val rms = sqrt(rms2 / samplesPerSecond)
            if (rms < MIN_RMS_M_PER_S2) continue

            Spectral.fftInPlace(real, imag)

            var bandPower = 0.0
            var totalPower = 0.0
            // One-sided power: bin 0 and Nyquist are unique, the rest
            // double-counted. Magnitude² is enough to compute a ratio —
            // we don't need PSD-scaled values here.
            for (kBin in 0..halfN) {
                val mag2 = real[kBin] * real[kBin] + imag[kBin] * imag[kBin]
                val oneSided = if (kBin == 0 || kBin == halfN) mag2 else mag2 * 2.0
                totalPower += oneSided
                val f = kBin * df
                if (f >= RHYTHMIC_BAND_LOW_HZ && f <= RHYTHMIC_BAND_HIGH_HZ) {
                    bandPower += oneSided
                }
            }
            if (totalPower <= 0.0) continue
            if (bandPower / totalPower >= RHYTHMIC_BAND_RATIO) flags[s] = true
        }
        return flags
    }

    /**
     * Confirm a candidate window with the ictal-tachycardia corroborator.
     * Returns the [Detection] when median HR inside the window is at
     * least [HR_RISE_FRACTION] above the pre-event baseline; null when
     * baseline coverage is insufficient or the HR rise gate fails.
     */
    private fun corroborate(
        runStartSec: Int,
        runEndSec: Int,
        startTimestampMs: Long,
        hrSeries: List<HrSample>,
    ): Detection? {
        val windowStartMs = startTimestampMs + runStartSec * 1_000L
        val windowEndMs = startTimestampMs + runEndSec * 1_000L
        val baselineStartMs = windowStartMs - PRE_EVENT_BASELINE_SEC * 1_000L

        val baselineSamples = hrSeries.filter {
            it.timestampMs in baselineStartMs until windowStartMs
        }
        val eventSamples = hrSeries.filter {
            it.timestampMs in windowStartMs until windowEndMs
        }
        // Both windows must carry HR substrate. The baseline floor of 3
        // samples is a soft guard against firing on a single anomalous
        // pre-event reading; the event-window floor is 1 because a brief
        // candidate may legitimately straddle very few HR samples.
        if (baselineSamples.size < 3 || eventSamples.isEmpty()) return null

        val baselineMedian = medianOf(baselineSamples.map { it.bpm })
        val eventMedian = medianOf(eventSamples.map { it.bpm })
        if (baselineMedian <= 0.0) return null
        val rise = (eventMedian - baselineMedian) / baselineMedian
        if (rise < HR_RISE_FRACTION) return null

        return Detection(
            startTimestampMs = windowStartMs,
            durationSec = runEndSec - runStartSec,
            medianHrBpm = eventMedian,
            baselineHrBpm = baselineMedian,
        )
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    private fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
