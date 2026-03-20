package com.bios.app

import com.bios.app.engine.Stats
import com.bios.app.model.PersonalBaseline
import org.junit.Assert.*
import org.junit.Test

class StatisticsTest {

    @Test
    fun `compute basic statistics`() {
        val values = listOf(10.0, 20.0, 30.0, 40.0, 50.0)
        val stats = Stats.compute(values)

        assertEquals(30.0, stats.mean, 0.01)
        assertEquals(30.0, stats.median, 0.01)
        assertEquals(10.0, stats.min, 0.01)
        assertEquals(50.0, stats.max, 0.01)
        assertEquals(14.14, stats.stdDev, 0.01)
    }

    @Test
    fun `compute percentiles`() {
        val values = (1..100).map { it.toDouble() }
        val stats = Stats.compute(values)

        assertEquals(5.95, stats.p5, 0.1)
        assertEquals(95.05, stats.p95, 0.1)
    }

    @Test
    fun `handle empty list`() {
        val stats = Stats.compute(emptyList())

        assertEquals(0.0, stats.mean, 0.01)
        assertEquals(0.0, stats.min, 0.01)
        assertEquals(0.0, stats.max, 0.01)
    }

    @Test
    fun `handle single value`() {
        val stats = Stats.compute(listOf(42.0))

        assertEquals(42.0, stats.mean, 0.01)
        assertEquals(42.0, stats.median, 0.01)
        assertEquals(0.0, stats.stdDev, 0.01)
    }

    @Test
    fun `baseline z-score calculation`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate",
            mean = 70.0,
            stdDev = 5.0,
            p5 = 62.0,
            p95 = 78.0
        )

        assertEquals(1.0, baseline.zScore(75.0), 0.01)
        assertEquals(-1.0, baseline.zScore(65.0), 0.01)
        assertEquals(0.0, baseline.zScore(70.0), 0.01)
        assertEquals(2.0, baseline.zScore(80.0), 0.01)
    }

    @Test
    fun `baseline z-score with zero stddev returns zero`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate",
            mean = 70.0,
            stdDev = 0.0,
            p5 = 70.0,
            p95 = 70.0
        )

        assertEquals(0.0, baseline.zScore(75.0), 0.01)
    }
}
