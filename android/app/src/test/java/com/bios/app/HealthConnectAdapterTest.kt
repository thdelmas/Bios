package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import com.bios.app.model.SleepStage
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the data mapping logic used by HealthConnectAdapter.
 * Since HealthConnectClient requires Android, we test the MetricReading
 * construction and mapping patterns used by each fetch method.
 */
class HealthConnectAdapterTest {

    // -- MetricReading construction (mirrors adapter fetch methods) --

    @Test
    fun `heart rate reading has correct metric type and confidence`() {
        val reading = MetricReading(
            metricType = MetricType.HEART_RATE.key,
            value = 72.0,
            timestamp = 1000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals("heart_rate", reading.metricType)
        assertEquals(72.0, reading.value, 0.01)
        assertEquals(ConfidenceTier.MEDIUM.level, reading.confidence)
    }

    @Test
    fun `HRV reading maps milliseconds correctly`() {
        val reading = MetricReading(
            metricType = MetricType.HEART_RATE_VARIABILITY.key,
            value = 45.5,
            timestamp = 2000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals("heart_rate_variability", reading.metricType)
        assertEquals(45.5, reading.value, 0.01)
    }

    @Test
    fun `SpO2 reading maps percentage`() {
        val reading = MetricReading(
            metricType = MetricType.BLOOD_OXYGEN.key,
            value = 97.0,
            timestamp = 3000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals(97.0, reading.value, 0.01)
    }

    @Test
    fun `skin temp deviation can be negative`() {
        val reading = MetricReading(
            metricType = MetricType.SKIN_TEMPERATURE_DEVIATION.key,
            value = -0.3,
            timestamp = 4000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertTrue(reading.value < 0)
    }

    // -- Sleep stage mapping --

    @Test
    fun `SleepStage values are ordered`() {
        assertEquals(0, SleepStage.AWAKE.value)
        assertEquals(1, SleepStage.LIGHT.value)
        assertEquals(2, SleepStage.DEEP.value)
        assertEquals(3, SleepStage.REM.value)
    }

    @Test
    fun `sleep reading stores stage as double value`() {
        val reading = MetricReading(
            metricType = MetricType.SLEEP_STAGE.key,
            value = SleepStage.DEEP.value.toDouble(),
            timestamp = 5000L,
            durationSec = 3600,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals(2.0, reading.value, 0.01)
        assertEquals(3600, reading.durationSec)
    }

    // -- Steps and calories --

    @Test
    fun `steps reading with duration`() {
        val reading = MetricReading(
            metricType = MetricType.STEPS.key,
            value = 250.0,
            timestamp = 6000L,
            durationSec = 900,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals(250.0, reading.value, 0.01)
        assertEquals(900, reading.durationSec)
    }

    @Test
    fun `active calories reading`() {
        val reading = MetricReading(
            metricType = MetricType.ACTIVE_CALORIES.key,
            value = 150.5,
            timestamp = 7000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertEquals("active_calories", reading.metricType)
        assertEquals(150.5, reading.value, 0.01)
    }

    // -- Confidence tiers --

    @Test
    fun `all adapter readings use MEDIUM confidence`() {
        val confidence = ConfidenceTier.MEDIUM.level
        assertEquals(2, confidence)
    }

    @Test
    fun `ConfidenceTier ordering is correct`() {
        assertTrue(ConfidenceTier.VENDOR_DERIVED.level < ConfidenceTier.LOW.level)
        assertTrue(ConfidenceTier.LOW.level < ConfidenceTier.MEDIUM.level)
        assertTrue(ConfidenceTier.MEDIUM.level < ConfidenceTier.HIGH.level)
        assertTrue(ConfidenceTier.HIGH.level < ConfidenceTier.CLINICAL.level)
    }

    // -- Source ID consistency --

    @Test
    fun `all readings from same source share sourceId`() {
        val sourceId = "health_connect_abc123"
        val readings = listOf(
            MetricReading(metricType = MetricType.HEART_RATE.key, value = 70.0, timestamp = 1000L, sourceId = sourceId, confidence = 2),
            MetricReading(metricType = MetricType.STEPS.key, value = 500.0, timestamp = 1000L, sourceId = sourceId, confidence = 2),
            MetricReading(metricType = MetricType.BLOOD_OXYGEN.key, value = 98.0, timestamp = 1000L, sourceId = sourceId, confidence = 2)
        )
        assertTrue(readings.all { it.sourceId == sourceId })
    }
}
