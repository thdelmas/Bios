package com.bios.app.engine

/**
 * Statistical morphology of an accepted PPG recording. All fields are scalar
 * summaries — no raw waveform is ever stored, which keeps storage discipline
 * and the manifesto's "instrument-not-collector" stance.
 *
 * Field definitions:
 * - [peakAmplitudeTrimmedMean]: mean of systolic peak heights after dropping
 *   the lowest 25 % (dichrotic notches / noise the detector lets through).
 *   In raw luminance units of the smoothed signal.
 * - [peakAmplitudeCov]: coefficient-of-variation of the trimmed peak heights.
 *   Vasomotor / sympathetic-tone proxy (Selvaraj 2008).
 * - [riseTimeMeanSec]: mean foot-to-peak time. Inverse-related to
 *   pulse-wave-velocity / arterial stiffness (Mitchell 2010; Ben-Shlomo 2014).
 * - [riseTimeCov]: rise-time CoV. Beat-to-beat consistency proxy.
 * - [decayAsymmetryIndex]: (decayTime − riseTime) / (decayTime + riseTime),
 *   ∈ (−1, 1). Healthy compliant arteries → asymmetric pulse (positive index);
 *   stiffer beds → more symmetric pulse (toward 0). Related to augmentation
 *   index family (Vlachopoulos 2010).
 * - [dichroticNotchRelativePosition]: fraction of the foot-to-foot beat period
 *   at which the dichrotic notch (closure of the aortic valve) appears, averaged
 *   across beats with a detectable notch. Null if too few notches were found
 *   to summarise. Diastolic-function / vascular-tone proxy (Allen 2007;
 *   Elgendi 2012).
 * - [augmentationIndexPercent]: PPG-derived augmentation index in percent,
 *   averaged across beats with both a notch and a diastolic-rebound peak.
 *   (1 − h_diastolic / h_systolic) × 100 clamped to [0, 100]. Higher =
 *   stiffer arterial bed (clinical SphygmoCor AIx direction). Null when too
 *   few beats yielded a measurable rebound. Blueprint audit §3.1 #8.
 * - [beatsMeasured]: number of complete foot→peak→foot triples used to compute
 *   the summaries above. Informational; lets downstream consumers gate display
 *   on a minimum sample size.
 *
 * These features are not exotic — Withings BPM Core, Aktiia, Empatica, and the
 * academic vascular-aging literature all surface a subset. Bios stores them so
 * later pull-side views (arterial-stiffness reference, traditional-medicine
 * pulse-quality readers) can consume them without a new sensor pass.
 */
data class PulseWaveformFeatures(
    val peakAmplitudeTrimmedMean: Double,
    val peakAmplitudeCov: Double,
    val riseTimeMeanSec: Double,
    val riseTimeCov: Double,
    val decayAsymmetryIndex: Double,
    val dichroticNotchRelativePosition: Double?,
    val augmentationIndexPercent: Double?,
    val beatsMeasured: Int,
)
