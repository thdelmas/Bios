package com.bios.app

import com.bios.app.engine.CircadianCalculator
import com.bios.app.engine.CircadianCalculator.DailySleepSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral parity port of W2F's `CircadianCalculatorTest`. The math is
 * universal — sleep-onset timing → phase shift / regularity — and now lives
 * in Bios per the producer-by-capture-surface rule. Keeping the test set
 * shape-identical (per the migration plan in issue #88) lets us verify that
 * the hoist preserves W2F's existing behavior before W2F switches to reading
 * from Bios.
 */
class CircadianCalculatorTest {

    // ---- Circular mean ----

    @Test
    fun `circularMean handles midnight wraparound`() {
        val mean = CircadianCalculator.circularMean(listOf(23.0, 1.0))
        assertTrue("Expected near 0.0 (midnight), got $mean", mean < 1.0 || mean > 23.0)
    }

    @Test
    fun `circularMean of identical values returns that value`() {
        val mean = CircadianCalculator.circularMean(listOf(22.5, 22.5, 22.5))
        assertEquals(22.5, mean, 0.01)
    }

    @Test
    fun `circularMean of afternoon values works normally`() {
        val mean = CircadianCalculator.circularMean(listOf(14.0, 16.0))
        assertEquals(15.0, mean, 0.01)
    }

    // ---- Circular std ----

    @Test
    fun `circularStd of identical values is 0`() {
        val std = CircadianCalculator.circularStd(listOf(23.0, 23.0, 23.0))
        assertEquals(0.0, std, 0.01)
    }

    @Test
    fun `circularStd of spread values is positive`() {
        val std = CircadianCalculator.circularStd(listOf(21.0, 22.0, 23.0, 0.0, 1.0))
        assertTrue("Expected positive std, got $std", std > 0.0)
    }

    // ---- Circular diff ----

    @Test
    fun `circularDiff handles simple cases`() {
        assertEquals(2.0, CircadianCalculator.circularDiff(14.0, 12.0), 0.01)
        assertEquals(-2.0, CircadianCalculator.circularDiff(12.0, 14.0), 0.01)
    }

    @Test
    fun `circularDiff handles midnight wraparound`() {
        assertEquals(2.0, CircadianCalculator.circularDiff(1.0, 23.0), 0.01)
        assertEquals(-2.0, CircadianCalculator.circularDiff(23.0, 1.0), 0.01)
    }

    // ---- Phase shift ----

    @Test
    fun `phase shift is null with insufficient data`() {
        val summaries = listOf(
            summary("2026-04-10", onset = 23.0, hours = 7.0),
            summary("2026-04-11", onset = 23.5, hours = 7.0),
        )
        val result = CircadianCalculator.calculate(summaries, todayOnsetHour = 23.0)
        assertNull("Should be null with < 3 samples", result.phaseShift)
    }

    @Test
    fun `phase shift detects advance when going to bed earlier`() {
        val summaries = (1..7).map { d ->
            summary("2026-04-%02d".format(d), onset = 23.5, hours = 7.0)
        }
        val result = CircadianCalculator.calculate(summaries, todayOnsetHour = 21.0)
        assertNotNull(result.phaseShift)
        assertTrue(
            "Earlier bedtime should report a positive phase shift, got ${result.phaseShift}",
            result.phaseShift!! > 0.0,
        )
    }

    @Test
    fun `phase shift detects delay when going to bed later`() {
        val summaries = (1..7).map { d ->
            summary("2026-04-%02d".format(d), onset = 23.0, hours = 7.0)
        }
        val result = CircadianCalculator.calculate(summaries, todayOnsetHour = 2.0)
        assertNotNull(result.phaseShift)
        assertTrue(
            "Later bedtime should report a negative phase shift, got ${result.phaseShift}",
            result.phaseShift!! < 0.0,
        )
    }

    @Test
    fun `no phase shift when consistent schedule`() {
        val summaries = (1..7).map { d ->
            summary("2026-04-%02d".format(d), onset = 23.0, hours = 7.5)
        }
        val result = CircadianCalculator.calculate(summaries, todayOnsetHour = 23.0)
        assertNotNull(result.phaseShift)
        assertEquals(0.0, result.phaseShift!!, 0.5)
    }

    // ---- Sleep regularity ----

    @Test
    fun `high regularity when consistent bedtime`() {
        val summaries = (1..14).map { d ->
            summary("2026-04-%02d".format(d), onset = 23.0, hours = 7.0)
        }
        val result = CircadianCalculator.calculate(summaries, 23.0)
        assertNotNull(result.sleepRegularity)
        assertEquals(0.0, result.sleepRegularity!!, 0.1)
    }

    @Test
    fun `low regularity when erratic bedtime`() {
        val onsets = listOf(20.0, 2.0, 22.0, 4.0, 21.0, 3.0, 23.0, 1.0, 20.0, 3.0, 22.0, 4.0, 21.0, 2.0)
        val summaries = onsets.mapIndexed { i, onset ->
            summary("2026-04-%02d".format(i + 1), onset = onset, hours = 6.0)
        }
        val result = CircadianCalculator.calculate(summaries, 2.0)
        assertNotNull(result.sleepRegularity)
        assertTrue(
            "Erratic bedtime should produce high circular std, got ${result.sleepRegularity}",
            result.sleepRegularity!! > 1.0,
        )
    }

    private fun summary(date: String, onset: Double?, hours: Double?) =
        DailySleepSummary(date = date, sleepOnsetHour = onset, sleepHours = hours)
}
