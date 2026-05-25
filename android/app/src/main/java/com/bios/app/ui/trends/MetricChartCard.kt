package com.bios.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.MetricReading
import com.bios.contracts.MetricType

/**
 * Per-metric line chart with a Day / Week / Month / Year / All
 * time-window selector. The owner picks the range; this composable
 * renders whatever the parent fetched for that range. The "Recent
 * entries" list below the chart uses its own row-limit fetch and is
 * unaffected by the window.
 */
@Composable
internal fun MetricChart(
    metric: MetricType,
    readings: List<MetricReading>,
    loaded: Boolean,
    selectedWindow: MetricChartTimeWindow,
    onSelectWindow: (MetricChartTimeWindow) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TimeWindowSelector(selected = selectedWindow, onSelect = onSelectWindow)
            Spacer(Modifier.height(12.dp))

            if (!loaded) {
                CenteredMessage("Loading…")
                return@Column
            }

            if (readings.size < 2) {
                CenteredMessage(
                    if (readings.isEmpty()) "No readings in this window."
                    else "Need at least two readings to chart."
                )
                return@Column
            }

            val sorted = readings.sortedBy { it.timestamp }
            val values = sorted.map { it.value }
            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).coerceAtLeast(1e-6)
            val firstTs = sorted.first().timestamp
            val lastTs = sorted.last().timestamp
            val spanMs = (lastTs - firstTs).coerceAtLeast(1L).toDouble()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${sorted.size} readings",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    formatMetricValue(values.last(), metric.unit),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            val lineColor = MaterialTheme.colorScheme.primary
            val pointColor = MaterialTheme.colorScheme.tertiary
            // Skip per-point dots when the sample count is high — they
            // turn into a dense band that hides the line trend.
            val showPoints = sorted.size <= POINT_DOT_THRESHOLD
            Canvas(
                modifier = Modifier.fillMaxWidth().height(120.dp),
            ) {
                val w = size.width
                val h = size.height
                val padY = 4f
                val usableH = h - 2 * padY
                fun yFor(v: Double): Float =
                    h - padY - ((v - minV) / range).toFloat() * usableH
                fun xFor(t: Long): Float =
                    ((t - firstTs) / spanMs).toFloat() * w

                for (i in 1 until sorted.size) {
                    val a = sorted[i - 1]
                    val b = sorted[i]
                    drawLine(
                        color = lineColor,
                        start = Offset(xFor(a.timestamp), yFor(a.value)),
                        end = Offset(xFor(b.timestamp), yFor(b.value)),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
                if (showPoints) {
                    for (entry in sorted) {
                        drawCircle(
                            color = pointColor,
                            radius = 3f,
                            center = Offset(xFor(entry.timestamp), yFor(entry.value)),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "min ${formatMetricValue(minV, metric.unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatEntryTimestamp(firstTs) + " → " + formatEntryTimestamp(lastTs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "max ${formatMetricValue(maxV, metric.unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimeWindowSelector(
    selected: MetricChartTimeWindow,
    onSelect: (MetricChartTimeWindow) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricChartTimeWindow.entries.forEach { window ->
            FilterChip(
                selected = window == selected,
                onClick = { onSelect(window) },
                label = { Text(window.label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Above this point count, per-sample dots clutter the trend line. */
private const val POINT_DOT_THRESHOLD = 60
