package com.bios.app

import com.bios.app.data.dao.TimedValue
import com.bios.app.engine.WindowCoverage
import com.bios.app.model.Anomaly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * docs/specs/rule-window-coverage.md. Times are the owner's 2026-10-03/04
 * weekend: COROS stamps daily RHR/HRV at 06:00 on the wake-up day.
 */
class WindowCoverageTest {

    private val zone = ZoneId.of("Europe/Madrid")
    private fun at(day: Int, h: Int, m: Int = 0) =
        LocalDateTime.of(2026, 10, day, h, m).atZone(zone).toInstant().toEpochMilli()

    private val thursdayNight = TimedValue(at(2, 6), 58.0)
    private val fridayDrinksNight = TimedValue(at(3, 6), 66.0)

    @Test
    fun `sunday case - watch off, one night left in a 48 h window is not evaluable`() {
        val sample = WindowCoverage.sample(listOf(fridayDrinksNight), windowHours = 48, now = at(4, 6, 14))
        assertFalse(sample.sufficient)
    }

    @Test
    fun `saturday case - two nights in a 48 h window are evaluable`() {
        val sample = WindowCoverage.sample(listOf(thursdayNight, fridayDrinksNight), 48, now = at(3, 6, 14))
        assertTrue(sample.sufficient)
        assertEquals(listOf(58.0, 66.0), sample.values)
    }

    @Test
    fun `newest reading older than 30 h is stale even when slots are covered`() {
        val readings = (0..6).map { TimedValue(at(1, 6) - it * 86_400_000L, 56.0) }
        val sample = WindowCoverage.sample(readings, windowHours = 168, now = at(2, 13))
        assertFalse(sample.sufficient)
    }

    @Test
    fun `required slots per window length`() {
        assertEquals(1, WindowCoverage.requiredSlots(24))
        assertEquals(2, WindowCoverage.requiredSlots(48))
        assertEquals(2, WindowCoverage.requiredSlots(72))
        assertEquals(5, WindowCoverage.requiredSlots(168))
        assertEquals(1, WindowCoverage.requiredSlots(6))
    }

    @Test
    fun `seven day window needs five of seven days`() {
        val now = at(10, 7)
        val five = listOf(0, 1, 2, 4, 6).map { TimedValue(at(10, 6) - it * 86_400_000L, 56.0) }
        val four = listOf(0, 2, 4, 6).map { TimedValue(at(10, 6) - it * 86_400_000L, 56.0) }
        assertTrue(WindowCoverage.sample(five, 168, now).sufficient)
        assertFalse(WindowCoverage.sample(four, 168, now).sufficient)
    }

    @Test
    fun `no readings is never sufficient`() {
        assertFalse(WindowCoverage.sample(emptyList(), 24, at(4, 6)).sufficient)
    }

    private fun alert(detectedAt: Long, metrics: String) = Anomaly(
        detectedAt = detectedAt, metricTypes = metrics, deviationScores = "{}",
        combinedScore = 1.0, severity = 2, title = "t", explanation = "e",
    )

    @Test
    fun `evidence already alerted - same night, overlapping metric, no newer reading`() {
        val saturdayAlert = alert(at(3, 8, 36), "[\"resting_heart_rate\"]")
        assertTrue(
            WindowCoverage.evidenceAlreadyAlerted(
                setOf("resting_heart_rate", "heart_rate_variability"), fridayDrinksNight.timestamp, listOf(saturdayAlert)
            )
        )
    }

    @Test
    fun `newer reading after the earlier alert lets a new alert through`() {
        val saturdayAlert = alert(at(3, 8, 36), "[\"resting_heart_rate\"]")
        val sundayNight = at(4, 6)
        assertFalse(WindowCoverage.evidenceAlreadyAlerted(setOf("resting_heart_rate"), sundayNight, listOf(saturdayAlert)))
    }

    @Test
    fun `earlier alert on unrelated metrics does not suppress`() {
        val stepsAlert = alert(at(3, 8, 36), "[\"steps\"]")
        assertFalse(
            WindowCoverage.evidenceAlreadyAlerted(setOf("resting_heart_rate"), fridayDrinksNight.timestamp, listOf(stepsAlert))
        )
    }
}
