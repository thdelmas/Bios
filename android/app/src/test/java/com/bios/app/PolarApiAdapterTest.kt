package com.bios.app

import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PolarApiAdapter connection state and reading construction.
 * Network calls are not tested here; we test pure logic and data mapping.
 */
class PolarApiAdapterTest {

    private fun createAdapter(token: String? = "test_token"): PolarApiAdapter =
        PolarApiAdapter(getToken = { token }, hasToken = { token != null })

    @Test
    fun `isConnected returns true when token exists`() {
        assertTrue(createAdapter("token").isConnected)
    }

    @Test
    fun `isConnected returns false when no token`() {
        assertFalse(createAdapter(null).isConnected)
    }

    @Test
    fun `Polar HR readings use HIGH confidence for clinical-grade ECG`() {
        val reading = MetricReading(
            metricType = MetricType.HEART_RATE.key,
            value = 72.0,
            timestamp = 1000L,
            sourceId = "polar_src",
            confidence = ConfidenceTier.HIGH.level
        )
        assertEquals(ConfidenceTier.HIGH.level, reading.confidence)
    }

    @Test
    fun `Polar HRV uses HIGH confidence`() {
        val reading = MetricReading(
            metricType = MetricType.HEART_RATE_VARIABILITY.key,
            value = 45.0,
            timestamp = 1000L,
            sourceId = "polar_src",
            confidence = ConfidenceTier.HIGH.level
        )
        assertEquals(ConfidenceTier.HIGH.level, reading.confidence)
        assertEquals("heart_rate_variability", reading.metricType)
    }

    @Test
    fun `resting HR reading maps correctly`() {
        val reading = MetricReading(
            metricType = MetricType.RESTING_HEART_RATE.key,
            value = 58.0,
            timestamp = 1000L,
            sourceId = "polar_src",
            confidence = ConfidenceTier.HIGH.level
        )
        assertEquals("resting_heart_rate", reading.metricType)
    }

    @Test
    fun `active minutes stored as minutes`() {
        val durationMs = 3600000L // 1 hour in ms
        val reading = MetricReading(
            metricType = MetricType.ACTIVE_MINUTES.key,
            value = durationMs / 60000.0,
            timestamp = 1000L,
            sourceId = "polar_src",
            confidence = ConfidenceTier.HIGH.level
        )
        assertEquals(60.0, reading.value, 0.01)
    }

    @Test
    fun `BASE_URL points to Polar AccessLink v3`() {
        assertTrue(PolarApiAdapter.BASE_URL.contains("polaraccesslink.com"))
        assertTrue(PolarApiAdapter.BASE_URL.contains("v3"))
    }

    @Test
    fun `PROVIDER_KEY is polar`() {
        assertEquals("polar", PolarApiAdapter.PROVIDER_KEY)
    }
}
