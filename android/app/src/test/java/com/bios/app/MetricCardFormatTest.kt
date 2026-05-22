package com.bios.app

import com.bios.app.ui.components.formatRelativeTime
import com.bios.app.ui.components.formatValue
import com.bios.app.ui.components.isStale
import com.bios.app.ui.components.staleThresholdMillis
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricCardFormatTest {

    @Test
    fun `sleep duration formats seconds as hours and minutes`() {
        // 7h 23m = 7 * 3600 + 23 * 60 = 26580s
        assertEquals("7h 23m", formatValue(26580.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles whole hours`() {
        assertEquals("8h 0m", formatValue(8.0 * 3600, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles sub-minute remainder by flooring`() {
        // 6h 30m 45s -> floor to 6h 30m
        assertEquals("6h 30m", formatValue(6 * 3600 + 30 * 60 + 45.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `sleep duration handles zero`() {
        assertEquals("0h 0m", formatValue(0.0, MetricType.SLEEP_DURATION))
    }

    @Test
    fun `heart rate formats as integer bpm`() {
        assertEquals("72", formatValue(72.4, MetricType.HEART_RATE))
    }

    @Test
    fun `relative time under 60 seconds is just now`() {
        assertEquals("just now", formatRelativeTime(0))
        assertEquals("just now", formatRelativeTime(30_000))
        assertEquals("just now", formatRelativeTime(59_000))
    }

    @Test
    fun `relative time clock skew into the future treated as just now`() {
        assertEquals("just now", formatRelativeTime(-5_000))
    }

    @Test
    fun `relative time under an hour rounds down to minutes`() {
        assertEquals("1m ago", formatRelativeTime(60_000))
        assertEquals("5m ago", formatRelativeTime(5 * 60_000))
        assertEquals("59m ago", formatRelativeTime(59 * 60_000 + 30_000))
    }

    @Test
    fun `relative time under a day rounds down to hours`() {
        assertEquals("1h ago", formatRelativeTime(60 * 60_000))
        assertEquals("2h ago", formatRelativeTime(2 * 60 * 60_000 + 45 * 60_000))
        assertEquals("23h ago", formatRelativeTime(23 * 60 * 60_000))
    }

    @Test
    fun `relative time beyond a day rounds down to days`() {
        assertEquals("1d ago", formatRelativeTime(24L * 60 * 60_000))
        assertEquals("7d ago", formatRelativeTime(7L * 24 * 60 * 60_000))
    }

    @Test
    fun `live vitals have a 10 minute stale threshold`() {
        val tenMin = 10L * 60_000L
        assertEquals(tenMin, staleThresholdMillis(MetricType.HEART_RATE))
        assertEquals(tenMin, staleThresholdMillis(MetricType.HEART_RATE_VARIABILITY))
        assertEquals(tenMin, staleThresholdMillis(MetricType.RESPIRATORY_RATE))
        assertEquals(tenMin, staleThresholdMillis(MetricType.BLOOD_OXYGEN))
    }

    @Test
    fun `daily-rhythm metrics have no stale threshold`() {
        // Sleep and skin-temp deviation are reported once per night by design —
        // flagging them as "stale" at 12+ hours would be noise, not signal.
        assertNull(staleThresholdMillis(MetricType.SLEEP_DURATION))
        assertNull(staleThresholdMillis(MetricType.SKIN_TEMPERATURE_DEVIATION))
        assertNull(staleThresholdMillis(MetricType.RESTING_HEART_RATE))
    }

    @Test
    fun `isStale is false for fresh HR`() {
        assertFalse(isStale(MetricType.HEART_RATE, 30_000))
        assertFalse(isStale(MetricType.HEART_RATE, 9 * 60_000L))
    }

    @Test
    fun `isStale is true for HR older than threshold`() {
        assertTrue(isStale(MetricType.HEART_RATE, 11 * 60_000L))
        assertTrue(isStale(MetricType.HEART_RATE, 11L * 60 * 60_000L))
    }

    @Test
    fun `isStale is false for metrics without a threshold regardless of age`() {
        assertFalse(isStale(MetricType.SLEEP_DURATION, 48L * 60 * 60_000L))
    }
}
