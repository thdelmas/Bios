package com.bios.app.engine

import kotlin.math.sqrt

data class Stats(
    val mean: Double,
    val median: Double,
    val min: Double,
    val max: Double,
    val stdDev: Double,
    val p5: Double,
    val p95: Double
) {
    companion object {
        fun compute(values: List<Double>): Stats {
            if (values.isEmpty()) return Stats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

            val sorted = values.sorted()
            val count = values.size.toDouble()
            val mean = values.sum() / count
            val variance = values.sumOf { (it - mean) * (it - mean) } / count
            val stdDev = sqrt(variance)

            return Stats(
                mean = mean,
                median = percentile(sorted, 0.50),
                min = sorted.first(),
                max = sorted.last(),
                stdDev = stdDev,
                p5 = percentile(sorted, 0.05),
                p95 = percentile(sorted, 0.95)
            )
        }

        private fun percentile(sorted: List<Double>, p: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val index = p * (sorted.size - 1)
            val lower = index.toInt()
            val upper = (lower + 1).coerceAtMost(sorted.size - 1)
            val fraction = index - lower
            return sorted[lower] + fraction * (sorted[upper] - sorted[lower])
        }
    }
}
