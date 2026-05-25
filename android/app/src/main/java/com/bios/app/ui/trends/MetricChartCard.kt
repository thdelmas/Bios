package com.bios.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.ui.RecentEntry
import com.bios.contracts.MetricType

@Composable
internal fun MetricChart(
    metric: MetricType,
    entries: List<RecentEntry>,
    loaded: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!loaded) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            if (entries.size < 2) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (entries.isEmpty()) "No readings yet."
                        else "Need at least two readings to chart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            val sorted = entries.sortedBy { it.reading.timestamp }
            val values = sorted.map { it.reading.value }
            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).coerceAtLeast(1e-6)
            val firstTs = sorted.first().reading.timestamp
            val lastTs = sorted.last().reading.timestamp
            val spanMs = (lastTs - firstTs).coerceAtLeast(1L).toDouble()
            val unit = metric.unit.symbol.ifEmpty { "" }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Last ${sorted.size} readings",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    buildString {
                        append(formatStat(values.last()))
                        if (unit.isNotEmpty()) {
                            append(' ')
                            append(unit)
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            val lineColor = MaterialTheme.colorScheme.primary
            val pointColor = MaterialTheme.colorScheme.tertiary
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
                    val a = sorted[i - 1].reading
                    val b = sorted[i].reading
                    drawLine(
                        color = lineColor,
                        start = Offset(xFor(a.timestamp), yFor(a.value)),
                        end = Offset(xFor(b.timestamp), yFor(b.value)),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
                for (entry in sorted) {
                    drawCircle(
                        color = pointColor,
                        radius = 3f,
                        center = Offset(xFor(entry.reading.timestamp), yFor(entry.reading.value)),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "min ${formatStat(minV)}${if (unit.isNotEmpty()) " $unit" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatEntryTimestamp(firstTs) + " → " + formatEntryTimestamp(lastTs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "max ${formatStat(maxV)}${if (unit.isNotEmpty()) " $unit" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
