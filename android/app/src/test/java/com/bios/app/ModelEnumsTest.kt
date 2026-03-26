package com.bios.app

import com.bios.app.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for model enum utilities and data class edge cases.
 */
class ModelEnumsTest {

    // --- MetricType ---

    @Test
    fun `fromKey returns correct metric type`() {
        assertEquals(MetricType.HEART_RATE, MetricType.fromKey("heart_rate"))
        assertEquals(MetricType.STEPS, MetricType.fromKey("steps"))
        assertEquals(MetricType.BLOOD_OXYGEN, MetricType.fromKey("blood_oxygen"))
    }

    @Test
    fun `fromKey returns null for unknown key`() {
        assertNull(MetricType.fromKey("unknown_metric"))
        assertNull(MetricType.fromKey(""))
    }

    @Test
    fun `readableName capitalizes and replaces underscores`() {
        assertEquals("Heart rate", MetricType.HEART_RATE.readableName)
        assertEquals("Blood oxygen", MetricType.BLOOD_OXYGEN.readableName)
        assertEquals("Skin temperature deviation", MetricType.SKIN_TEMPERATURE_DEVIATION.readableName)
    }

    @Test
    fun `all metric types have unique keys`() {
        val keys = MetricType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `fromKey roundtrips for all entries`() {
        for (type in MetricType.entries) {
            assertEquals(type, MetricType.fromKey(type.key))
        }
    }

    // --- ConfidenceTier ---

    @Test
    fun `confidenceTier fromLevel roundtrips`() {
        for (tier in ConfidenceTier.entries) {
            assertEquals(tier, ConfidenceTier.fromLevel(tier.level))
        }
    }

    @Test
    fun `confidenceTier fromLevel unknown returns LOW`() {
        assertEquals(ConfidenceTier.LOW, ConfidenceTier.fromLevel(99))
    }

    @Test
    fun `confidenceTier levels are ordered`() {
        assertTrue(ConfidenceTier.VENDOR_DERIVED.level < ConfidenceTier.LOW.level)
        assertTrue(ConfidenceTier.LOW.level < ConfidenceTier.MEDIUM.level)
        assertTrue(ConfidenceTier.MEDIUM.level < ConfidenceTier.HIGH.level)
        assertTrue(ConfidenceTier.HIGH.level < ConfidenceTier.CLINICAL.level)
    }

    // --- PersonalBaseline.zScore ---

    @Test
    fun `zScore returns 0 when stdDev is zero`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate", mean = 70.0, stdDev = 0.0,
            p5 = 60.0, p95 = 80.0
        )
        assertEquals(0.0, baseline.zScore(75.0), 0.001)
    }

    @Test
    fun `zScore returns 0 when stdDev is negative`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate", mean = 70.0, stdDev = -1.0,
            p5 = 60.0, p95 = 80.0
        )
        assertEquals(0.0, baseline.zScore(75.0), 0.001)
    }

    @Test
    fun `zScore computes correctly for positive deviation`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate", mean = 70.0, stdDev = 5.0,
            p5 = 60.0, p95 = 80.0
        )
        assertEquals(2.0, baseline.zScore(80.0), 0.001)
    }

    @Test
    fun `zScore computes correctly for negative deviation`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate", mean = 70.0, stdDev = 5.0,
            p5 = 60.0, p95 = 80.0
        )
        assertEquals(-2.0, baseline.zScore(60.0), 0.001)
    }

    @Test
    fun `zScore is zero at the mean`() {
        val baseline = PersonalBaseline(
            metricType = "heart_rate", mean = 70.0, stdDev = 5.0,
            p5 = 60.0, p95 = 80.0
        )
        assertEquals(0.0, baseline.zScore(70.0), 0.001)
    }

    // --- Anomaly defaults ---

    @Test
    fun `anomaly feedback fields default to null`() {
        val anomaly = Anomaly(
            metricTypes = "[]", deviationScores = "{}",
            combinedScore = 1.0, severity = 1,
            title = "Test", explanation = "Test"
        )
        assertNull(anomaly.feedbackAt)
        assertNull(anomaly.feltSick)
        assertNull(anomaly.visitedDoctor)
        assertNull(anomaly.diagnosis)
        assertNull(anomaly.symptoms)
        assertNull(anomaly.notes)
        assertNull(anomaly.outcomeAccurate)
    }

    // --- HealthEventType ---

    @Test
    fun `health event types have 6 entries`() {
        assertEquals(6, HealthEventType.entries.size)
    }

    @Test
    fun `health event type labels are non-empty`() {
        for (type in HealthEventType.entries) {
            assertTrue("${type.name} should have a label", type.label.isNotBlank())
        }
    }

    // --- HealthEventStatus ---

    @Test
    fun `health event statuses have 3 entries`() {
        assertEquals(3, HealthEventStatus.entries.size)
    }

    // --- SleepStage ---

    @Test
    fun `sleep stages have ascending values`() {
        assertTrue(SleepStage.AWAKE.value < SleepStage.LIGHT.value)
        assertTrue(SleepStage.LIGHT.value < SleepStage.DEEP.value)
        assertTrue(SleepStage.DEEP.value < SleepStage.REM.value)
    }
}
