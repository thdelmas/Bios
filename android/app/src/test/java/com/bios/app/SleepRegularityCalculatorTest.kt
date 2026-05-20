package com.bios.app

import com.bios.app.engine.SleepRegularityCalculator
import com.bios.app.engine.SleepRegularityCalculator.Fields
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the SLEEP_REGULARITY composite + sidecar contract.
 *
 * Algorithm under test is pure circular statistics over a window of
 * SLEEP_DURATION readings — fully JVM-testable. Picks the implementation
 * apart along three axes: well-clustered → high score, dispersed → low,
 * wrap-around (around midnight) handled correctly.
 */
class SleepRegularityCalculatorTest {

    private val sourceId = "test-source"
    private val now = utcMidnight(2026, 5, 21) // Wed midnight UTC for reproducibility
    private val hour = 3_600_000L

    @Test
    fun `fewer than three nights returns null`() {
        val nights = listOf(
            sleepNight(daysAgo = 1, bedHour = 23, durationH = 8),
            sleepNight(daysAgo = 2, bedHour = 23, durationH = 8),
        )
        assertNull(SleepRegularityCalculator.derive(nights, sourceId, nowMs = now))
    }

    @Test
    fun `perfectly-regular schedule scores 100`() {
        // 7 nights, all bedtime 23:00, all 8h sleep. Midpoint = 03:00 every
        // night. Variance ≈ 0 → score = 100.
        val nights = (1..7).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val result = SleepRegularityCalculator.derive(nights, sourceId, nowMs = now)
        assertNotNull(result)
        assertEquals(100.0, result!!.reading.value, 0.5)
        assertEquals(MetricType.SLEEP_REGULARITY.key, result.reading.metricType)
    }

    @Test
    fun `highly-irregular schedule scores zero`() {
        // 7 nights with bedtimes spread across the clock — high midpoint
        // variance, score saturates at 0.
        val bedtimes = listOf(20, 0, 4, 22, 12, 18, 2)
        val nights = bedtimes.mapIndexed { i, hr ->
            sleepNight(daysAgo = i + 1, bedHour = hr, durationH = 8)
        }
        val result = SleepRegularityCalculator.derive(nights, sourceId, nowMs = now)
        assertNotNull(result)
        assertEquals(0.0, result!!.reading.value, 0.5)
    }

    @Test
    fun `bedtime wrap-around across midnight does not inflate variance`() {
        // 7 nights bedtime alternating 23:30 / 00:30 — the actual spread is
        // 1h, not 23h. Circular stats handle this; naive minute-arithmetic
        // would not. Score should stay near 100 (≤ 15 min midpoint sigma).
        val nights = (1..7).map { i ->
            val isLate = i % 2 == 0
            // 23:30 → bed at +23.5h; 00:30 → bed at +0.5h.
            sleepNightHalfHour(daysAgo = i, bedHourHalfSteps = if (isLate) 47 else 1, durationH = 8)
        }
        val result = SleepRegularityCalculator.derive(nights, sourceId, nowMs = now)
        assertNotNull(result)
        val midpointVarMin = payloadLong(result!!, Fields.MIDPOINT_VARIANCE_MIN)
        // 1h bedtime spread → ~30 min circular std. Must be well below 90
        // (the irregular threshold) — the naive computation would yield
        // ~11 hours of std and saturate at the half-day clamp.
        assertTrue("midpoint variance $midpointVarMin min unexpectedly large for ±30min bedtime",
            midpointVarMin < 90)
    }

    @Test
    fun `sidecar payload contains all four field keys`() {
        val nights = (1..7).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val result = SleepRegularityCalculator.derive(nights, sourceId, nowMs = now)!!
        val keys = result.payload.map { it.fieldKey }.toSet()
        assertEquals(
            setOf(
                Fields.BEDTIME_VARIANCE_MIN,
                Fields.WAKE_VARIANCE_MIN,
                Fields.MIDPOINT_VARIANCE_MIN,
                Fields.WINDOW_DAYS,
            ),
            keys
        )
        // Every payload row joins back to the parent reading via FK.
        assertTrue(result.payload.all { it.readingId == result.reading.id })
        // Window days field carries the configured window.
        assertEquals(7L, payloadLong(result, Fields.WINDOW_DAYS))
    }

    @Test
    fun `non-default window is respected`() {
        // Provide 14 nights but ask for a 14-day window; default is 7.
        val nights = (1..14).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val sevenDayResult = SleepRegularityCalculator.derive(
            nights, sourceId, windowDays = 7, nowMs = now
        )!!
        val fourteenDayResult = SleepRegularityCalculator.derive(
            nights, sourceId, windowDays = 14, nowMs = now
        )!!
        assertEquals(7L, payloadLong(sevenDayResult, Fields.WINDOW_DAYS))
        assertEquals(14L, payloadLong(fourteenDayResult, Fields.WINDOW_DAYS))
    }

    @Test
    fun `readings outside the window are excluded`() {
        // 6 well-clustered nights inside the 7-day window + 1 wildly-off
        // night 30 days ago. The old night must not pull the score down —
        // the windowing filter excludes it.
        val recent = (1..6).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val ancient = listOf(sleepNight(daysAgo = 30, bedHour = 8, durationH = 4))
        val result = SleepRegularityCalculator.derive(
            recent + ancient, sourceId, nowMs = now
        )!!
        assertEquals(100.0, result.reading.value, 0.5)
    }

    @Test
    fun `non-sleep-duration readings are ignored`() {
        // Mix in HEART_RATE rows — they must not affect the regularity math.
        val nights = (1..7).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val noise = (1..7).map {
            MetricReading(
                metricType = MetricType.HEART_RATE.key,
                value = 60.0,
                timestamp = now - it * 24 * hour,
                sourceId = sourceId,
                confidence = ConfidenceTier.HIGH.level
            )
        }
        val result = SleepRegularityCalculator.derive(nights + noise, sourceId, nowMs = now)
        assertEquals(100.0, result!!.reading.value, 0.5)
    }

    @Test
    fun `confidence of result is the minimum of input confidences`() {
        val mixed = (1..7).map { i ->
            val tier = if (i == 3) ConfidenceTier.LOW else ConfidenceTier.HIGH
            sleepNight(daysAgo = i, bedHour = 23, durationH = 8, confidence = tier)
        }
        val result = SleepRegularityCalculator.derive(mixed, sourceId, nowMs = now)!!
        // The single LOW night drags the composite's confidence to LOW.
        assertEquals(ConfidenceTier.LOW.level, result.reading.confidence)
    }

    @Test
    fun `output is a SLEEP_REGULARITY reading carrying the window as duration`() {
        val nights = (1..7).map { sleepNight(daysAgo = it, bedHour = 23, durationH = 8) }
        val result = SleepRegularityCalculator.derive(nights, sourceId, nowMs = now)!!
        assertEquals(MetricType.SLEEP_REGULARITY.key, result.reading.metricType)
        assertEquals(7 * 24 * 3600, result.reading.durationSec)
        assertEquals(sourceId, result.reading.sourceId)
    }

    // ---- helpers ----

    private fun sleepNight(
        daysAgo: Int,
        bedHour: Int,
        durationH: Int,
        confidence: ConfidenceTier = ConfidenceTier.HIGH,
    ): MetricReading {
        // Bedtime = local midnight of (today - daysAgo) + bedHour hours.
        val nightStart = now - daysAgo.toLong() * 24 * hour
        val bed = nightStart + bedHour.toLong() * hour
        val durMs = durationH.toLong() * hour
        return MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            value = (durationH * 3600).toDouble(),
            timestamp = bed + durMs / 2L,
            durationSec = durationH * 3600,
            sourceId = sourceId,
            confidence = confidence.level
        )
    }

    /** Same as [sleepNight] but bedtime is expressed in 30-minute steps. */
    private fun sleepNightHalfHour(
        daysAgo: Int,
        bedHourHalfSteps: Int,
        durationH: Int,
    ): MetricReading {
        val nightStart = now - daysAgo.toLong() * 24 * hour
        val bed = nightStart + bedHourHalfSteps.toLong() * (hour / 2L)
        val durMs = durationH.toLong() * hour
        return MetricReading(
            metricType = MetricType.SLEEP_DURATION.key,
            value = (durationH * 3600).toDouble(),
            timestamp = bed + durMs / 2L,
            durationSec = durationH * 3600,
            sourceId = sourceId,
            confidence = ConfidenceTier.HIGH.level
        )
    }

    private fun payloadLong(
        result: SleepRegularityCalculator.Result,
        fieldKey: String,
    ): Long = result.payload.first { it.fieldKey == fieldKey }.longValue!!

    private fun utcMidnight(year: Int, month: Int, dayOfMonth: Int): Long {
        // Plain epoch math (no JDK time zone surprises): the calculator
        // operates in UTC, so anchor the tests there too.
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(year, month - 1, dayOfMonth, 0, 0, 0)
        return cal.timeInMillis
    }
}
