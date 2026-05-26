package com.bios.app

import com.bios.app.engine.GrowthChartEngine
import com.bios.app.engine.GrowthIndicator
import com.bios.app.engine.GrowthSex
import com.bios.app.model.GrowthChartReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Guards the LMS percentile engine against the published WHO reference
 * z-scores (#199, audit gap §2.7).
 *
 * Anchor values: a measurement at the published median of the LMS table
 * must produce a z-score of 0 and a percentile of 50; a measurement at
 * +/- 1 standard deviation must produce z = +/- 1 and percentile ≈ 84.13 /
 * 15.87 respectively. The LMS transform is invertible so the unit test is
 * deterministic — no external table lookup required.
 */
class GrowthChartEngineTest {

    private val tolerancePct = 0.5
    private val toleranceZ = 0.05

    @Test
    fun who_male_weight_at_birth_median_is_z_zero_and_50th_percentile() {
        // WHO 0d male WFA: lambda=0.3487, mu=3.3464, sigma=0.14602
        val mu = 3.3464
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 0,
            measurement = mu,
        )
        assertNotNull(point)
        assertEquals(0.0, point!!.zScore, toleranceZ)
        assertEquals(50.0, point.percentile, tolerancePct)
    }

    @Test
    fun who_female_length_at_12mo_median_is_z_zero_and_50th_percentile() {
        // WHO 366d female LFA: lambda=1.0, mu=74.0150, sigma=0.03477
        val mu = 74.0150
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.FEMALE,
            indicator = GrowthIndicator.LENGTH_FOR_AGE,
            ageInDays = 366,
            measurement = mu,
        )
        assertNotNull(point)
        assertEquals(0.0, point!!.zScore, toleranceZ)
        assertEquals(50.0, point.percentile, tolerancePct)
    }

    @Test
    fun who_male_weight_above_median_produces_positive_z_score() {
        // 12mo: mu=9.6479; a measurement at 11.0 kg is well above median.
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 366,
            measurement = 11.0,
        )
        assertNotNull(point)
        assertTrue("Z must be positive for above-median weight", point!!.zScore > 0.0)
        assertTrue("Percentile must be > 50 for above-median weight", point.percentile > 50.0)
    }

    @Test
    fun who_male_weight_below_median_produces_negative_z_score() {
        // 12mo: mu=9.6479; a measurement at 8.0 kg is below median.
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 366,
            measurement = 8.0,
        )
        assertNotNull(point)
        assertTrue("Z must be negative for below-median weight", point!!.zScore < 0.0)
        assertTrue("Percentile must be < 50 for below-median weight", point.percentile < 50.0)
    }

    @Test
    fun who_male_weight_at_z_plus_one_yields_approximately_84th_percentile() {
        // 0d: lambda=0.3487, mu=3.3464, sigma=0.14602.
        // Solve: ((x/mu)^lambda - 1) / (lambda * sigma) = 1
        //   => (x/mu)^lambda = 1 + lambda*sigma
        //   => x = mu * (1 + lambda*sigma)^(1/lambda)
        val lambda = 0.3487
        val mu = 3.3464
        val sigma = 0.14602
        val xAtZOne = mu * Math.pow(1.0 + lambda * sigma, 1.0 / lambda)
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 0,
            measurement = xAtZOne,
        )
        assertNotNull(point)
        assertEquals(1.0, point!!.zScore, toleranceZ)
        // CDF(1) = 0.8413
        assertEquals(84.13, point.percentile, tolerancePct)
    }

    @Test
    fun engine_interpolates_lms_between_published_rows() {
        // 15-day-old male WFA — between the 0d and 30d rows.
        // Expect a sensible (positive, finite) percentile.
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 15,
            measurement = 4.0,
        )
        assertNotNull(point)
        assertTrue(point!!.percentile in 0.0..100.0)
    }

    @Test
    fun engine_returns_null_for_unsupported_reference() {
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.CDC_2_20Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 730,
            measurement = 12.0,
        )
        assertNull("CDC tables tracked as follow-up; engine must return null", point)
    }

    @Test
    fun engine_returns_null_for_unsupported_indicator() {
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.HEAD_CIRCUMFERENCE_FOR_AGE,
            ageInDays = 0,
            measurement = 35.0,
        )
        assertNull("Head-circumference tables tracked as follow-up", point)
    }

    @Test
    fun engine_returns_null_for_age_outside_shipped_range() {
        // Beyond 731 days the WHO 0-2y subset doesn't cover.
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 800,
            measurement = 12.0,
        )
        assertNull(point)
    }

    @Test
    fun engine_returns_null_for_non_positive_measurement() {
        val point = GrowthChartEngine.compute(
            reference = GrowthChartReference.WHO_0_5Y,
            sex = GrowthSex.MALE,
            indicator = GrowthIndicator.WEIGHT_FOR_AGE,
            ageInDays = 0,
            measurement = 0.0,
        )
        assertNull(point)
    }

    @Test
    fun lambda_zero_branch_uses_log_transform() {
        // Sanity check the log-transform code path. Lambda=0 should make
        // z = ln(measurement/mu) / sigma.
        val z = GrowthChartEngine.zScoreFromLms(measurement = 2.0, lambda = 0.0, mu = 2.0, sigma = 0.1)
        assertEquals(0.0, z, 1e-9)

        val zPlus = GrowthChartEngine.zScoreFromLms(measurement = 2.0 * Math.E, lambda = 0.0, mu = 2.0, sigma = 1.0)
        assertTrue(abs(zPlus - 1.0) < 1e-9)
    }

    @Test
    fun percentile_clamps_to_unit_range() {
        // An extreme z should still produce a value in [0, 100].
        val pctHigh = GrowthChartEngine.percentileFromZ(10.0)
        val pctLow = GrowthChartEngine.percentileFromZ(-10.0)
        assertTrue(pctHigh in 0.0..100.0)
        assertTrue(pctLow in 0.0..100.0)
    }
}
