package com.bios.app

import com.bios.app.engine.EctopyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state tests for [EctopyDetector]. Synthetic RR series cover the
 * canonical PVC + compensatory-pause signature, motion-artefact mimics,
 * and edge cases (too few beats, all-regular rhythm).
 */
class EctopyDetectorTest {

    /** 60 bpm steady-state RR series, no ectopy. */
    private fun regularSeries(count: Int, baseMs: Double = 1000.0): List<Double> =
        List(count) { baseMs }

    /**
     * Insert a PVC + compensatory pause at [pvcIndex]. The short interval
     * is 0.65 × base (well below the 0.80 short-ratio cutoff) and the
     * compensatory pause is 1.30 × base (above the 1.15 long-ratio cutoff).
     */
    private fun withPvcAt(series: List<Double>, pvcIndex: Int, base: Double = 1000.0): List<Double> {
        val out = series.toMutableList()
        out[pvcIndex] = 0.65 * base
        out[pvcIndex + 1] = 1.30 * base
        return out
    }

    @Test
    fun regular_rhythm_yields_zero_burden() {
        val result = EctopyDetector.analyse(regularSeries(60))
        assertNotNull(result)
        assertEquals(0.0, result!!.burdenPercent, 1e-9)
        assertEquals(0, result.candidatePairs)
        assertEquals(60, result.beatsAnalysed)
    }

    @Test
    fun single_pvc_in_60_beat_window_produces_small_nonzero_burden() {
        var series = regularSeries(60)
        series = withPvcAt(series, pvcIndex = 30)
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        assertEquals(1, result!!.candidatePairs)
        // 1 candidate / 60 beats = 1.6667 % — well below the 10 % pattern cutoff
        assertEquals(100.0 / 60.0, result.burdenPercent, 1e-6)
    }

    @Test
    fun pvc_bigeminy_pattern_crosses_the_clinical_threshold() {
        // Bigeminy = every other beat is a PVC. Insert a PVC at every
        // even index past the median anchor (need a critical mass of
        // regular beats first so the median stays at base).
        val base = 1000.0
        val series = (0 until 60).map { i ->
            when {
                i < 20 -> base
                i % 2 == 0 -> 0.65 * base
                else -> 1.30 * base
            }
        }
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        // Expect ~ 19 short-long pairs across the 40-beat bigeminy stretch.
        assertTrue(
            "bigeminy burden should land well above the 10 % clinical cutoff",
            result!!.burdenPercent > 25.0,
        )
        assertTrue(result.candidatePairs >= 15)
    }

    @Test
    fun too_few_intervals_returns_null() {
        // 20 beats < MIN_RR_INTERVALS = 30: median anchor is unreliable.
        val result = EctopyDetector.analyse(regularSeries(20))
        assertNull(result)
    }

    @Test
    fun all_zero_series_returns_null() {
        // Degenerate input — median is 0, signal-to-noise is undefined.
        val result = EctopyDetector.analyse(List(60) { 0.0 })
        assertNull(result)
    }

    @Test
    fun short_only_without_compensatory_pause_does_not_count() {
        // A short beat alone (e.g., respiratory sinus arrhythmia speed-up
        // without compensatory pause) must not register as a PVC. Insert
        // a short interval but follow it with a normal-rate beat.
        var series = regularSeries(60).toMutableList()
        series[30] = 700.0  // short
        series[31] = 1000.0 // normal — no compensatory pause
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        assertEquals(0, result!!.candidatePairs)
    }

    @Test
    fun long_only_without_preceding_short_does_not_count() {
        // A single long beat (e.g., yawn pause) must not register without
        // a preceding short-RR ectopic.
        var series = regularSeries(60).toMutableList()
        series[30] = 1300.0
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        assertEquals(0, result!!.candidatePairs)
    }

    @Test
    fun burden_is_bounded_to_percent_range() {
        // Stress test: contrive a series of mostly short-long pairs. The
        // burden must stay in [0, 100].
        val base = 1000.0
        val series = List(60) { i ->
            if (i % 2 == 0) 0.65 * base else 1.30 * base
        }
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        assertTrue("burden=${result!!.burdenPercent} must be ≤ 100", result.burdenPercent <= 100.0)
        assertTrue("burden=${result.burdenPercent} must be ≥ 0", result.burdenPercent >= 0.0)
    }

    @Test
    fun median_anchor_resists_outlier_inflation() {
        // A single very long beat (say 3 seconds — paroxysmal pause) must
        // not inflate the median so far that subsequent normal beats look
        // "short" by comparison. Median resists this; mean would not.
        var series = regularSeries(60).toMutableList()
        series[30] = 3000.0 // single 3-second pause
        val result = EctopyDetector.analyse(series)
        assertNotNull(result)
        assertEquals(
            "median anchor ignores the single outlier",
            1000.0,
            EctopyDetector.medianOf(series),
            1e-9,
        )
        // No PVC-pair signature emerges (the long beat has no preceding short).
        assertEquals(0, result!!.candidatePairs)
    }
}
