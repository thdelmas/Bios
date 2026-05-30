package com.bios.app.engine

import com.bios.app.model.MetricReading

/**
 * Median of a non-empty list of doubles (the mean of the two middle elements
 * for even counts).
 *
 * Extracted so [AnomalyDetector]'s absolute-rule evaluation and its tests
 * share one implementation: the home-BP, white-coat-robust convention
 * (ESH 2023 — median over a window, not the mean) is pinned in one place
 * rather than copied into the test.
 *
 * Callers guarantee a non-empty list (the absolute-rule path returns early
 * when fewer than `absoluteMinReadings` are present).
 */
internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
}

/**
 * Keeps only readings whose duration is at least [minDurationSec]. A missing
 * (`null`) duration is treated as zero, so a duration-aware gate never passes
 * a reading that didn't record a length.
 *
 * Extracted so [AnomalyDetector]'s absolute-rule fetch and its tests share the
 * gate: the status_epilepticus_convulsive pattern relies on the ILAE 2015
 * t1 = 300 s convulsive-SE cutoff, and a copy could drift from it silently.
 */
internal fun filterDurationAtLeast(
    rows: List<MetricReading>,
    minDurationSec: Int,
): List<MetricReading> = rows.filter { (it.durationSec ?: 0) >= minDurationSec }
