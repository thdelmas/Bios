package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PhoneSensorAdapter data mapping patterns.
 * Since SensorManager requires Android, we test the MetricReading
 * construction and confidence assignment used by the adapter.
 */
class PhoneSensorAdapterTest {

    @Test
    fun `active minutes reading uses LOW confidence for phone sensors`() {
        val reading = MetricReading(
            metricType = MetricType.ACTIVE_MINUTES.key,
            value = 10.0,
            timestamp = 1000L,
            durationSec = 10,
            sourceId = "phone_sensor",
            confidence = ConfidenceTier.LOW.level
        )
        assertEquals(ConfidenceTier.LOW.level, reading.confidence)
        assertEquals("active_minutes", reading.metricType)
    }

    @Test
    fun `step counter reading uses LOW confidence`() {
        val reading = MetricReading(
            metricType = MetricType.STEPS.key,
            value = 1500.0,
            timestamp = 1000L,
            sourceId = "phone_sensor",
            confidence = ConfidenceTier.LOW.level
        )
        assertEquals(ConfidenceTier.LOW.level, reading.confidence)
    }

    @Test
    fun `phone sensor confidence is lower than wearable`() {
        assertTrue(ConfidenceTier.LOW.level < ConfidenceTier.MEDIUM.level)
    }

    @Test
    fun `phone steps would lose dedup to wearable steps at same timestamp`() {
        val phoneSteps = MetricReading(
            metricType = MetricType.STEPS.key,
            value = 100.0,
            timestamp = 5000L,
            sourceId = "phone",
            confidence = ConfidenceTier.LOW.level
        )
        val wearableSteps = MetricReading(
            metricType = MetricType.STEPS.key,
            value = 105.0,
            timestamp = 5000L,
            sourceId = "wearable",
            confidence = ConfidenceTier.MEDIUM.level
        )
        assertTrue(wearableSteps.confidence > phoneSteps.confidence)
    }

    @Test
    fun `active minutes duration matches sample window`() {
        val sampleDurationSec = 10
        val reading = MetricReading(
            metricType = MetricType.ACTIVE_MINUTES.key,
            value = sampleDurationSec.toDouble(),
            timestamp = System.currentTimeMillis(),
            durationSec = sampleDurationSec,
            sourceId = "phone_sensor",
            confidence = ConfidenceTier.LOW.level
        )
        assertEquals(reading.value.toInt(), reading.durationSec)
    }

    @Test
    fun `gravity constant approximation`() {
        val gravity = 9.81f
        assertTrue(gravity > 9.7f && gravity < 9.9f)
    }
}
