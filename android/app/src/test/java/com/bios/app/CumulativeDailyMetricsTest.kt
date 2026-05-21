package com.bios.app

import com.bios.app.ui.components.cumulativeDailyMetrics
import com.bios.contracts.MetricType
import com.bios.contracts.MetricUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the set of metrics that the Home/MetricCard surface treats as
 * cumulative-daily — these get summed across today's readings, not
 * rendered as the latest sample. Surfaced by a real bug report: watch
 * showed 11000 steps, Bios card showed 94 (the most recent ~hourly
 * StepsRecord bucket from Health Connect).
 *
 * If a future metric joins the set (e.g. `cough_count` when the mic
 * adapter ships), update this test alongside the production set so the
 * contract stays explicit.
 */
class CumulativeDailyMetricsTest {

    @Test
    fun set_contains_steps_active_calories_active_minutes() {
        assertEquals(
            setOf(
                MetricType.STEPS,
                MetricType.ACTIVE_CALORIES,
                MetricType.ACTIVE_MINUTES,
            ),
            cumulativeDailyMetrics,
        )
    }

    @Test
    fun every_member_is_a_count_or_duration_unit() {
        // Pin the type-shape: STEPS = COUNT, KCAL for calories, SECONDS for
        // active-minutes-as-duration. Anything else in this set would be a
        // category error (you can't usefully "sum" a HR reading across the
        // day to get a daily total).
        for (m in cumulativeDailyMetrics) {
            assertTrue(
                "${m.key} unit ${m.unit} not in the expected cumulative-shape set",
                m.unit in setOf(MetricUnit.COUNT, MetricUnit.KCAL, MetricUnit.SECONDS)
            )
        }
    }

    @Test
    fun non_cumulative_metrics_stay_out_of_the_set() {
        // Spot-check: HR, HRV, SpO2 are instantaneous readings; SkinTemp,
        // BBT, and biomarkers are point measurements. None should appear
        // in the cumulative set — summing them would produce a number with
        // no clinical meaning.
        val excluded = listOf(
            MetricType.HEART_RATE,
            MetricType.HEART_RATE_VARIABILITY,
            MetricType.BLOOD_OXYGEN,
            MetricType.SKIN_TEMPERATURE_DEVIATION,
            MetricType.SLEEP_DURATION,
            MetricType.HBA1C,
        )
        for (m in excluded) {
            assertFalse(
                "${m.key} should NOT be in cumulativeDailyMetrics",
                m in cumulativeDailyMetrics,
            )
        }
    }
}
