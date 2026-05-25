package com.bios.app.ui.trends

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatStat(value: Double): String = when {
    value >= 1000 -> String.format("%.0f", value)
    value >= 100 -> String.format("%.0f", value)
    else -> String.format("%.1f", value)
}

internal fun formatEntryTimestamp(epochMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(epochMs))
