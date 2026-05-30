package com.bios.app.engine

import com.bios.app.model.TrendDirection

/**
 * Linear-regression trend over a series of daily means.
 *
 * Extracted to a top-level function so [BaselineEngine] and its unit tests
 * call the *same* implementation — the math can be pinned without
 * constructing a BiosDatabase, and a regression here can't hide behind a
 * test-local copy.
 *
 * Returns the [TrendDirection] (RISING / FALLING / STABLE, using a ±2 %
 * normalized-slope deadband) paired with the raw regression slope. A series
 * shorter than three points, a degenerate regression, or a zero mean all
 * resolve to STABLE.
 */
internal fun computeTrend(dailyMeans: List<Double>): Pair<TrendDirection, Double> {
    if (dailyMeans.size < 3) return Pair(TrendDirection.STABLE, 0.0)

    val n = dailyMeans.size.toDouble()
    val xs = (0 until dailyMeans.size).map { it.toDouble() }
    val sumX = xs.sum()
    val sumY = dailyMeans.sum()
    val sumXY = xs.zip(dailyMeans).sumOf { (x, y) -> x * y }
    val sumX2 = xs.sumOf { it * it }

    val denominator = n * sumX2 - sumX * sumX
    if (denominator == 0.0) return Pair(TrendDirection.STABLE, 0.0)

    val slope = (n * sumXY - sumX * sumY) / denominator

    val mean = sumY / n
    if (mean == 0.0) return Pair(TrendDirection.STABLE, slope)

    val normalizedSlope = slope / mean

    val direction = when {
        normalizedSlope > 0.02 -> TrendDirection.RISING
        normalizedSlope < -0.02 -> TrendDirection.FALLING
        else -> TrendDirection.STABLE
    }

    return Pair(direction, slope)
}
