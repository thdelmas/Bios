package com.bios.app.ui.trends

import com.bios.contracts.MetricUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatStat(value: Double): String = when {
    value >= 1000 -> String.format("%.0f", value)
    value >= 100 -> String.format("%.0f", value)
    else -> String.format("%.1f", value)
}

/**
 * Owner-facing rendering of a metric value with its unit symbol. Time-based
 * metrics (MetricUnit.SECONDS) format as "Xh Ym" / "Mm" / "Ss" depending on
 * magnitude — owners read "4h 30m" not "16200 s". Other units fall through to
 * `formatStat(value) [symbol]`.
 */
internal fun formatMetricValue(value: Double, unit: MetricUnit): String = when (unit) {
    MetricUnit.SECONDS -> formatSecondsHuman(value)
    else -> {
        val symbol = unit.symbol
        if (symbol.isEmpty()) formatStat(value) else "${formatStat(value)} $symbol"
    }
}

private fun formatSecondsHuman(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    if (total < 60) return "${total}s"
    val minutes = total / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
}

internal fun formatEntryTimestamp(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
