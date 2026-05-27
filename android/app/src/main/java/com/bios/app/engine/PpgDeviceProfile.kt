package com.bios.app.engine

/**
 * Device-specific motion-rejection thresholds for the camera-PPG
 * capture path (#266 Cut 2).
 *
 * The eyeball-tuned defaults in [CaptureQuality] and
 * [PpgSignalProcessor] over-reject on hardware whose LED-to-lens
 * geometry puts more frame-to-frame variability into the live
 * luminance trace than the constants expect (Pixel 9a is the known
 * regression). The actigraphy / PPG literature is consistent that
 * motion thresholds are device-dependent — what reads as "still"
 * on a Pixel 6 is "moving" on a 9a and vice versa.
 *
 * This profile decouples the rejection logic from a global cutoff,
 * letting [PpgDeviceProfiles.forDevice] return per-device values
 * derived from the [PpgCalibrationLogger] (#266 Cut 1) distribution
 * data as it accumulates. Unknown devices fall back to the
 * [PpgDeviceProfiles.DEFAULT] which preserves pre-Cut-2 behavior —
 * no regression risk for the device set that currently works.
 *
 * Both thresholds are tuned together. The real-time check
 * ([motionMedianJumpY]) and the offline check ([maxPeakAmplitudeCov])
 * exist to catch the same kind of motion artifact at two layers;
 * setting them independently for a device produces confusing
 * inconsistency (real-time says STEADY, offline rejects after 60 s).
 * Per-device profiles must keep them coupled.
 */
data class PpgDeviceProfile(
    /**
     * Median frame-to-frame Y jump (8-bit luminance) the real-time
     * classifier accepts before flipping to MOTION. Higher = more
     * tolerant; per-device tuning lets noisier hardware stay below
     * the line without genuine motion slipping through.
     *
     * See [CaptureQuality.MOTION_MEDIAN_JUMP_Y_DEFAULT] for the
     * eyeball-tuned baseline and the rationale.
     */
    val motionMedianJumpY: Double,
    /**
     * Coefficient-of-variation cap on detected peak amplitudes the
     * offline processor accepts before rejecting as
     * MOTION_ARTIFACT. Same scale as
     * [PpgSignalProcessor.MAX_PEAK_AMPLITUDE_COV_DEFAULT] (0.80 is
     * the documented baseline tolerating clean fingertip PPG's
     * respiratory + vasomotor modulation).
     */
    val maxPeakAmplitudeCov: Double,
    /**
     * Live classifier's variance floor for FINGER_OFF. Below this the
     * windowed Y/R signal has too little modulation to plausibly contain
     * a heart-rate component. On devices with a low absolute PPG amplitude
     * (Pixel 9a: AC ≈ 1 R unit on a DC ≈ 80 mean = 1.25 %) the global
     * default of [CaptureQuality.FINGER_OFF_MIN_VARIANCE_DEFAULT] sits
     * inside the legitimate PPG variance range and makes the chip flicker
     * red mid-capture.
     */
    val fingerOffMinVariance: Double = CaptureQuality.FINGER_OFF_MIN_VARIANCE_DEFAULT,
    /**
     * Coefficient-of-variation cap on successive RR intervals before the
     * offline processor rejects as IRREGULAR_RHYTHM. On low-SNR devices
     * the adaptive-threshold peak detector picks up noise/dichrotic
     * inflections during quiet periods, jittering RR intervals beyond
     * the [PpgSignalProcessor.MAX_RR_COV_DEFAULT] of 0.30 even on
     * healthy regular rhythms.
     */
    val maxRrCov: Double = PpgSignalProcessor.MAX_RR_COV_DEFAULT,
)

/**
 * Per-device registry of [PpgDeviceProfile]s.
 *
 * **PROFILES is intentionally empty at Cut 2 landing.** The issue
 * (#266) spec is explicit: thresholds belong in this table only
 * once derived from real distribution data (via the [PpgCalibrationLogger]
 * the Cut 1 owner-debug toggle records). Adding a Pixel 9a /
 * Pixel 6 / Samsung S24 / etc. entry is a one-line edit once at
 * least 5–10 captures of accepted-quality data have been analyzed
 * for that model.
 *
 * `forDevice(model)` is null-safe and case-insensitive on
 * `Build.MODEL` so device-marketing-name capitalization variations
 * resolve to the same profile.
 */
object PpgDeviceProfiles {

    /** Eyeball-tuned defaults — preserved here as the fallback so
     *  the Cut 2 refactor is behaviour-preserving for the device
     *  set that currently works. */
    val DEFAULT: PpgDeviceProfile = PpgDeviceProfile(
        motionMedianJumpY = CaptureQuality.MOTION_MEDIAN_JUMP_Y_DEFAULT,
        maxPeakAmplitudeCov = PpgSignalProcessor.MAX_PEAK_AMPLITUDE_COV_DEFAULT,
        fingerOffMinVariance = CaptureQuality.FINGER_OFF_MIN_VARIANCE_DEFAULT,
        maxRrCov = PpgSignalProcessor.MAX_RR_COV_DEFAULT,
    )

    /**
     * Per-device overrides. Populated as the calibration-log distribution
     * data accumulates. See scripts/analyze-ppg-calibration-log.md (TBD)
     * for the threshold-derivation procedure once enough captures are in.
     */
    internal val PROFILES: Map<String, PpgDeviceProfile> = mapOf(
        // Pixel 9a: torch+main-lens geometry gives a high-DC, low-AC PPG
        // signal. Even with AE+AWB locked, NR/edge-enhance disabled,
        // R-channel averaging, and a Butterworth bandpass [0.7, 3.5] Hz,
        // observed peakAmpCov clusters around 1.6–2.9 on otherwise-clean
        // captures (vs the 0.80 default — the legitimate respiratory +
        // vasomotor amplitude modulation just looks larger than on the
        // reference hardware). Windowed variance sits below the 0.5 floor
        // ~80 % of the time on a calm capture, and the noisy peak picks
        // push rrCov over the 0.30 IRREGULAR_RHYTHM cap on regular rhythms.
        // The captures rejected by the original 0.80 cap give peak rates
        // of 60–63 bpm that match a wrist wearable to within ~2 bpm;
        // captures with real motion stand out at peakAmpCov ≥ 5. Cap of
        // 3.5 admits the legitimate band and still rejects motion.
        "Pixel 9a" to PpgDeviceProfile(
            motionMedianJumpY = 12.0,
            maxPeakAmplitudeCov = 3.5,
            fingerOffMinVariance = 0.05,
            maxRrCov = 0.55,
        ),
    )

    /**
     * Resolve the [PpgDeviceProfile] for the supplied device model
     * string (typically `Build.MODEL`). Null or unknown → [DEFAULT].
     * Case-insensitive lookup so "pixel 9a" and "Pixel 9a" resolve
     * identically.
     */
    fun forDevice(model: String?): PpgDeviceProfile {
        if (model == null) return DEFAULT
        return PROFILES[model]
            ?: PROFILES[model.lowercase()]
            ?: PROFILES.entries.firstOrNull { it.key.equals(model, ignoreCase = true) }?.value
            ?: DEFAULT
    }
}
