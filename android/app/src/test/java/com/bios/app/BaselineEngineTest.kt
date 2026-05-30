package com.bios.app

import com.bios.app.engine.BaselineEngine
import com.bios.app.engine.computeTrend
import com.bios.app.model.TrendDirection
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for BaselineEngine's pure trend-detection math. These call the real
 * [computeTrend] (extracted to a top-level function so it needs no
 * BiosDatabase) directly, so a regression in the production algorithm is
 * caught here instead of hiding behind a test-local copy.
 */
class BaselineEngineTest {

    @Test
    fun `rising trend detected for increasing daily means`() {
        // Steadily increasing values: 60, 62, 64, 66, 68, 70, 72
        val dailyMeans = (0..6).map { 60.0 + it * 2.0 }
        val (direction, slope) = computeTrend(dailyMeans)

        assertEquals(TrendDirection.RISING, direction)
        assertEquals(2.0, slope, 0.01)
    }

    @Test
    fun `falling trend detected for decreasing daily means`() {
        val dailyMeans = (0..6).map { 80.0 - it * 2.0 }
        val (direction, slope) = computeTrend(dailyMeans)

        assertEquals(TrendDirection.FALLING, direction)
        assertEquals(-2.0, slope, 0.01)
    }

    @Test
    fun `stable trend for flat daily means`() {
        val dailyMeans = listOf(70.0, 70.1, 69.9, 70.0, 70.1, 69.9, 70.0)
        val (direction, _) = computeTrend(dailyMeans)

        assertEquals(TrendDirection.STABLE, direction)
    }

    @Test
    fun `fewer than 3 data points returns stable`() {
        val (direction, slope) = computeTrend(listOf(70.0, 72.0))
        assertEquals(TrendDirection.STABLE, direction)
        assertEquals(0.0, slope, 0.01)
    }

    @Test
    fun `empty list returns stable`() {
        val (direction, slope) = computeTrend(emptyList())
        assertEquals(TrendDirection.STABLE, direction)
        assertEquals(0.0, slope, 0.01)
    }

    @Test
    fun `single value returns stable`() {
        val (direction, slope) = computeTrend(listOf(70.0))
        assertEquals(TrendDirection.STABLE, direction)
        assertEquals(0.0, slope, 0.01)
    }

    @Test
    fun `small increase below threshold is stable`() {
        // Normalized slope needs to exceed 0.02 to be RISING
        // Mean ~70, so slope must be > 70*0.02 = 1.4 per day
        val dailyMeans = listOf(70.0, 70.2, 70.4, 70.6, 70.8, 71.0, 71.2)
        val (direction, _) = computeTrend(dailyMeans)

        assertEquals(TrendDirection.STABLE, direction)
    }

    @Test
    fun `minimum data days constant is 7`() {
        assertEquals(7, BaselineEngine.MINIMUM_DATA_DAYS)
    }

    @Test
    fun `default window days constant is 14`() {
        assertEquals(14, BaselineEngine.DEFAULT_WINDOW_DAYS)
    }
}
