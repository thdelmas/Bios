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
 * Two regimes:
 * - Point-in-time metrics (HR, HRV, etc.): exact `(metricType, timestamp)`
 *   collisions collapse to the highest-confidence reading, across sources.
 * - Count metrics (steps, active calories/minutes): one row per
 *   `(metricType, timestamp, sourceId)`. Every witness is stored; the day
 *   total picks one witness per calendar day at read time
 *   (docs/specs/source-preference.md). The old cross-source window pass
 *   would have deleted the watch's minutes next to a phone bucket.
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

    // ---- Count metrics: every source's rows survive ----

    private fun steps(ts: Long, source: String, value: Double, confidence: Int = ConfidenceTier.MEDIUM.level) =
        reading(metricType = MetricType.STEPS.key, value = value, timestamp = ts, sourceId = source, confidence = confidence)

    @Test
    fun `cross-source step buckets at the same minute both survive`() {
        val coros = steps(12_000_000L, "hc-coros", 60.0)
        val fitbit = steps(12_000_000L, "hc-fitbit", 58.0)
        val result = Deduplicator.deduplicate(listOf(coros, fitbit))
        assertEquals(2, result.size)
        assertEquals(setOf("hc-coros", "hc-fitbit"), result.map { it.sourceId }.toSet())
    }

    @Test
    fun `cross-source step buckets a few hundred ms apart both survive`() {
        val hc = steps(12_000_000L, "health_connect", 200.0, ConfidenceTier.HIGH.level)
        val phone = steps(12_000_347L, "phone_sensor", 200.0, ConfidenceTier.LOW.level)
        assertEquals(2, Deduplicator.deduplicate(listOf(hc, phone)).size)
    }

    @Test
    fun `same-source step bucket at the same timestamp collapses to the better confidence`() {
        val low = steps(12_000_000L, "hc-coros", 50.0, ConfidenceTier.LOW.level)
        val high = steps(12_000_000L, "hc-coros", 60.0, ConfidenceTier.HIGH.level)
        val result = Deduplicator.deduplicate(listOf(low, high))
        assertEquals(1, result.size)
        assertEquals(60.0, result[0].value, 0.01)
    }

    @Test
    fun `active calories and active minutes keep per-source rows too`() {
        val a = reading(metricType = MetricType.ACTIVE_CALORIES.key, timestamp = 1L, sourceId = "s1")
        val b = reading(metricType = MetricType.ACTIVE_CALORIES.key, timestamp = 1L, sourceId = "s2")
        val c = reading(metricType = MetricType.ACTIVE_MINUTES.key, timestamp = 1L, sourceId = "s1")
        val d = reading(metricType = MetricType.ACTIVE_MINUTES.key, timestamp = 1L, sourceId = "s2")
        assertEquals(4, Deduplicator.deduplicate(listOf(a, b, c, d)).size)
    }

    // ---- Point-in-time metrics keep the cross-source exact rule ----

    @Test
    fun `heart rate from different sources at near-but-not-equal timestamps both survive`() {
        val a = reading(timestamp = 1000L, sourceId = "s1")
        val b = reading(timestamp = 1300L, sourceId = "s2")
        assertEquals(2, Deduplicator.deduplicate(listOf(a, b)).size)
    }

    @Test
    fun `hrv exact-ms collision across sources still dedupes`() {
        val hrv = MetricType.HEART_RATE_VARIABILITY.key
        val a = reading(metricType = hrv, timestamp = 1000L, sourceId = "s1", confidence = ConfidenceTier.LOW.level)
        val b = reading(metricType = hrv, timestamp = 1000L, sourceId = "s2", confidence = ConfidenceTier.HIGH.level)
        val result = Deduplicator.deduplicate(listOf(a, b))
        assertEquals(1, result.size)
        assertEquals("s2", result[0].sourceId)
    }

    @Test
    fun `only count metrics keep per-source rows`() {
        assertTrue(Deduplicator.keepsPerSource(MetricType.STEPS.key))
        assertTrue(Deduplicator.keepsPerSource(MetricType.ACTIVE_CALORIES.key))
        assertTrue(Deduplicator.keepsPerSource(MetricType.ACTIVE_MINUTES.key))
        assertTrue(!Deduplicator.keepsPerSource(MetricType.HEART_RATE.key))
    }
}
