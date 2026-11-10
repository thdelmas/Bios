package com.bios.app

import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.SignalStrip
import com.bios.app.model.PersonalBaseline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/specs/alert-detail.md — criterion 6 (range strips). */
class SignalStripTest {

    private val rhr = PersonalBaseline(
        metricType = "resting_heart_rate", mean = 59.0, stdDev = 3.0, p5 = 54.0, p95 = 65.0,
    )

    @Test
    fun `value above the range lands right of the band, inside the track`() {
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 69.0, rhr))
        assertTrue(g.dot > g.bandEnd)
        assertTrue(g.dot <= 1f)
        assertTrue(g.bandStart > 0f)
    }

    @Test
    fun `value below the range lands left of the band`() {
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 48.0, rhr))
        assertTrue(g.dot < g.bandStart)
        assertTrue(g.dot >= 0f)
    }

    @Test
    fun `value inside the range lands inside the band`() {
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 60.0, rhr))
        assertTrue(g.dot in g.bandStart..g.bandEnd)
    }

    @Test
    fun `band keeps half a band of air on each side when the value is inside`() {
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 60.0, rhr))
        assertEquals(54.0 - 5.5, g.domainLow, 1e-9)
        assertEquals(65.0 + 5.5, g.domainHigh, 1e-9)
        assertEquals(0.25f, g.bandStart, 1e-6f)
        assertEquals(0.75f, g.bandEnd, 1e-6f)
    }

    @Test
    fun `a flat baseline does not divide by zero`() {
        val flat = rhr.copy(p5 = 60.0, p95 = 60.0, stdDev = 0.0)
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 60.0, flat))
        assertTrue(g.dot in 0f..1f)
        assertTrue(g.bandEnd >= g.bandStart)
        assertTrue(g.domainHigh > g.domainLow)
    }

    @Test
    fun `far zones sit beyond the band on both sides`() {
        val g = SignalStrip.geometry(AlertDetailContent.signal("resting_heart_rate", 60.0, rhr))
        assertTrue(g.farLow < g.bandStart)
        assertTrue(g.farHigh > g.bandEnd)
        // band 54–65 (11 wide): far at 54 − 4.53 and 65 + 4.53, inside the half-band air.
        assertTrue(g.farLow > 0f && g.farHigh < 1f)
    }
}
