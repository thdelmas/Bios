package com.bios.app.ui.trends

/**
 * Time-window choices for the per-metric chart on the trends dashboard.
 * Controls only the chart fetch — the "Recent entries" list below it
 * stays on its own row-limit fetch, unchanged.
 *
 * `ALL` queries from epoch to now; the DAO range filter is inclusive so
 * passing `0L` as start safely returns every reading on file.
 */
enum class MetricChartTimeWindow(val label: String, val durationMillis: Long?) {
    HOUR("Hour", MILLIS_PER_HOUR),
    DAY("Day", 24L * MILLIS_PER_HOUR),
    WEEK("Week", 7L * 24L * MILLIS_PER_HOUR),
    MONTH("Month", 30L * 24L * MILLIS_PER_HOUR),
    YEAR("Year", 365L * 24L * MILLIS_PER_HOUR),
    ALL("All", null);

    /** Inclusive lower-bound timestamp (epoch ms) for this window. */
    fun startMillis(now: Long = System.currentTimeMillis()): Long =
        durationMillis?.let { now - it } ?: 0L

    companion object {
        /** Default window — small enough to feel responsive on the first paint. */
        val DEFAULT: MetricChartTimeWindow = WEEK

        /**
         * Soft ceiling on how many points the line chart renders. Above
         * this we sample uniformly; Canvas can draw thousands of segments
         * but the visual turns into spaghetti and the GPU bill climbs.
         */
        const val MAX_CHART_POINTS: Int = 500
    }
}

private const val MILLIS_PER_HOUR: Long = 60L * 60L * 1000L

/**
 * Uniformly subsample [readings] when the count exceeds
 * [MetricChartTimeWindow.MAX_CHART_POINTS]. Preserves first + last
 * samples so the rendered range still matches the underlying data.
 * Returns the input unchanged when it's already small enough.
 */
fun <T> downsampleForChart(readings: List<T>): List<T> {
    val max = MetricChartTimeWindow.MAX_CHART_POINTS
    if (readings.size <= max) return readings
    val stride = readings.size.toDouble() / max
    val out = ArrayList<T>(max)
    var i = 0.0
    while (i < readings.size && out.size < max - 1) {
        out += readings[i.toInt()]
        i += stride
    }
    out += readings.last()
    return out
}
