package com.bios.app

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.SignalSnapshot
import com.bios.app.model.PersonalBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/specs/alert-detail.md — criterion 6: signals stored at detection. */
class SignalSnapshotTest {

    private val rhr = PersonalBaseline(
        metricType = "resting_heart_rate", mean = 59.0, stdDev = 3.0, p5 = 54.0, p95 = 65.0,
    )

    @Test
    fun `round trip keeps value, range, z and reading count`() {
        val signals = listOf(
            AlertDetailContent.signal("resting_heart_rate", 69.0, rhr, readingCount = 2),
            AlertDetailContent.signal("steps", 38.0, rhr.copy(metricType = "steps", p5 = 1.0, p95 = 189.0), 503),
        )
        val back = SignalSnapshot.decode(SignalSnapshot.encode(signals))
        assertEquals(2, back.size)
        val first = back.first { it.metricKey == "resting_heart_rate" }
        assertEquals(69.0, first.value, 1e-9)
        assertEquals(54.0, first.usualLow, 1e-9)
        assertEquals(65.0, first.usualHigh, 1e-9)
        assertEquals(signals[0].zScore, first.zScore, 1e-9)
        assertEquals(2, first.readingCount)
        assertTrue(first.deviating)
        assertTrue(first.sources.isEmpty())
    }

    @Test
    fun `null, blank and garbage decode to nothing`() {
        assertTrue(SignalSnapshot.decode(null).isEmpty())
        assertTrue(SignalSnapshot.decode("  ").isEmpty())
        assertTrue(SignalSnapshot.decode("not json").isEmpty())
        assertTrue(SignalSnapshot.decode("[{\"value\": 1}]").isEmpty())
    }
}
