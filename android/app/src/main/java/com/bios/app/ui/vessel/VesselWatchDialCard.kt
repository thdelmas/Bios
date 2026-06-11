package com.bios.app.ui.vessel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricUnit
import kotlin.math.roundToInt

private const val SPARKLINE_WINDOW_DAYS = 90L
private const val SPARKLINE_MAX_POINTS = 60

/**
 * One dial. Self-fetches its own latest value + short history in a
 * [LaunchedEffect] (the same decentralized pattern as the home MetricCard),
 * then renders one of three honest states: a value with its own trend, an
 * explicit "no reading yet" gap with the fill hint, or — for not-yet-
 * instrumented dials — a named PLANNED gap. Nothing here reads or shows any
 * other dial; there is no cross-dial aggregation.
 */
@Composable
fun VesselWatchDialCard(
    dial: VesselDial,
    viewModel: AppViewModel,
    refreshKey: Long?,
) {
    var primary by remember(dial) { mutableStateOf<Double?>(null) }
    var secondary by remember(dial) { mutableStateOf<Double?>(null) }
    var history by remember(dial) { mutableStateOf<List<MetricReading>>(emptyList()) }

    LaunchedEffect(dial, refreshKey) {
        val key = dial.primaryKey ?: return@LaunchedEffect
        primary = viewModel.getLatestReading(key)?.value
        secondary = dial.secondaryKey?.let { viewModel.getLatestReading(it)?.value }
        val now = System.currentTimeMillis()
        val start = now - SPARKLINE_WINDOW_DAYS * 24 * 3600 * 1000L
        history = viewModel.db.metricReadingDao().fetch(key.key, start, now)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dial.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                StateChip(dial = dial, hasValue = primary != null)
            }

            if (primary != null) {
                Text(
                    formatDialValue(dial, primary!!, secondary),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (history.size >= 2) {
                    Sparkline(
                        readings = history,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                dial.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * State label per dial. Colour stays low-key on purpose: this is a surface
 * the owner reads, not a scoreboard. Empty dials are never red-alarmed — a
 * missing reading is a gap to fill, not a failure.
 */
@Composable
private fun StateChip(dial: VesselDial, hasValue: Boolean) {
    val (label, color) = when (dial.availability) {
        DialAvailability.INSTRUMENTED ->
            if (hasValue) "Live" to MaterialTheme.colorScheme.primary
            else "Awaiting sync" to MaterialTheme.colorScheme.onSurfaceVariant
        DialAvailability.DERIVED_ON_DEMAND ->
            if (hasValue) "On demand" to MaterialTheme.colorScheme.primary
            else "Run a capture" to MaterialTheme.colorScheme.onSurfaceVariant
        DialAvailability.NEEDS_ENTRY ->
            if (hasValue) "Entered" to MaterialTheme.colorScheme.primary
            else "Add" to MaterialTheme.colorScheme.onSurfaceVariant
        DialAvailability.PLANNED ->
            "Planned" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

/** Bare trend line for a single dial's own history. No axes, no score. */
@Composable
private fun Sparkline(readings: List<MetricReading>, color: Color) {
    val sorted = remember(readings) { readings.sortedBy { it.timestamp } }
    val downsampled = remember(sorted) { downsample(sorted, SPARKLINE_MAX_POINTS) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        val values = downsampled.map { it.value }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val firstTs = downsampled.first().timestamp
        val spanMs = (downsampled.last().timestamp - firstTs).takeIf { it > 0L } ?: 1L
        val padY = 3f
        val usableH = size.height - 2 * padY

        fun xFor(t: Long): Float = ((t - firstTs).toDouble() / spanMs).toFloat() * size.width
        fun yFor(v: Double): Float = size.height - padY - ((v - minV) / range).toFloat() * usableH

        for (i in 1 until downsampled.size) {
            val a = downsampled[i - 1]
            val b = downsampled[i]
            drawLine(
                color = color,
                start = Offset(xFor(a.timestamp), yFor(a.value)),
                end = Offset(xFor(b.timestamp), yFor(b.value)),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun downsample(readings: List<MetricReading>, maxPoints: Int): List<MetricReading> {
    if (readings.size <= maxPoints) return readings
    val step = readings.size.toDouble() / maxPoints
    return (0 until maxPoints).map { readings[(it * step).toInt()] }
}

/** Owner-facing value string. Blood pressure renders as systolic/diastolic. */
private fun formatDialValue(dial: VesselDial, primary: Double, secondary: Double?): String {
    val unit = dial.primaryKey?.unit
    if (dial.secondaryKey != null && secondary != null) {
        val sym = unit?.symbol.orEmpty()
        return "${primary.roundToInt()}/${secondary.roundToInt()}${if (sym.isEmpty()) "" else " $sym"}"
    }
    if (unit == MetricUnit.SECONDS) return formatDuration(primary)
    val rounded = (primary * 10).roundToInt() / 10.0
    val number = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    val sym = unit?.symbol.orEmpty()
    return if (sym.isEmpty()) number else "$number $sym"
}

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
