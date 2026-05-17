package com.bios.app.ingest

import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType
import kotlin.math.abs

/**
 * Per-metric dedup policy for the ingestion pipeline.
 *
 * Two passes:
 * 1. Exact-match (every metric): readings with identical `(metricType, timestamp)`
 *    collapse to the highest-confidence one. This is the only safe rule for
 *    point-in-time metrics (HR, HRV, RHR, SpO2, sleep stages, biomarkers).
 * 2. Window-overlap (time-window-aggregate metrics only): bucketed counts from
 *    different sources can land with millisecond-to-second clock skew and survive
 *    pass 1, producing inflated `SUM(value)` downstream. Within the metric's
 *    dedup window, different-source readings collapse to the highest-confidence
 *    one. Same-source readings are kept — consecutive buckets from one source
 *    are sequential data, not duplicates.
 *
 * Background: issue #14 — Health Connect StepsRecord and Gadgetbridge step
 * buckets covering the same activity land milliseconds apart and were both
 * being written, doubling step sums for consumers like W2F.
 */
internal object Deduplicator {

    private val WINDOW_MS = mapOf(
        MetricType.STEPS.key to 5L * 60_000L,
        MetricType.ACTIVE_CALORIES.key to 5L * 60_000L,
        MetricType.ACTIVE_MINUTES.key to 5L * 60_000L,
    )

    fun windowMs(metricType: String): Long = WINDOW_MS[metricType] ?: 0L

    fun deduplicate(readings: List<MetricReading>): List<MetricReading> {
        val exactDeduped = exactMatchDedup(readings)
        val windowDeduped = windowOverlapDedup(exactDeduped)
        return windowDeduped.sortedBy { it.timestamp }
    }

    private fun exactMatchDedup(readings: List<MetricReading>): List<MetricReading> {
        val seen = mutableMapOf<String, MetricReading>()
        for (reading in readings) {
            val key = "${reading.metricType}_${reading.timestamp}"
            val existing = seen[key]
            if (existing == null || reading.confidence > existing.confidence) {
                seen[key] = reading
            }
        }
        return seen.values.toList()
    }

    private fun windowOverlapDedup(readings: List<MetricReading>): List<MetricReading> {
        val (windowed, untouched) = readings.partition { windowMs(it.metricType) > 0L }
        if (windowed.isEmpty()) return readings

        val accepted = mutableListOf<MetricReading>()
        for ((metric, group) in windowed.groupBy { it.metricType }) {
            val window = windowMs(metric)
            val sorted = group.sortedWith(
                compareByDescending<MetricReading> { it.confidence }.thenBy { it.timestamp }
            )
            val kept = mutableListOf<MetricReading>()
            for (r in sorted) {
                val collision = kept.any { k ->
                    k.sourceId != r.sourceId && abs(k.timestamp - r.timestamp) <= window
                }
                if (!collision) kept += r
            }
            accepted += kept
        }
        return untouched + accepted
    }
}
