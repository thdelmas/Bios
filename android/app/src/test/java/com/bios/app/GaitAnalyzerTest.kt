package com.bios.app

import com.bios.app.engine.GaitAnalyzer
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-math tests for [GaitAnalyzer] (#208, GERIATRICS_PALLIATIVE_POV §2.3).
 *
 * Stride-time CoV is the canonical Hausdorff 2007 gait-variability
 * scalar — coefficient of variation of inter-step intervals, reported as
 * a percent. The tests verify:
 *  - Constant-cadence walk → CoV ≈ 0.
 *  - Highly irregular cadence → CoV well above the clinical 3 % cohort
 *    threshold the Hausdorff / Verghese literature uses.
 *  - Out-of-band intervals (double-tap noise, between-bout pauses) are
 *    rejected.
 *  - Insufficient sample → null (no false-confidence reading).
 *  - The MetricReading writer tags the canonical key.
 */
class GaitAnalyzerTest {

    @Test
    fun constant_cadence_walk_has_near_zero_cov() {
        // Perfectly regular 1 Hz cadence — 1000 ms between steps.
        val timestamps = (0 until 40).map { it * 1000L }
        val cov = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        assertNotNull(cov)
        assertTrue("Constant cadence should be near-zero CoV, got $cov", cov!! < 0.001)
    }

    @Test
    fun mildly_variable_cadence_produces_modest_cov() {
        // Alternating 950 / 1050 ms intervals — mean 1000, stdDev 50,
        // CoV = 5 %. Above the literature cohort cutoff (~3 %).
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (i in 0 until 40) {
            t += if (i % 2 == 0) 950L else 1050L
            timestamps.add(t)
        }
        val cov = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        assertNotNull(cov)
        assertTrue("Alternating cadence should yield CoV ~5%, got $cov", abs(cov!! - 5.0) < 0.1)
    }

    @Test
    fun highly_variable_cadence_produces_large_cov() {
        // Random-ish intervals in the 500–1500 ms band. Variability >>
        // the Hausdorff cohort cutoff.
        val intervals = listOf(
            500L, 1500L, 700L, 1300L, 900L, 1100L, 600L, 1400L, 800L, 1200L,
            500L, 1500L, 700L, 1300L, 900L, 1100L, 600L, 1400L, 800L, 1200L,
            500L, 1500L, 700L, 1300L, 900L, 1100L, 600L, 1400L, 800L, 1200L,
        )
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (dt in intervals) {
            t += dt
            timestamps.add(t)
        }
        val cov = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        assertNotNull(cov)
        assertTrue("Highly variable cadence should produce CoV well above the 3 % cohort cutoff, got $cov", cov!! > 20.0)
    }

    @Test
    fun out_of_band_intervals_are_filtered_out() {
        // Embed a 100 ms double-tap (below MIN_STRIDE_MS) and a 5 s
        // between-bout pause (above MAX_STRIDE_MS) in an otherwise
        // regular 1 s cadence. Filters should drop both.
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (i in 0 until 30) {
            t += 1000L
            timestamps.add(t)
        }
        // Insert a 100 ms double-tap.
        timestamps.add(timestamps.last() + 100L)
        // Insert a 5 s pause then resume cadence.
        t = timestamps.last() + 5_000L
        timestamps.add(t)
        for (i in 0 until 20) {
            t += 1000L
            timestamps.add(t)
        }
        val cov = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        assertNotNull(cov)
        // After filtering, the surviving intervals are all 1000 ms — CoV ≈ 0.
        assertTrue("Out-of-band intervals should be filtered, CoV near 0, got $cov", cov!! < 0.01)
    }

    @Test
    fun insufficient_sample_returns_null() {
        // 10 steps → 9 intervals, below MIN_INTERVALS = 20.
        val timestamps = (0 until 10).map { it * 1000L }
        val cov = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        assertNull("Below MIN_INTERVALS the analyzer must return null", cov)
    }

    @Test
    fun empty_input_returns_null() {
        assertNull(GaitAnalyzer.strideTimeCoVPercent(emptyList()))
    }

    @Test
    fun to_reading_wraps_with_canonical_key_and_tier() {
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (i in 0 until 40) {
            t += if (i % 2 == 0) 950L else 1050L
            timestamps.add(t)
        }
        val reading = GaitAnalyzer.toReading(timestamps, sourceId = "phone_sensor")
        assertNotNull(reading)
        assertEquals(MetricType.GAIT_VARIABILITY_COV.key, reading!!.metricType)
        assertTrue("Reading value should be positive CoV", reading.value > 0.0)
    }

    @Test
    fun to_reading_returns_null_for_insufficient_data() {
        val reading = GaitAnalyzer.toReading(listOf(0L, 1000L, 2000L), sourceId = "phone_sensor")
        assertNull(reading)
    }

    @Test
    fun speed_corrected_cov_normalises_against_reference_cadence() {
        // Same alternating 950/1050 cadence — mean 1000 ms = reference,
        // so corrected ≈ raw.
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (i in 0 until 40) {
            t += if (i % 2 == 0) 950L else 1050L
            timestamps.add(t)
        }
        val raw = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        val corrected = GaitAnalyzer.gaitVariabilitySpeedCorrected(timestamps)
        assertNotNull(raw)
        assertNotNull(corrected)
        assertTrue(
            "At reference cadence, corrected and raw should agree, got raw=$raw corrected=$corrected",
            abs(raw!! - corrected!!) < 0.01,
        )
    }

    @Test
    fun speed_corrected_cov_deflates_slow_walking() {
        // Slow cadence at the upper end of the in-band window: 1800/1900 ms
        // alternation (mean 1850, both inside MIN..MAX). Corrected should
        // be smaller than raw because speedFactor = 1.85 > 1.
        val timestamps = mutableListOf<Long>(0)
        var t = 0L
        for (i in 0 until 40) {
            t += if (i % 2 == 0) 1800L else 1900L
            timestamps.add(t)
        }
        val raw = GaitAnalyzer.strideTimeCoVPercent(timestamps)
        val corrected = GaitAnalyzer.gaitVariabilitySpeedCorrected(timestamps)
        assertNotNull(raw)
        assertNotNull(corrected)
        assertTrue(
            "Slow-walking corrected CoV should be smaller than raw, got raw=$raw corrected=$corrected",
            corrected!! < raw!!,
        )
    }

    @Test
    fun postural_sway_amplitude_returns_null_for_short_input() {
        assertNull(GaitAnalyzer.posturalSwayAmplitudeMm(listOf(0.0, 0.0, 0.0), 50.0))
    }

    @Test
    fun postural_sway_amplitude_returns_positive_for_oscillating_input() {
        // 100 samples at 50 Hz, sinusoidal accel — non-zero amplitude.
        val n = 200
        val rateHz = 50.0
        val accels = (0 until n).map { i ->
            val tSec = i / rateHz
            0.5 * kotlin.math.sin(2 * kotlin.math.PI * 0.5 * tSec) // 0.5 Hz oscillation
        }
        val amp = GaitAnalyzer.posturalSwayAmplitudeMm(accels, rateHz)
        assertNotNull(amp)
        assertTrue("Oscillating accel should yield positive postural-sway amplitude, got $amp", amp!! > 0.0)
    }
}
