package com.bios.app

import com.bios.app.engine.BaselinedActivityThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM coverage of [BaselinedActivityThreshold]'s compute +
 * pruneOldSamples helpers (#244 Cut 2). The Android-bound storage
 * wrapper (`recordSample` / `currentThreshold`) is a thin
 * SharedPreferences shim — same exclusion documented in
 * `PpgCalibrationLoggerTest`: the unit-test source set has no
 * Android Context.
 */
class BaselinedActivityThresholdTest {

    private val minSamples = BaselinedActivityThreshold.MIN_SAMPLES_FOR_BASELINE

    @Test
    fun compute_returns_null_below_min_sample_count() {
        val tooFew = List(minSamples - 1) { 0.1f }
        assertNull(BaselinedActivityThreshold.compute(tooFew))
    }

    @Test
    fun compute_returns_3x_P25_at_or_above_min_sample_count() {
        // Samples: 1..400 ranks evenly distributed. P25 by rank-index
        // floor(400 * 25 / 100) = 100 sits at the 101st value (1-indexed)
        // = 101. Expected threshold = 3 * 101 = 303.
        val samples = (1..400).map { it.toFloat() }
        val result = BaselinedActivityThreshold.compute(samples)
        assertNotNull(result)
        assertEquals(303f, result!!, 1f)
    }

    @Test
    fun compute_applies_a_sanity_floor_for_degenerate_inputs() {
        // A perfectly still phone (variance ≈ 0 for every sample) would
        // produce a near-zero P25 and an unusably small 3*P25. The
        // sanity floor keeps the threshold above sensor-hub quantization
        // noise so isQuietSample stays meaningful even on lab benches.
        val allZero = List(minSamples) { 0f }
        val result = BaselinedActivityThreshold.compute(allZero)
        assertNotNull(result)
        assertEquals(0.05f, result!!, 1e-6f)
    }

    @Test
    fun compute_uses_the_25th_percentile_not_the_mean() {
        // 90% of samples at variance = 0.05 (quiet), 10% at variance = 5
        // (a few high-motion outliers). The MEAN would be skewed by the
        // outliers (~0.55); the P25 stays at 0.05 → threshold = 0.15.
        val quiet = List((minSamples * 0.9).toInt()) { 0.05f }
        val outliers = List(minSamples - quiet.size) { 5f }
        val samples = quiet + outliers
        val result = BaselinedActivityThreshold.compute(samples)
        assertNotNull(result)
        assertEquals(0.15f, result!!, 1e-3f)
    }

    @Test
    fun compute_is_resilient_to_unsorted_input() {
        // The implementation sorts internally; the caller doesn't need
        // to pre-sort.
        val ascending = (1..400).map { it.toFloat() }
        val shuffled = ascending.shuffled(java.util.Random(42L))
        assertEquals(
            BaselinedActivityThreshold.compute(ascending),
            BaselinedActivityThreshold.compute(shuffled),
        )
    }

    // -- pruneOldSamples --

    @Test
    fun pruneOldSamples_keeps_samples_inside_the_7_day_window() {
        val now = 1_700_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val samples = listOf(0.1f, 0.2f, 0.3f, 0.4f)
        val timestamps = listOf(
            now - 8 * day, // outside — drops
            now - 6 * day, // inside
            now - 3 * day, // inside
            now,           // inside
        )
        val (keptSamples, keptTimestamps) =
            BaselinedActivityThreshold.pruneOldSamples(samples, timestamps, now)
        assertEquals(listOf(0.2f, 0.3f, 0.4f), keptSamples)
        assertEquals(listOf(now - 6 * day, now - 3 * day, now), keptTimestamps)
    }

    @Test
    fun pruneOldSamples_keeps_sample_exactly_at_the_cutoff() {
        val now = 1_700_000_000_000L
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        val cutoff = now - sevenDays
        val (kept, _) = BaselinedActivityThreshold.pruneOldSamples(
            samples = listOf(1.0f, 2.0f),
            timestamps = listOf(cutoff, cutoff - 1),
            nowMs = now,
        )
        // Sample at exactly the cutoff is "≥ cutoff" → kept. The one
        // 1 ms older drops.
        assertEquals(listOf(1.0f), kept)
    }

    @Test
    fun pruneOldSamples_empty_input_yields_empty_output() {
        val (kept, keptTs) = BaselinedActivityThreshold.pruneOldSamples(
            emptyList(), emptyList(), nowMs = 1_700_000_000_000L,
        )
        assertEquals(emptyList<Float>(), kept)
        assertEquals(emptyList<Long>(), keptTs)
    }

    @Test
    fun pruneOldSamples_throws_on_misaligned_lists() {
        runCatching {
            BaselinedActivityThreshold.pruneOldSamples(
                samples = listOf(1f, 2f, 3f),
                timestamps = listOf(100L, 200L),
                nowMs = 300L,
            )
        }.let { assertEquals(true, it.isFailure) }
    }
}
