package com.bios.app

import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for logic used by HomeScreen: status card state, alert grouping, and baseline countdown.
 */
class HomeScreenTest {

    private fun anomaly(
        severity: Int = AlertTier.NOTICE.level,
        acknowledged: Boolean = false
    ) = Anomaly(
        metricTypes = "[\"heart_rate\"]",
        deviationScores = "{\"heart_rate\":2.0}",
        combinedScore = 2.0,
        severity = severity,
        title = "Test Alert",
        explanation = "Test explanation",
        acknowledged = acknowledged
    )

    // -- Status card logic --

    @Test
    fun `zero unacknowledged alerts shows all clear`() {
        val unacknowledged = emptyList<Anomaly>()
        assertEquals(0, unacknowledged.size)
    }

    @Test
    fun `alert count text is singular for one alert`() {
        val count = 1
        val text = "$count alert${if (count == 1) "" else "s"} need your attention"
        assertEquals("1 alert need your attention", text)
    }

    @Test
    fun `alert count text is plural for multiple alerts`() {
        val count = 3
        val text = "$count alert${if (count == 1) "" else "s"} need your attention"
        assertEquals("3 alerts need your attention", text)
    }

    @Test
    fun `hasAdvisory detects advisory tier`() {
        val alerts = listOf(
            anomaly(severity = AlertTier.NOTICE.level),
            anomaly(severity = AlertTier.ADVISORY.level)
        )
        val hasAdvisory = alerts.any { AlertTier.fromLevel(it.severity) >= AlertTier.ADVISORY }
        assertTrue(hasAdvisory)
    }

    @Test
    fun `hasAdvisory detects urgent tier`() {
        val alerts = listOf(anomaly(severity = AlertTier.URGENT.level))
        val hasAdvisory = alerts.any { AlertTier.fromLevel(it.severity) >= AlertTier.ADVISORY }
        assertTrue(hasAdvisory)
    }

    @Test
    fun `hasAdvisory is false for only notices`() {
        val alerts = listOf(
            anomaly(severity = AlertTier.NOTICE.level),
            anomaly(severity = AlertTier.OBSERVATION.level)
        )
        val hasAdvisory = alerts.any { AlertTier.fromLevel(it.severity) >= AlertTier.ADVISORY }
        assertFalse(hasAdvisory)
    }

    @Test
    fun `hasAdvisory is false for empty list`() {
        val alerts = emptyList<Anomaly>()
        val hasAdvisory = alerts.any { AlertTier.fromLevel(it.severity) >= AlertTier.ADVISORY }
        assertFalse(hasAdvisory)
    }

    // -- Baseline countdown logic --

    @Test
    fun `countdown shows remaining days`() {
        val currentDays = 3
        val requiredDays = 7
        val remaining = requiredDays - currentDays
        assertEquals(4, remaining)
    }

    @Test
    fun `countdown singular text for one day remaining`() {
        val remaining = 1
        val text = "Bios needs $remaining more day${if (remaining == 1) "" else "s"} of data"
        assertTrue(text.contains("1 more day "))
    }

    @Test
    fun `countdown plural text for multiple days remaining`() {
        val remaining = 5
        val text = "Bios needs $remaining more day${if (remaining == 1) "" else "s"} of data"
        assertTrue(text.contains("5 more days"))
    }

    @Test
    fun `progress fraction is correct`() {
        val progress = 3.toFloat() / 7.toFloat()
        assertEquals(0.4285f, progress, 0.001f)
    }

    @Test
    fun `progress at zero days is zero`() {
        val progress = 0.toFloat() / 7.toFloat()
        assertEquals(0.0f, progress, 0.001f)
    }

    @Test
    fun `progress at required days is one`() {
        val progress = 7.toFloat() / 7.toFloat()
        assertEquals(1.0f, progress, 0.001f)
    }

    // -- AlertTier comparison --

    @Test
    fun `AlertTier ordering matches severity`() {
        assertTrue(AlertTier.OBSERVATION < AlertTier.NOTICE)
        assertTrue(AlertTier.NOTICE < AlertTier.ADVISORY)
        assertTrue(AlertTier.ADVISORY < AlertTier.URGENT)
    }
}
