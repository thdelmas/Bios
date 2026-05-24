package com.bios.app

import com.bios.app.engine.ColeKripkeClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage of [ColeKripkeClassifier] (#244 Cut 1).
 *
 * Pins the UCSD/PIM 7-tap FIR algorithm: weight set, scaling factor,
 * wake threshold, activity-count clipping, and edge-padding behavior
 * at array boundaries. These constants are the published Cole-Kripke
 * (1992) baseline — drift here means a different classifier than the
 * actigraphy literature documents.
 */
class ColeKripkeClassifierTest {

    @Test
    fun weight_set_matches_the_published_UCSD_PIM_coefficients() {
        // Cole RJ et al. (1992) Sleep 15(5):461. Pinning the weights so
        // a refactor can't silently swap them for some other variant
        // (UCSD vs PIM vs Webster originals are all subtly different).
        assertArrayEqualsInts(intArrayOf(106, 54, 58, 76, 230, 74, 67), ColeKripkeClassifier.WEIGHTS_UCSD)
        assertEquals(0.001, ColeKripkeClassifier.SCALE, 1e-9)
        assertEquals(1.0, ColeKripkeClassifier.WAKE_THRESHOLD, 1e-9)
        assertEquals(300f, ColeKripkeClassifier.ACTIVITY_CLIP, 1e-3f)
    }

    @Test
    fun zero_activity_window_scores_below_threshold_and_is_sleep() {
        val window = FloatArray(7) { 0f }
        assertEquals(0.0, ColeKripkeClassifier.score(window), 1e-9)
        assertFalse(ColeKripkeClassifier.isAwake(window))
    }

    @Test
    fun saturated_activity_window_scores_well_above_threshold_and_is_wake() {
        // Every tap saturated at the clip → SI = 0.001 * 300 * sum(weights) = 0.001 * 300 * 665 = 199.5
        val window = FloatArray(7) { 300f }
        val score = ColeKripkeClassifier.score(window)
        assertEquals(199.5, score, 1e-3)
        assertTrue(ColeKripkeClassifier.isAwake(window))
    }

    @Test
    fun isolated_center_spike_score_matches_the_published_formula() {
        // Only the center tap has activity; the score is exactly the
        // center weight * scale * activity. Pins the formula end-to-end.
        val window = floatArrayOf(0f, 0f, 0f, 0f, 10f, 0f, 0f)
        // SI = 0.001 * 230 * 10 = 2.3
        assertEquals(2.3, ColeKripkeClassifier.score(window), 1e-9)
        assertTrue(ColeKripkeClassifier.isAwake(window))
    }

    @Test
    fun isAwake_is_exclusive_at_the_threshold_boundary() {
        // Below threshold → sleep; at-or-above → wake. The published
        // formulation is SI >= 1.0 wake. Float precision means we
        // can't directly construct a window that scores exactly 1.0,
        // so the test pins values clearly above and clearly below.
        val above = floatArrayOf(0f, 0f, 0f, 0f, 5f, 0f, 0f)
        // SI = 0.001 * 230 * 5 = 1.15 → wake
        assertTrue("SI=1.15 must be wake", ColeKripkeClassifier.isAwake(above))

        val justBelow = floatArrayOf(0f, 0f, 0f, 0f, 4f, 0f, 0f)
        // SI = 0.001 * 230 * 4 = 0.92 → sleep
        assertFalse("SI=0.92 must be sleep", ColeKripkeClassifier.isAwake(justBelow))
    }

    @Test
    fun window_of_wrong_length_throws() {
        val tooShort = FloatArray(5) { 1f }
        val tooLong = FloatArray(9) { 1f }
        runCatching { ColeKripkeClassifier.score(tooShort) }
            .also { assertTrue(it.isFailure) }
        runCatching { ColeKripkeClassifier.score(tooLong) }
            .also { assertTrue(it.isFailure) }
    }

    @Test
    fun activityCountFor_clips_at_ACTIVITY_CLIP() {
        // 100 * 9 = 900, must clip to 300.
        assertEquals(300f, ColeKripkeClassifier.activityCountFor(100f), 1e-3f)
    }

    @Test
    fun activityCountFor_handles_null_as_zero() {
        // Sensor absent: variance is null. Treat as no movement observed.
        assertEquals(0f, ColeKripkeClassifier.activityCountFor(null), 1e-9f)
    }

    @Test
    fun activityCountFor_scales_low_variance_linearly() {
        // 0.5 (m/s²)² * 9 = 4.5 — just barely below the single-sample
        // wake threshold (which needs ≈4.35 activity for SI = 1.0 with
        // an isolated center spike). Documented in ColeKripkeClassifier
        // as the Cut 1 conversion-factor rationale.
        assertEquals(4.5f, ColeKripkeClassifier.activityCountFor(0.5f), 1e-3f)
    }

    // -- centered window edge handling --

    @Test
    fun centeredWindow_at_array_middle_reads_the_actual_neighbors() {
        // Use a synthetic ramp so each position is distinguishable
        // post-scaling.
        val variances: List<Float?> = (0..10).map { it.toFloat() / 100f }
        // Index 5 → window [1, 2, 3, 4, 5, 6, 7] / 100 → activity =
        // each * 9.
        val window = ColeKripkeClassifier.centeredWindow(variances, index = 5)
        assertEquals(7, window.size)
        assertEquals(9f * 0.01f, window[0], 1e-3f)
        assertEquals(9f * 0.05f, window[4], 1e-3f) // center = index 4
        assertEquals(9f * 0.07f, window[6], 1e-3f)
    }

    @Test
    fun centeredWindow_at_array_start_pads_left_with_the_edge_value() {
        // Index 0 → offsets -4..-1 are out of range; per the helper's
        // documented edge-padding policy each clamps to the boundary
        // value (variances[0]).
        val variances: List<Float?> = listOf(2.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        val window = ColeKripkeClassifier.centeredWindow(variances, index = 0)
        // The four left-of-center taps + the center all read variance=2.0.
        for (k in 0..4) {
            assertEquals("tap $k", 9f * 2f, window[k], 1e-3f)
        }
        // The right-of-center taps read variance=0.0.
        for (k in 5..6) {
            assertEquals("tap $k", 0f, window[k], 1e-3f)
        }
    }

    @Test
    fun centeredWindow_at_array_end_pads_right_with_the_edge_value() {
        val variances: List<Float?> = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 2.0f)
        val window = ColeKripkeClassifier.centeredWindow(variances, index = variances.lastIndex)
        // The center + the two right-of-center taps all read the
        // boundary value 2.0; left-of-center taps read the actual values.
        assertEquals(9f * 2f, window[ColeKripkeClassifier.CENTER_INDEX], 1e-3f)
        assertEquals(9f * 2f, window[5], 1e-3f)
        assertEquals(9f * 2f, window[6], 1e-3f)
    }

    @Test
    fun isolated_high_variance_spike_does_not_fire_wake_in_a_quiet_neighbourhood() {
        // The whole point of the FIR smoothing: a single 1-minute
        // burst no longer single-handedly flips a minute to wake when
        // the surrounding 6 minutes are quiet. (Cole-Kripke needs
        // contextual corroboration.)
        // Activity = 0.5f * 9 = 4.5 at center, zeros elsewhere:
        // SI = 0.001 * 230 * 4.5 = 1.035 → just barely above threshold.
        // But a 0.4f variance spike (below the old 1-tap 0.5 threshold)
        // produces SI = 0.001 * 230 * 0.4 * 9 = 0.828 → sleep.
        val quietWithBriefSpike: List<Float?> =
            listOf(0f, 0f, 0f, 0f, 0.4f, 0f, 0f, 0f, 0f)
        val window = ColeKripkeClassifier.centeredWindow(quietWithBriefSpike, index = 4)
        assertFalse(
            "single brief variance spike below the smoothed wake threshold should stay sleep",
            ColeKripkeClassifier.isAwake(window),
        )
    }

    @Test
    fun sustained_moderate_variance_fires_wake_decisively() {
        // 7 contiguous minutes of variance = 1.0 (m/s²)² → activity = 9
        // each; SI = 0.001 * 9 * sum(weights) = 0.001 * 9 * 665 = 5.985.
        val sustained = FloatArray(7) { ColeKripkeClassifier.activityCountFor(1.0f) }
        val score = ColeKripkeClassifier.score(sustained)
        assertEquals(5.985, score, 1e-3)
        assertTrue(ColeKripkeClassifier.isAwake(sustained))
        // And sanity-check that this is clearly above threshold (>>1)
        // rather than borderline.
        assertNotEquals(score, ColeKripkeClassifier.WAKE_THRESHOLD, 1.0)
    }

    private fun assertArrayEqualsInts(expected: IntArray, actual: IntArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
