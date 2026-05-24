package com.bios.app.engine

/**
 * Cole-Kripke (1992) 7-tap actigraphy sleep/wake classifier (#244 Cut 1).
 *
 * The published algorithm computes a per-minute "sleep index" (SI)
 * from a centered 7-tap FIR filter over activity counts:
 *
 * ```
 * SI = 0.001 * (
 *     106 * A[-4] + 54 * A[-3] + 58 * A[-2] + 76 * A[-1] +
 *     230 * A[ 0] +
 *     74  * A[+1] + 67 * A[+2]
 * )
 * ```
 *
 * where `A[x]` is the activity count for the minute at offset `x`
 * relative to the center, clipped at [ACTIVITY_CLIP]. `SI < 1.0`
 * scores as sleep; `SI >= 1.0` scores as wake. The UCSD/PIM
 * coefficients above are the published Cole-Kripke (1992) weights
 * — same constants used by `ghammad/pyActigraphy` and
 * `dipetkov/actigraph.sleepr`.
 *
 * Bios's accelerometer-variance input is rescaled into a
 * Cole-Kripke-shaped activity count via [activityCountFor]. The
 * scaling factor [ACTIVITY_COUNT_PER_VARIANCE] is a hardcoded
 * approximation for Cut 1; #244 Cut 2 replaces it with a per-owner
 * baseline learned from the owner's own quiet-hour distribution.
 *
 * Webster's 5 post-FIR rescoring rules (after ≥4 min wake the next
 * 1 min is wake, etc.) are deliberately out of scope for Cut 1 —
 * the bare FIR algorithm is the published baseline; rescoring is
 * accuracy polish that lands in a follow-up cut.
 *
 * ## References
 *  - Cole RJ, Kripke DF, Gruen W, Mullaney DJ, Gillin JC (1992) —
 *    Automatic sleep/wake identification from wrist activity.
 *    *Sleep* 15(5):461–469.
 *  - Webster JB, Kripke DF, Messin S, Mullaney DJ, Wyborney G (1982)
 *    — An activity-based sleep monitor system for ambulatory use.
 *    *Sleep* 5(4):389–399. (Rescoring rules; deferred to Cut 1b.)
 *  - `ghammad/pyActigraphy` (GPL-3) — reference Python port.
 *  - `dipetkov/actigraph.sleepr` (GPL-2) — reference R port.
 */
internal object ColeKripkeClassifier {

    /** UCSD/PIM 7-tap FIR coefficients: A[-4], A[-3], A[-2], A[-1], A[0], A[+1], A[+2]. */
    val WEIGHTS_UCSD: IntArray = intArrayOf(106, 54, 58, 76, 230, 74, 67)

    /** Scaling factor in the SI formula (per Cole-Kripke 1992). */
    const val SCALE: Double = 0.001

    /** Activity counts beyond this saturate. Per the published
     *  algorithm — clipping keeps a single high-amplitude minute
     *  from dominating the FIR output. */
    const val ACTIVITY_CLIP: Float = 300f

    /** Wake threshold on the computed SI. `SI < 1.0` is sleep,
     *  `SI >= 1.0` is wake. */
    const val WAKE_THRESHOLD: Double = 1.0

    /** Centered-window length the FIR convolution consumes. */
    const val WINDOW_LENGTH: Int = 7

    /** Index in a 7-wide centered window where the "current" sample
     *  lives (i.e. offset 0). */
    const val CENTER_INDEX: Int = 4

    /**
     * Cut 1 hardcoded multiplier mapping Bios's accelerometer-magnitude
     * variance ((m/s²)²) to a Cole-Kripke-shaped activity count.
     *
     * Choice rationale: the prior 1-tap classifier marked
     * `variance > 0.5` as AWAKE. At this multiplier, a sustained
     * 7-minute stretch of `variance = 0.5` produces
     * `SI ≈ 0.001 * (sum(WEIGHTS_UCSD)) * (0.5 * 9.0) ≈ 3.0`, i.e.
     * decisively above the wake threshold. A single isolated
     * `variance = 0.5` spike produces `SI ≈ 0.001 * 230 * 4.5 ≈ 1.04`,
     * just above threshold — i.e. the new classifier converges on the
     * old per-sample decision when the spike sits alone, but is
     * smoother in the presence of context.
     *
     * Approximate; Cut 2's [com.bios.app.engine.BaselinedActivityThreshold]
     * (not yet shipped) replaces this with a per-owner-learned value
     * over a 7-day quiet-hour window.
     */
    const val ACTIVITY_COUNT_PER_VARIANCE: Float = 9.0f

    /**
     * Convert one accelerometer-variance sample to the Cole-Kripke
     * activity count, clipped at [ACTIVITY_CLIP]. A null variance
     * (sensor absent) becomes a zero count — the inference treats
     * missing motion as "no movement observed."
     */
    fun activityCountFor(varianceMpsSquared: Float?): Float {
        val v = varianceMpsSquared ?: 0f
        val raw = v * ACTIVITY_COUNT_PER_VARIANCE
        return if (raw < 0f) 0f else if (raw > ACTIVITY_CLIP) ACTIVITY_CLIP else raw
    }

    /**
     * Compute the Cole-Kripke sleep index from a centered 7-wide
     * window of activity counts. Caller is responsible for assembling
     * the window with edge-padding where the array boundary is hit.
     */
    fun score(window: FloatArray): Double {
        require(window.size == WINDOW_LENGTH) {
            "Cole-Kripke window must be exactly $WINDOW_LENGTH samples (got ${window.size})"
        }
        var sum = 0.0
        for (i in 0 until WINDOW_LENGTH) {
            sum += WEIGHTS_UCSD[i] * window[i].toDouble()
        }
        return SCALE * sum
    }

    /** True when the windowed SI is at or above [WAKE_THRESHOLD]. */
    fun isAwake(window: FloatArray): Boolean = score(window) >= WAKE_THRESHOLD

    /**
     * Assemble a 7-wide centered window of activity counts from a
     * sequential variance series. Indices outside `[0, variances.size)`
     * are edge-padded with the boundary value (Cut 1 simplest choice;
     * the canonical Cole-Kripke literature is silent on edge handling
     * and most reference ports do the same).
     */
    fun centeredWindow(variances: List<Float?>, index: Int): FloatArray {
        val window = FloatArray(WINDOW_LENGTH)
        for (k in 0 until WINDOW_LENGTH) {
            val raw = index + (k - CENTER_INDEX)
            val clamped = raw.coerceIn(0, variances.size - 1)
            window[k] = activityCountFor(variances[clamped])
        }
        return window
    }
}
