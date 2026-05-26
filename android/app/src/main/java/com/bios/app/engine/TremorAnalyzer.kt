package com.bios.app.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Phone-accelerometer / gyroscope tremor pipeline (#206, NEUROLOGY_POV §2.4).
 *
 * Tremor is a rhythmic involuntary oscillation of a body part. The clinically
 * meaningful physiologic band is 3–12 Hz; Parkinsonian rest tremor sits in
 * the 4–6 Hz sub-band, essential tremor in 4–12 Hz postural / action.
 *
 * Apple's MovementDisorderAPI (HealthKit, watchOS) exposes tremor and
 * dyskinesia analytics derived from the same accelerometer streams; Roche
 * Floodlight Open ships an open-source analytics framework on the same
 * physiological substrate, and MJFF mPower demonstrated the wrist /
 * smartphone-accelerometer signal long before the platform APIs existed.
 * The MDS-UPDRS examination scale anchors the clinical phenotype.
 *
 * This analyzer takes a window of timestamped accelerometer (or gyroscope)
 * samples — gravity-removed magnitudes work, or the dominant axis after
 * gravity rejection — and reports:
 *
 *  - **peakFrequencyHz** — strongest spectral peak in the 3–12 Hz band, found
 *    via Goertzel evaluations on a dense grid (0.25 Hz resolution by default).
 *    Goertzel is the standard narrow-band evaluator for tremor analysis
 *    because it costs O(N) per probe frequency and avoids the FFT's
 *    power-of-two length constraint.
 *  - **rmsAmplitude** — root-mean-square amplitude of the band-passed
 *    component at the peak frequency. Units are whatever the input samples
 *    use; downstream callers feed gravity-removed m/s² and the
 *    [com.bios.contracts.MetricType.TREMOR_AMPLITUDE_G] writer stores SI.
 *  - **classification** — REST_PARKINSONIAN if the peak is in [4 Hz, 6 Hz]
 *    and posture is REST, POSTURAL_ESSENTIAL if peak is in [4 Hz, 12 Hz]
 *    and posture is POSTURAL_OR_ACTION, otherwise NON_PATHOLOGIC.
 *  - **insufficient-data verdict** — when the window is too short or peak
 *    power is below the band noise floor.
 *
 * **Manifesto stance**: this analyzer never names a diagnosis. The output is
 * a per-window observation. The pull-side [com.bios.app.ui.tremor.TremorTrendScreen]
 * surfaces the (frequency, amplitude) history; the suggested action always
 * references the owner's clinician. Owner-pull only.
 *
 * References:
 *  - Goertzel G (1958) — An Algorithm for the Evaluation of Finite
 *    Trigonometric Series. American Mathematical Monthly 65(1):34–35.
 *  - Goetz CG et al. (2008) — MDS-UPDRS, Movement Disorders 23:2129–2170.
 *  - Bot BM et al. (2016) — The mPower study, Parkinson's disease mobile
 *    data collected via iPhone. Sci Data 3:160011.
 *  - Roche Floodlight Open analytics — Bonati et al. (2022) digital
 *    biomarkers for movement disorders, Movement Disorders 37(2):255–265.
 *  - Apple MovementDisorderAPI (HealthKit) — Movement Disorder API
 *    documentation; HKQuantityTypeIdentifierAppleMovingTime.
 */
object TremorAnalyzer {

    /** Lower bound of the physiologic tremor band (Hz). */
    const val TREMOR_BAND_LOW_HZ = 3.0

    /** Upper bound of the physiologic tremor band (Hz). */
    const val TREMOR_BAND_HIGH_HZ = 12.0

    /** Sub-band lower bound for Parkinsonian rest tremor (Hz). */
    const val PARKINSONIAN_REST_LOW_HZ = 4.0

    /** Sub-band upper bound for Parkinsonian rest tremor (Hz). */
    const val PARKINSONIAN_REST_HIGH_HZ = 6.0

    /** Grid resolution for the Goertzel sweep. 0.25 Hz separates 4 Hz from
     *  5 Hz from 6 Hz comfortably and stays well inside the typical 50 Hz
     *  Android accelerometer sample rate's Nyquist. */
    const val GRID_RESOLUTION_HZ = 0.25

    /** Minimum sample count before a verdict is credible. 64 samples at
     *  50 Hz is 1.28 s — short enough to track ephemeral postural episodes,
     *  long enough for a 3 Hz cycle (~333 ms period) to fit three times. */
    const val MIN_SAMPLES = 64

    /**
     * Peak-amplitude floor below which the window is scored NON_PATHOLOGIC.
     * Resting-hand acceleration noise from a phone-on-table fixture sits
     * around 0.005–0.01 m/s²; physiologic tremor sits an order of magnitude
     * above. The default is conservative — it suppresses sensor noise but
     * accepts mild tremor.
     */
    const val MIN_RMS_AMPLITUDE = 0.02

    /** Posture inferred from accelerometer steadiness or set by the caller. */
    enum class Posture {
        /** Hand at rest in lap or by side; mean magnitude near gravity. */
        REST,

        /** Hand held against gravity (arms outstretched) or in active
         *  movement (writing, drinking, MDS-UPDRS finger-to-nose). */
        POSTURAL_OR_ACTION,

        /** Not inferable from the window. */
        UNKNOWN,
    }

    /** Classification verdict for the window. */
    enum class TremorClassification {
        /** Peak in [4, 6] Hz at REST — Parkinsonian rest tremor pattern. */
        REST_PARKINSONIAN,

        /** Peak in [4, 12] Hz with postural / action posture — essential
         *  tremor pattern. Distinguished from REST_PARKINSONIAN by posture
         *  *and* by extending above 6 Hz. */
        POSTURAL_ESSENTIAL,

        /** Peak in the band but classification ambiguous (e.g. posture
         *  unknown, or 4–6 Hz with postural/action). */
        AMBIGUOUS,

        /** No tremor signal above the noise floor in the physiologic band. */
        NON_PATHOLOGIC,

        /** Too few samples to evaluate. */
        INSUFFICIENT_DATA,
    }

    /** Full per-window analyzer output. */
    data class TremorEpisode(
        /** Peak frequency in the 3–12 Hz band (Hz). 0.0 when insufficient
         *  data or no signal above the noise floor. */
        val peakFrequencyHz: Double,
        /** RMS amplitude at the peak frequency, in the input sample's units
         *  (callers feed m/s²). 0.0 when insufficient data. */
        val rmsAmplitude: Double,
        /** Posture used for classification. */
        val posture: Posture,
        /** Classification verdict. */
        val classification: TremorClassification,
        /** Sample count actually evaluated. */
        val sampleCount: Int,
        /** Effective sample rate used for the spectral sweep (Hz). */
        val sampleRateHz: Double,
    )

    /**
     * Analyses a one-dimensional signal (gravity-removed accelerometer
     * magnitude, or any axis after gravity rejection) sampled at
     * [sampleRateHz]. Returns a [TremorEpisode] describing the dominant
     * spectral content in the physiologic band.
     *
     * @param samples gravity-removed magnitudes (m/s²) or single-axis values.
     * @param sampleRateHz effective sample rate. Most Android phones report
     *        50 Hz from `SENSOR_DELAY_NORMAL` accelerometers; sample-and-
     *        hold rates vary by vendor so the caller measures and passes
     *        it in rather than assuming.
     * @param posture caller-asserted posture, when known. Defaults to
     *        UNKNOWN; passing REST or POSTURAL_OR_ACTION narrows the
     *        classification verdict.
     */
    fun analyze(
        samples: DoubleArray,
        sampleRateHz: Double,
        posture: Posture = Posture.UNKNOWN,
    ): TremorEpisode {
        if (samples.size < MIN_SAMPLES || sampleRateHz < 2 * TREMOR_BAND_HIGH_HZ) {
            return TremorEpisode(
                peakFrequencyHz = 0.0,
                rmsAmplitude = 0.0,
                posture = posture,
                classification = TremorClassification.INSUFFICIENT_DATA,
                sampleCount = samples.size,
                sampleRateHz = sampleRateHz,
            )
        }

        // De-mean the signal so the DC component doesn't bias the spectral
        // sweep. Spectral evaluators are sensitive to a constant offset
        // (gravity bleed-through, sensor bias) at f≈0; subtracting the
        // mean removes the strongest non-tremor contributor.
        val mean = samples.average()
        val centered = DoubleArray(samples.size) { samples[it] - mean }

        // Goertzel sweep across the 3–12 Hz band.
        var bestFreq = 0.0
        var bestPower = 0.0
        var freq = TREMOR_BAND_LOW_HZ
        while (freq <= TREMOR_BAND_HIGH_HZ + 1e-9) {
            val power = goertzelPower(centered, sampleRateHz, freq)
            if (power > bestPower) {
                bestPower = power
                bestFreq = freq
            }
            freq += GRID_RESOLUTION_HZ
        }

        // Convert Goertzel single-bin power to peak-frequency RMS amplitude.
        // Goertzel returns |X_k|² in DFT-magnitude units; for a real signal
        // a pure sinusoid of amplitude A at exactly bin k has |X_k| = N*A/2,
        // so RMS amplitude = sqrt(2 * power) / N (RMS of A·cos = A/√2).
        val rms = if (bestPower > 0.0) {
            sqrt(2.0 * bestPower) / centered.size
        } else 0.0

        if (rms < MIN_RMS_AMPLITUDE) {
            return TremorEpisode(
                peakFrequencyHz = 0.0,
                rmsAmplitude = rms,
                posture = posture,
                classification = TremorClassification.NON_PATHOLOGIC,
                sampleCount = centered.size,
                sampleRateHz = sampleRateHz,
            )
        }

        val classification = classify(bestFreq, posture)

        return TremorEpisode(
            peakFrequencyHz = bestFreq,
            rmsAmplitude = rms,
            posture = posture,
            classification = classification,
            sampleCount = centered.size,
            sampleRateHz = sampleRateHz,
        )
    }

    /**
     * Classifies the (peakFreq, posture) tuple per the published tremor
     * phenotypes. Roche Floodlight Open and the MDS-UPDRS examination
     * structure both anchor on the 4–6 Hz rest / 4–12 Hz postural-action
     * distinction; the AMBIGUOUS verdict surfaces overlap cases without
     * forcing a label.
     */
    fun classify(peakFreqHz: Double, posture: Posture): TremorClassification {
        val inBand = peakFreqHz in TREMOR_BAND_LOW_HZ..TREMOR_BAND_HIGH_HZ
        if (!inBand) return TremorClassification.NON_PATHOLOGIC
        val inRestSubBand = peakFreqHz in PARKINSONIAN_REST_LOW_HZ..PARKINSONIAN_REST_HIGH_HZ
        return when (posture) {
            Posture.REST ->
                if (inRestSubBand) TremorClassification.REST_PARKINSONIAN
                else TremorClassification.AMBIGUOUS
            Posture.POSTURAL_OR_ACTION ->
                // Essential tremor presents postural / action and can sit
                // anywhere in 4–12 Hz; the upper-band peaks (>6 Hz) are
                // particularly characteristic.
                if (peakFreqHz >= PARKINSONIAN_REST_LOW_HZ)
                    TremorClassification.POSTURAL_ESSENTIAL
                else TremorClassification.AMBIGUOUS
            Posture.UNKNOWN -> TremorClassification.AMBIGUOUS
        }
    }

    /**
     * Goertzel single-frequency power estimate. Computes |X_k|² where k is
     * the (continuous) bin matching [targetFreqHz] at the given sample
     * rate. O(N) per call — efficient for the few-dozen probes the sweep
     * needs across the 3–12 Hz band.
     */
    private fun goertzelPower(
        samples: DoubleArray,
        sampleRateHz: Double,
        targetFreqHz: Double,
    ): Double {
        val n = samples.size
        val omega = 2.0 * PI * targetFreqHz / sampleRateHz
        val coeff = 2.0 * cos(omega)
        var s0: Double
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        // |X|² = s1² + s2² - coeff · s1 · s2.
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return if (power < 0) 0.0 else power
    }

    /**
     * Convenience: compute gravity-removed magnitudes from raw 3-axis
     * accelerometer samples, accepting the local definition of standard
     * gravity. Callers that already do their own gravity rejection (high-
     * pass filter, vendor sensor fusion) feed the magnitudes directly to
     * [analyze] and skip this helper.
     */
    fun gravityRemovedMagnitude(
        ax: Double, ay: Double, az: Double, gravity: Double = STANDARD_GRAVITY,
    ): Double {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        return abs(mag - gravity)
    }

    /** Standard gravity in m/s². NIST CODATA 2018. */
    const val STANDARD_GRAVITY = 9.80665
}
