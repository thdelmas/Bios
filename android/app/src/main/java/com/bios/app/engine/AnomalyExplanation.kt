package com.bios.app.engine

import com.bios.contracts.MetricType
import kotlin.math.abs

/**
 * Builds the owner-facing explanation for a detected anomaly: each metric's
 * signed z-score deviation phrased in plain language, ordered by magnitude,
 * then the pattern's own explanation.
 *
 * Extracted to a top-level function so [AnomalyDetector] and its unit tests
 * share one implementation — the phrasing and ordering can be pinned without
 * constructing the full detector, and the zero-deviation filter below can't
 * silently drift between production and test.
 *
 * Rules carried in with z = 0 are ABSENT-direction sentinels (no event in the
 * window) and don't fit the deviation template, so they're dropped here and
 * left to [patternExplanation].
 */
internal fun buildAnomalyExplanation(
    patternExplanation: String,
    deviations: Map<MetricType, Double>,
): String {
    val parts = deviations.entries
        .filter { it.value != 0.0 }
        .sortedByDescending { abs(it.value) }
        .map { (metric, zScore) ->
            val direction = if (zScore > 0) "above" else "below"
            val sigmas = String.format("%.1f", abs(zScore))
            "Your ${metric.readableName.lowercase()} is $sigmas standard deviations $direction your personal baseline."
        }
        .toMutableList()

    parts.add(patternExplanation)
    return parts.joinToString(" ")
}
