package com.bios.app

import com.bios.app.ui.trends.MetricChartTimeWindow
import com.bios.app.ui.trends.downsampleForChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM contract checks for the per-metric chart time-window.
 * Covers (a) the start-millis math the dashboard uses to query the
 * DAO, and (b) the downsampler that keeps Canvas rendering tractable
 * for "Year" / "All" windows on auto-sampled metrics.
 */
class MetricChartTimeWindowTest {

    private val now: Long = 1_700_000_000_000L
    private val day: Long = 24L * 60L * 60L * 1000L

    @Test
    fun day_window_subtracts_24_hours() {
        assertEquals(now - day, MetricChartTimeWindow.DAY.startMillis(now))
    }

    @Test
    fun week_window_subtracts_seven_days() {
        assertEquals(now - 7 * day, MetricChartTimeWindow.WEEK.startMillis(now))
    }

    @Test
    fun year_window_subtracts_365_days() {
        assertEquals(now - 365 * day, MetricChartTimeWindow.YEAR.startMillis(now))
    }

    @Test
    fun all_window_starts_at_epoch() {
        // 0L is inclusive on the DAO range filter, so "All" covers every
        // reading on file without needing a separate query path.
        assertEquals(0L, MetricChartTimeWindow.ALL.startMillis(now))
    }

    @Test
    fun downsample_is_a_no_op_below_the_ceiling() {
        val input = (1..100).toList()
        assertEquals(input, downsampleForChart(input))
    }

    @Test
    fun downsample_caps_at_max_chart_points() {
        val input = (1..5000).toList()
        val out = downsampleForChart(input)
        assertTrue("expected <= MAX_CHART_POINTS, got ${out.size}",
            out.size <= MetricChartTimeWindow.MAX_CHART_POINTS)
    }

    @Test
    fun downsample_preserves_first_and_last() {
        // The chart's x-axis spans first..last sample; losing either
        // endpoint would visually shrink the range.
        val input = (1..5000).toList()
        val out = downsampleForChart(input)
        assertEquals(1, out.first())
        assertEquals(5000, out.last())
    }
}
