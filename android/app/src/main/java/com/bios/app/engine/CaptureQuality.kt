package com.bios.app.engine

import kotlin.math.abs

/**
 * Real-time placement / motion classifier for the ongoing camera-PPG capture.
 *
 * The full [PpgSignalProcessor] only runs *after* the 60-second recording —
 * by then the owner has already wasted a minute if their finger drifted or
 * the lens picked up room light. This classifier looks at the most recent
 * second of luminance frames and produces an enum the UI surfaces as a
 * coloured indicator + coaching message, so the owner gets immediate
 * feedback while they're still in a position to fix it.
 *
 * Heuristics, ordered most-to-least specific:
 *  1. **WAITING** — not enough frames yet for a stable classification. First
 *     ~1 second of capture, while the auto-exposure settles and the flash
 *     warms up.
 *  2. **FINGER_OFF** — mean luminance is too high (room light reaching the
 *     sensor) or variance is near zero (no PPG modulation because no
 *     contact). Aligns with the offline [PpgSignalProcessor] checks for
 *     INSUFFICIENT_SIGNAL and SATURATION.
 *  3. **MOTION** — large frame-to-frame jumps in luminance over the last
 *     half-second. Heart-beat amplitude is small (~5–20 Y units); motion
 *     and pressure changes are typically >25 units.
 *  4. **STEADY** — the finger is on the lens and the frame is calm. The
 *     only state where the eventual offline analysis is likely to accept
 *     the recording.
 *
 * Thresholds are eyeball-tuned for 8-bit Y values at ~30 fps with the
 * device flash on. They may need calibration as field data accumulates;
 * keeping them as named consts so a future PR can promote them to a
 * device-specific profile.
 */
enum class CaptureQuality(val message: String, val isGood: Boolean) {
    WAITING("Hold the camera. Reading starting…", false),
    FINGER_OFF("Cover the rear camera and flash fully with your fingertip.", false),
    MOTION("Hold still — motion is interfering with the signal.", false),
    STEADY("Signal looks good. Keep holding.", true);

    companion object {
        /** Below this we say WAITING — not enough frames to classify. Set
         *  to ~1.5 seconds at 20 fps so the camera's auto-exposure has time
         *  to settle on the flash + finger combination; otherwise the first
         *  big AE step gets misread as motion. */
        const val MIN_FRAMES_FOR_CLASSIFICATION = 30

        /** Mean Y above this means room light is reaching the sensor. */
        const val FINGER_OFF_BRIGHT_MEAN = 200.0

        /** Variance below this means there's no PPG modulation at all. */
        const val FINGER_OFF_MIN_VARIANCE = 0.5

        /**
         * Motion threshold on the *median* frame-to-frame Y jump over the
         * recent window. Heart-beats produce a few large diffs per second
         * (systolic peaks) on top of mostly small diffs, so a single max
         * value is a poor discriminator — devices with strong PPG amplitude
         * would always classify as MOTION. Median-of-diffs is robust to
         * those spikes: sustained motion lifts the whole distribution.
         */
        const val MOTION_MEDIAN_JUMP_Y = 6.0

        /** Window for the motion check (seconds). */
        const val MOTION_WINDOW_SEC = 1.0

        /**
         * Classify the most recent frames. [window] is mean-Y values oldest
         * first; [samplingRateHz] is the analyzer's current frame rate.
         */
        fun classify(window: List<Double>, samplingRateHz: Double): CaptureQuality {
            if (window.size < MIN_FRAMES_FOR_CLASSIFICATION) return WAITING

            val mean = window.average()
            if (mean > FINGER_OFF_BRIGHT_MEAN) return FINGER_OFF

            val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
            if (variance < FINGER_OFF_MIN_VARIANCE) return FINGER_OFF

            val recentSpan =
                (samplingRateHz * MOTION_WINDOW_SEC).toInt().coerceAtLeast(5)
            val recentDiffs = window
                .takeLast(recentSpan + 1)
                .zipWithNext { a, b -> abs(b - a) }
                .sorted()
            if (recentDiffs.isEmpty()) return STEADY
            val median = recentDiffs[recentDiffs.size / 2]
            if (median > MOTION_MEDIAN_JUMP_Y) return MOTION

            return STEADY
        }

        /**
         * Distribution of the live "median frame-to-frame jump" metric across a
         * full capture, sampled in non-overlapping 1-second windows. Returned
         * percentiles (p50 = typical session-wide steadiness; p90 = the worst
         * sustained second the owner held through) let the calibration logger
         * characterise the distribution that [MOTION_MEDIAN_JUMP_Y] is being
         * thresholded against, on each device. Returns `null` when the
         * recording is too short to produce a single window.
         */
        fun medianJumpPercentiles(
            luminance: List<Double>,
            samplingRateHz: Double,
        ): MedianJumpStats? {
            val windowSize = (samplingRateHz * MOTION_WINDOW_SEC).toInt().coerceAtLeast(5)
            if (luminance.size < windowSize + 1) return null

            val perWindowMedians = mutableListOf<Double>()
            var start = 0
            // Step in non-overlapping windows of [windowSize] samples; each
            // window emits one median |diff|. Loop bound is inclusive — the
            // last sample index used is start + windowSize - 1.
            while (start + windowSize <= luminance.size) {
                val diffs = (start until start + windowSize - 1).map { i ->
                    abs(luminance[i + 1] - luminance[i])
                }.sorted()
                perWindowMedians.add(diffs[diffs.size / 2])
                start += windowSize
            }
            if (perWindowMedians.isEmpty()) return null

            val sorted = perWindowMedians.sorted()
            return MedianJumpStats(
                p50 = percentile(sorted, 0.5),
                p90 = percentile(sorted, 0.9),
            )
        }

        private fun percentile(sortedAscending: List<Double>, q: Double): Double {
            if (sortedAscending.isEmpty()) return 0.0
            val idx = (sortedAscending.size * q).toInt().coerceAtMost(sortedAscending.size - 1)
            return sortedAscending[idx]
        }
    }
}

/**
 * Percentiles of the per-second median frame-to-frame Y jump observed during
 * a full PPG capture. Surfaced to [PpgCalibrationLogger] so device-specific
 * thresholds can be set from real distributions instead of eyeballed.
 */
data class MedianJumpStats(val p50: Double, val p90: Double)
