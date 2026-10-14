package com.bios.app.ingest

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Per-metric dedup policy for the ingestion pipeline.
 *
 * Point-in-time metrics (HR, HRV, RHR, SpO2, sleep stages, biomarkers):
 * readings with identical `(metricType, timestamp)` collapse to the
 * highest-confidence one, whatever their source. Two feeds mirroring the
 * same wearable at the same millisecond are one reading.
 *
 * Count metrics (steps, active calories, active minutes) keep one row per
 * `(metricType, timestamp, sourceId)`: every witness's count is stored and
 * the day total picks one witness per calendar day at read time
 * (docs/specs/source-preference.md). The earlier cross-source window pass
 * (issue #14) is gone: with COROS, Fitbit and the phone each writing
 * per-minute buckets, any in-batch collapse between sources would delete
 * the watch's minutes whenever a phone bucket landed nearby.
 */
internal object Deduplicator {

    private val PER_SOURCE_METRICS = setOf(
        MetricType.STEPS.key,
        MetricType.ACTIVE_CALORIES.key,
        MetricType.ACTIVE_MINUTES.key,
    )

    /** True for metrics whose rows are kept per source (count metrics). */
    fun keepsPerSource(metricType: String): Boolean = metricType in PER_SOURCE_METRICS

    fun deduplicate(readings: List<MetricReading>): List<MetricReading> {
        val seen = mutableMapOf<String, MetricReading>()
        for (reading in readings) {
            val key = if (keepsPerSource(reading.metricType)) {
                "${reading.metricType}_${reading.timestamp}_${reading.sourceId}"
            } else {
                "${reading.metricType}_${reading.timestamp}"
            }
            val existing = seen[key]
            if (existing == null || reading.confidence > existing.confidence) {
                seen[key] = reading
            }
        }
        return seen.values.sortedBy { it.timestamp }
    }
}
