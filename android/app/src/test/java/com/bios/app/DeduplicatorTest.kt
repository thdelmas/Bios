package com.bios.app

import com.bios.app.ingest.Deduplicator
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real [Deduplicator] used by the ingestion pipeline.
 *
 * Two regimes are covered:
 * - Point-in-time metrics (HR, HRV, etc.) — exact `(metricType, timestamp)`
 *   collisions collapse to the highest-confidence reading.
 * - Time-window-aggregate metrics (steps, active calories/minutes) — bucketed
 *   counts from different sources within the dedup window collapse to the
 *   highest-confidence reading. Same-source readings inside the window are
 *   preserved (consecutive buckets are sequential data, not duplicates).
 *
 * The cross-source step-bucket case is the regression bait for issue #14 —
 * Health Connect + Gadgetbridge reporting the same activity with a few-hundred-ms
 * clock skew used to inflate `SUM(value)` downstream.
 */
class DeduplicatorTest {

    private fun reading(
        metricType: String = MetricType.HEART_RATE.key,
        value: Double = 70.0,
        timestamp: Long = 1000L,
        sourceId: String = "source1",
        confidence: Int = ConfidenceTier.MEDIUM.level,
    ) = MetricReading(
        metricType = metricType,
        value = value,
        timestamp = timestamp,
        sourceId = sourceId,
        confidence = confidence,
    )

    // ---- Pass 1: exact-match dedup (all metrics) ----

    @Test
    fun `no duplicates passes through unchanged`() {
        val readings = listOf(
            reading(timestamp = 1000L, value = 70.0),
            reading(timestamp = 2000L, value = 72.0),
            reading(timestamp = 3000L, value = 68.0),
        )
        val result = Deduplicator.deduplicate(readings)
        assertEquals(3, result.size)
    }

    @Test
    fun `duplicate timestamp and type keeps higher confidence`() {
        val low = reading(timestamp = 1000L, confidence = ConfidenceTier.LOW.level, value = 70.0)
        val high = reading(timestamp = 1000L, confidence = ConfidenceTier.HIGH.level, value = 72.0)
        val result = Deduplicator.deduplicate(listOf(low, high))
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
        assertEquals(72.0, result[0].value, 0.01)
    }

    @Test
    fun `different metric types at same timestamp are not deduped`() {
        val hr = reading(metricType = MetricType.HEART_RATE.key, timestamp = 1000L)
        val hrv = reading(metricType = MetricType.HEART_RATE_VARIABILITY.key, timestamp = 1000L)
        val result = Deduplicator.deduplicate(listOf(hr, hrv))
        assertEquals(2, result.size)
    }

    @Test
    fun `same type different timestamps are not deduped`() {
        val r1 = reading(timestamp = 1000L)
        val r2 = reading(timestamp = 2000L)
        val result = Deduplicator.deduplicate(listOf(r1, r2))
        assertEquals(2, result.size)
    }

    @Test
    fun `results are sorted by timestamp`() {
        val readings = listOf(
            reading(timestamp = 3000L),
            reading(timestamp = 1000L),
            reading(timestamp = 2000L),
        )
        val result = Deduplicator.deduplicate(readings)
        assertEquals(listOf(1000L, 2000L, 3000L), result.map { it.timestamp })
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(Deduplicator.deduplicate(emptyList()).isEmpty())
    }

    // ---- Pass 2: window-overlap dedup (steps, active calories, active minutes) ----

    @Test
    fun `cross-source step buckets within window collapse to higher confidence`() {
        // The issue #14 scenario: HC StepsRecord vs Gadgetbridge bucket covering
        // the same activity, separated by sub-second clock skew.
        val hc = reading(
            metricType = MetricType.STEPS.key,
            value = 200.0,
            timestamp = 12_000_000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.HIGH.level,
        )
        val gadgetbridge = reading(
            metricType = MetricType.STEPS.key,
            value = 200.0,
            timestamp = 12_000_347L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(hc, gadgetbridge))
        assertEquals(1, result.size)
        assertEquals("health_connect", result[0].sourceId)
    }

    @Test
    fun `cross-source step buckets outside window both survive`() {
        // 10 minutes apart — beyond the 5-minute window — these are genuinely
        // distinct buckets, not duplicates.
        val a = reading(
            metricType = MetricType.STEPS.key,
            timestamp = 0L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.HIGH.level,
        )
        val b = reading(
            metricType = MetricType.STEPS.key,
            timestamp = 10L * 60_000L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `same-source consecutive step buckets inside window are kept`() {
        // Within one source, sequential buckets are real data — not duplicates.
        // E.g. Gadgetbridge sometimes emits per-minute step rows during a sync.
        val first = reading(
            metricType = MetricType.STEPS.key,
            timestamp = 0L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val second = reading(
            metricType = MetricType.STEPS.key,
            timestamp = 60_000L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(first, second))
        assertEquals(2, result.size)
    }

    @Test
    fun `step window boundary is inclusive`() {
        // Exactly at the window edge counts as colliding — the bucket boundary
        // is well within the noise envelope of real cross-source skew.
        val window = 5L * 60_000L
        val hc = reading(
            metricType = MetricType.STEPS.key,
            timestamp = 0L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.HIGH.level,
        )
        val gadgetbridge = reading(
            metricType = MetricType.STEPS.key,
            timestamp = window,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(hc, gadgetbridge))
        assertEquals(1, result.size)
        assertEquals("health_connect", result[0].sourceId)
    }

    @Test
    fun `active calories also use windowed dedup`() {
        val hc = reading(
            metricType = MetricType.ACTIVE_CALORIES.key,
            timestamp = 1000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.HIGH.level,
        )
        val gadgetbridge = reading(
            metricType = MetricType.ACTIVE_CALORIES.key,
            timestamp = 1500L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(hc, gadgetbridge))
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
    }

    @Test
    fun `active minutes also use windowed dedup`() {
        val hc = reading(
            metricType = MetricType.ACTIVE_MINUTES.key,
            timestamp = 1000L,
            sourceId = "health_connect",
            confidence = ConfidenceTier.HIGH.level,
        )
        val gadgetbridge = reading(
            metricType = MetricType.ACTIVE_MINUTES.key,
            timestamp = 1500L,
            sourceId = "gadgetbridge",
            confidence = ConfidenceTier.MEDIUM.level,
        )
        val result = Deduplicator.deduplicate(listOf(hc, gadgetbridge))
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
    }

    // ---- Regression: point-in-time metrics keep exact-match-only behavior ----

    @Test
    fun `heart rate from different sources at near-but-not-equal timestamps both survive`() {
        // HR is point-in-time — even a 1-second skew between two adapters is
        // legitimate sampling, not a duplicate. Exact-ms collisions still dedupe.
        val a = reading(
            metricType = MetricType.HEART_RATE.key,
            timestamp = 1000L,
            sourceId = "oura",
        )
        val b = reading(
            metricType = MetricType.HEART_RATE.key,
            timestamp = 1500L,
            sourceId = "health_connect",
        )
        val result = Deduplicator.deduplicate(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `hrv exact-ms collision across sources still dedupes`() {
        val low = reading(
            metricType = MetricType.HEART_RATE_VARIABILITY.key,
            timestamp = 1000L,
            sourceId = "phone_sensor",
            confidence = ConfidenceTier.LOW.level,
        )
        val high = reading(
            metricType = MetricType.HEART_RATE_VARIABILITY.key,
            timestamp = 1000L,
            sourceId = "oura",
            confidence = ConfidenceTier.HIGH.level,
        )
        val result = Deduplicator.deduplicate(listOf(low, high))
        assertEquals(1, result.size)
        assertEquals(ConfidenceTier.HIGH.level, result[0].confidence)
    }

    // ---- Window-policy surface ----

    @Test
    fun `point-in-time metrics report zero window`() {
        assertEquals(0L, Deduplicator.windowMs(MetricType.HEART_RATE.key))
        assertEquals(0L, Deduplicator.windowMs(MetricType.HEART_RATE_VARIABILITY.key))
        assertEquals(0L, Deduplicator.windowMs(MetricType.RESTING_HEART_RATE.key))
        assertEquals(0L, Deduplicator.windowMs(MetricType.BLOOD_OXYGEN.key))
    }

    @Test
    fun `bucketed activity metrics report a non-zero window`() {
        assertTrue(Deduplicator.windowMs(MetricType.STEPS.key) > 0L)
        assertTrue(Deduplicator.windowMs(MetricType.ACTIVE_CALORIES.key) > 0L)
        assertTrue(Deduplicator.windowMs(MetricType.ACTIVE_MINUTES.key) > 0L)
    }
}
