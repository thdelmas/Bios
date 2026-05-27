package com.bios.app

import com.bios.app.engine.BaselineEngine
import com.bios.app.model.BaselineContext
import com.bios.contracts.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.model.TrendDirection
import com.bios.app.ui.trends.noBaselineMessage
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for logic used by TrendsScreen: baseline selection, formatting, and trend display.
 */
class TrendsScreenTest {

    private fun baseline(
        metricType: String = MetricType.HEART_RATE.key,
        mean: Double = 70.0,
        stdDev: Double = 5.0,
        p5: Double = 62.0,
        p95: Double = 78.0,
        trend: String = TrendDirection.STABLE.name,
        trendSlope: Double = 0.0,
        windowDays: Int = 14
    ) = PersonalBaseline(
        metricType = metricType,
        mean = mean,
        stdDev = stdDev,
        p5 = p5,
        p95 = p95,
        trend = trend,
        trendSlope = trendSlope,
        windowDays = windowDays
    )

    // -- Metric selection logic --

    @Test
    fun `find baseline for selected metric type`() {
        val baselines = listOf(
            baseline(metricType = MetricType.HEART_RATE.key),
            baseline(metricType = MetricType.BLOOD_OXYGEN.key, mean = 97.0)
        )
        val selected = baselines.find { it.metricType == MetricType.BLOOD_OXYGEN.key }
        assertNotNull(selected)
        assertEquals(97.0, selected!!.mean, 0.01)
    }

    @Test
    fun `find baseline returns null when metric not present`() {
        val baselines = listOf(
            baseline(metricType = MetricType.HEART_RATE.key)
        )
        val selected = baselines.find { it.metricType == MetricType.STEPS.key }
        assertNull(selected)
    }

    // -- Stat formatting (mirrors TrendsScreen.formatStat) --

    private fun formatStat(value: Double): String {
        return when {
            value >= 1000 -> String.format("%.0f", value)
            value >= 100 -> String.format("%.0f", value)
            else -> String.format("%.1f", value)
        }
    }

    @Test
    fun `formatStat shows one decimal for small values`() {
        assertEquals("70.0", formatStat(70.0))
        assertEquals("5.3", formatStat(5.3))
    }

    @Test
    fun `formatStat drops decimals for hundreds`() {
        assertEquals("120", formatStat(120.0))
    }

    @Test
    fun `formatStat drops decimals for thousands`() {
        assertEquals("8500", formatStat(8500.0))
    }

    // -- TrendDirection parsing --

    @Test
    fun `TrendDirection valueOf works for all baseline trends`() {
        for (direction in TrendDirection.entries) {
            val baseline = baseline(trend = direction.name)
            assertEquals(direction, TrendDirection.valueOf(baseline.trend))
        }
    }

    // -- MetricType readable names --

    @Test
    fun `MetricType fromKey resolves known baseline metric types`() {
        val baseline = baseline(metricType = MetricType.HEART_RATE.key)
        val resolved = MetricType.fromKey(baseline.metricType)
        assertNotNull(resolved)
        assertEquals("Heart rate", resolved!!.readableName)
    }

    @Test
    fun `MetricType fromKey returns null for unknown key`() {
        assertNull(MetricType.fromKey("unknown_metric"))
    }

    // -- TrendsScreen header surfaces any metric the search bar can navigate to (#311) --
    // The original chip-row design hardcoded a small trackableMetrics set; metrics opened
    // via HomeScreen.MetricSearchBar that were not in that set landed on a dashboard with
    // an unselected chip row. The clickable header that replaced the chip row reads
    // `selectedMetric.readableName` directly, so the bug is impossible by construction
    // *as long as* every MetricType yields a non-empty readableName. These tests pin that.

    @Test
    fun `every MetricType yields a non-empty readableName so any navigation target renders`() {
        for (m in MetricType.entries) {
            assertTrue(
                "${m.key} produced a blank readableName — TrendsScreen header would be empty",
                m.readableName.isNotBlank(),
            )
        }
    }

    @Test
    fun `metrics cited in #311 surface human-readable header text`() {
        // The issue called out Weight / BMI / Body fat as failure-mode examples.
        // Each must render a non-key human string in the dashboard header.
        assertEquals("Body mass", MetricType.BODY_MASS.readableName)
        assertEquals("Bmi kg per m2", MetricType.BMI_KG_PER_M2.readableName)
        assertEquals("Body fat pct", MetricType.BODY_FAT_PCT.readableName)
    }

    // -- Baseline list display --

    @Test
    fun `baselines list preserves insertion order`() {
        val baselines = listOf(
            baseline(metricType = MetricType.HEART_RATE.key),
            baseline(metricType = MetricType.BLOOD_OXYGEN.key),
            baseline(metricType = MetricType.STEPS.key)
        )
        assertEquals(3, baselines.size)
        assertEquals(MetricType.HEART_RATE.key, baselines[0].metricType)
        assertEquals(MetricType.STEPS.key, baselines[2].metricType)
    }

    @Test
    fun `empty baselines list is handled`() {
        val baselines = emptyList<PersonalBaseline>()
        assertTrue(baselines.isEmpty())
        assertNull(baselines.find { it.metricType == MetricType.HEART_RATE.key })
    }

    @Test
    fun `trend slope formatting shows sign`() {
        val rising = baseline(trendSlope = 0.45)
        val falling = baseline(trendSlope = -0.32)
        assertTrue(String.format("%+.2f", rising.trendSlope).startsWith("+"))
        assertTrue(String.format("%+.2f", falling.trendSlope).startsWith("-"))
    }

    // -- noBaselineMessage --
    // The previous "Bios needs at least 7 days of data" line was misleading
    // when the real problem was that the watch never wrote anything to Health
    // Connect. These tests pin the message branches that replaced it.

    @Test
    fun `noBaselineMessage flags zero data with a setup hint`() {
        val (title, body) = noBaselineMessage("Heart rate", null)
        assertTrue(title.contains("No Heart rate data"))
        assertTrue(body.contains("Health Connect"))
    }

    @Test
    fun `noBaselineMessage flags zero data when coverage is empty`() {
        val cov = BaselineEngine.Coverage(
            sensorSamplesInWindow = 0,
            totalSamples = 0,
            lastTimestamp = null
        )
        val (title, body) = noBaselineMessage("Heart rate", cov)
        assertTrue(title.contains("No Heart rate data"))
        assertTrue(body.contains("hasn't received"))
    }

    @Test
    fun `noBaselineMessage flags stale feed when last reading is days old`() {
        val twoDaysAgo = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        val cov = BaselineEngine.Coverage(
            sensorSamplesInWindow = 5,
            totalSamples = 100,
            lastTimestamp = twoDaysAgo
        )
        val (title, body) = noBaselineMessage("Heart rate", cov)
        assertTrue(title.contains("stale"))
        assertTrue(body.contains("100"))
    }

    @Test
    fun `noBaselineMessage reports sample shortfall when below baseline minimum`() {
        val recent = System.currentTimeMillis() - 3600_000L
        val cov = BaselineEngine.Coverage(
            sensorSamplesInWindow = 4,
            totalSamples = 4,
            lastTimestamp = recent
        )
        val (title, body) = noBaselineMessage("Heart rate", cov)
        assertTrue(title.contains("Building"))
        assertTrue(body.contains("4 sensor reading"))
        assertTrue(body.contains(BaselineEngine.MIN_SAMPLES_FOR_BASELINE.toString()))
    }

    @Test
    fun `noBaselineMessage falls back to baseline pending when data is sufficient`() {
        val recent = System.currentTimeMillis() - 3600_000L
        val cov = BaselineEngine.Coverage(
            sensorSamplesInWindow = 50,
            totalSamples = 50,
            lastTimestamp = recent
        )
        val (title, _) = noBaselineMessage("Heart rate", cov)
        assertTrue(title.contains("pending"))
    }
}
