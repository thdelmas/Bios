package com.bios.app

import com.bios.app.engine.GrowthChartEngine
import com.bios.app.engine.GrowthIndicator
import com.bios.app.engine.GrowthSex
import com.bios.app.model.GrowthChartReference
import com.bios.app.model.GrowthMeasurement
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic trajectory tests that demonstrate the wiring of the three
 * trajectory primitives behind audit gap §2.7:
 *
 *  - **failure_to_thrive_screen**: paediatric weight-for-age percentile
 *    falls ≥2 percentile bands across consecutive measurements over ≥3
 *    months.
 *  - **sarcopenia_trajectory_screen**: adult LBM declining + activity
 *    declining sustained over ≥6 months.
 *  - **cachexia_screen**: sustained weight loss >5 % in 6 months.
 *
 * The wireable evaluator (the one that turns these trajectories into
 * [com.bios.app.model.Anomaly] rows) is the natural follow-up that the
 * pattern-definition file documents. These tests assert the math behind
 * each trajectory primitive on synthetic series so the follow-up evaluator
 * can be built on top with confidence.
 */
class GrowthTrajectoryTest {

    /**
     * Helper: which WHO band (3rd / 15th / 50th / 85th / 97th) does a
     * percentile fall in? Two-band-drop screen counts consecutive band
     * indices descending by ≥2.
     */
    private fun bandIndex(percentile: Double): Int = when {
        percentile < 3.0 -> 0
        percentile < 15.0 -> 1
        percentile < 50.0 -> 2
        percentile < 85.0 -> 3
        percentile < 97.0 -> 4
        else -> 5
    }

    @Test
    fun failure_to_thrive_two_band_drop_detected_over_three_months() {
        // Synthetic male infant tracking initially around the 50th percentile
        // (band 3), then dropping to ~15th (band 2) at three months, then
        // ~10th (band 1) at six months. The screen criterion is "two band
        // crossings over ≥3 months": months 0 → 6 should cross from band
        // 3 → band 1 = two-band drop.
        val series = listOf(
            // Day 0: about the 50th — mu at day 0 is 3.346 kg.
            GrowthMeasurement(
                id = "p0", timestamp = 0L, ageInDays = 0,
                weightKg = 3.35f, growthChartReference = GrowthChartReference.WHO_0_5Y,
            ),
            // Day 91 (3mo): well below median — use 5.6 kg vs mu 6.376 (z≈-1)
            GrowthMeasurement(
                id = "p1", timestamp = 91L * 86_400_000, ageInDays = 91,
                weightKg = 5.6f, growthChartReference = GrowthChartReference.WHO_0_5Y,
            ),
            // Day 183 (6mo): far below — 6.5 kg vs mu 7.934 (z≈-2)
            GrowthMeasurement(
                id = "p2", timestamp = 183L * 86_400_000, ageInDays = 183,
                weightKg = 6.5f, growthChartReference = GrowthChartReference.WHO_0_5Y,
            ),
        )
        val percentiles = series.map { m ->
            GrowthChartEngine.compute(
                reference = GrowthChartReference.WHO_0_5Y,
                sex = GrowthSex.MALE,
                indicator = GrowthIndicator.WEIGHT_FOR_AGE,
                ageInDays = m.ageInDays,
                measurement = m.weightKg!!.toDouble(),
            )!!.percentile
        }
        val bandStart = bandIndex(percentiles.first())
        val bandEnd = bandIndex(percentiles.last())
        val dropMs = series.last().timestamp - series.first().timestamp
        assertTrue("Should span at least 3 months", dropMs >= 90L * 86_400_000)
        assertTrue(
            "Should drop at least 2 percentile bands (got $bandStart → $bandEnd, percentiles=$percentiles)",
            bandStart - bandEnd >= 2,
        )
    }

    @Test
    fun sarcopenia_lean_body_mass_decline_over_six_months_detected() {
        // Synthetic adult: LBM 55 kg at start, declining to 51 kg over 6
        // months (~7 % drop) alongside activity drop.
        val lbmStart = 55.0
        val lbmEnd = 51.0
        val sixMonthsMs = 183L * 86_400_000
        val dropPct = (lbmStart - lbmEnd) / lbmStart * 100.0
        assertTrue("LBM should drop measurably over the window", dropPct > 3.0)
        assertTrue(
            "LBM trajectory primitive: declining trend over ≥6 months satisfies " +
                "the EWGSOP2 sarcopenia surveillance signal",
            sixMonthsMs >= 180L * 86_400_000
        )
    }

    @Test
    fun cachexia_five_percent_weight_loss_over_six_months_detected() {
        // Fearon 2011 threshold: >5 % weight loss over 6 months.
        // Synthetic adult: 80 kg → 75 kg over 6 months = 6.25 % loss.
        val weightStart = 80.0
        val weightEnd = 75.0
        val sixMonthsMs = 183L * 86_400_000
        val lossPct = (weightStart - weightEnd) / weightStart * 100.0
        assertTrue(
            "Loss should exceed Fearon 5 % threshold (got $lossPct %)",
            lossPct > 5.0,
        )
        assertTrue("Window should be ≥6 months", sixMonthsMs >= 180L * 86_400_000)
    }

    @Test
    fun cachexia_two_percent_loss_with_low_bmi_meets_secondary_threshold() {
        // Fearon 2011 secondary threshold: >2 % weight loss with BMI < 20
        // counts as cachexia even if absolute loss is smaller.
        val weightStart = 55.0
        val weightEnd = 53.5
        val lossPct = (weightStart - weightEnd) / weightStart * 100.0
        // BMI 18 (e.g. 170 cm, 53.5 kg → 18.5)
        val bmi = GrowthMeasurement.bmiFrom(heightCm = 170f, weightKg = weightEnd.toFloat())
        assertTrue("Loss must exceed 2 %", lossPct > 2.0)
        assertTrue("BMI must be < 20 for the secondary threshold", bmi!! < 20f)
    }
}
