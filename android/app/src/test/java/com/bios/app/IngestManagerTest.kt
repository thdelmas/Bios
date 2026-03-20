package com.bios.app

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.model.MetricType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for IngestManager's deduplication logic.
 * The deduplicate method is private, so we mirror it here for direct testing.
 */
class IngestManagerTest {

    // Mirror of IngestManager.deduplicate
    private fun deduplicate(readings: List<MetricReading>): List<MetricReading> {
        val seen = mutableMapOf<String, MetricReading>()
        for (reading in readings) {
            val key = "${reading.metricType}_${reading.timestamp}"
            val existing = seen[key]
            if (existing == null || reading.confidence > existing.confidence) {
                seen[key] = reading
            }
        }
        return seen.values.sortedBy { it.timestamp }
    }

    private fun reading(
        metricType: String = MetricType.HEART_RATE.key,
        value: Double = 70.0,
        timestamp: Long = 1000L,
        sourceId: String = "source1",
        confidence: Int = ConfidenceTier.MEDIUM.level
    ) = MetricReading(
        metricType = metricType,
        value = value,
        timestamp = timestamp,
        sourceId = sourceId,
        confidence = confidence
    )

    @Test
    fun `no duplicates passes through unchanged`() {
        val readings = listOf(
            reading(timestamp = 1000L, value = 70.0),
            reading(timestamp = 2000L, value = 72.0),
            reading(timestamp = 3000L, value = 68.0)
        )

        val result = deduplicate(readings)
        assertEquals(3, result.size)
    }

    @Test
    fun `duplicate timestamp and type keeps higher confidence`() {
        val low = reading(timestamp = 1000L, confidence = ConfidenceTier.LOW.level, value = 70.0)
        val high = reading(timestamp = 1000L, confidence = ConfidenceTier.HIGH.level, value = 72.0)

        val result = deduplicate(listOf(low, high))
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
        assertEquals(72.0, result[0].value, 0.01)
    }

    @Test
    fun `different metric types at same timestamp are not deduped`() {
        val hr = reading(metricType = MetricType.HEART_RATE.key, timestamp = 1000L)
        val hrv = reading(metricType = MetricType.HEART_RATE_VARIABILITY.key, timestamp = 1000L)

        val result = deduplicate(listOf(hr, hrv))
        assertEquals(2, result.size)
    }

    @Test
    fun `same type different timestamps are not deduped`() {
        val r1 = reading(timestamp = 1000L)
        val r2 = reading(timestamp = 2000L)

        val result = deduplicate(listOf(r1, r2))
        assertEquals(2, result.size)
    }

    @Test
    fun `results are sorted by timestamp`() {
        val readings = listOf(
            reading(timestamp = 3000L),
            reading(timestamp = 1000L),
            reading(timestamp = 2000L)
        )

        val result = deduplicate(readings)
        assertEquals(listOf(1000L, 2000L, 3000L), result.map { it.timestamp })
    }

    @Test
    fun `empty input returns empty`() {
        val result = deduplicate(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `equal confidence keeps first seen`() {
        val r1 = reading(timestamp = 1000L, confidence = ConfidenceTier.MEDIUM.level, value = 70.0)
        val r2 = reading(timestamp = 1000L, confidence = ConfidenceTier.MEDIUM.level, value = 72.0)

        val result = deduplicate(listOf(r1, r2))
        assertEquals(1, result.size)
        // Equal confidence means second doesn't replace first
        assertEquals(70.0, result[0].value, 0.01)
    }

    @Test
    fun `multiple duplicates with escalating confidence keeps best`() {
        val readings = listOf(
            reading(timestamp = 1000L, confidence = ConfidenceTier.LOW.level, value = 68.0),
            reading(timestamp = 1000L, confidence = ConfidenceTier.MEDIUM.level, value = 70.0),
            reading(timestamp = 1000L, confidence = ConfidenceTier.HIGH.level, value = 72.0),
            reading(timestamp = 1000L, confidence = ConfidenceTier.MEDIUM.level, value = 71.0)
        )

        val result = deduplicate(readings)
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
    }
}
