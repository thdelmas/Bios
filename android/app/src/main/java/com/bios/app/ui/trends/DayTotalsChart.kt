package com.bios.app.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bios.contracts.MetricType
import java.time.LocalDate

/**
 * One bar per calendar bucket for cumulative-daily metrics. The parent
 * owns the data and the selection; this composable only draws and reports
 * taps. Header reads `<bucket label> · <total unit>` for the selected bar
 * (docs/specs/day-totals.md, criterion 4).
 */
@Composable
internal fun DayTotalsChart(
    metric: MetricType,
    state: DayTotalsChartState,
    onSelectWindow: (MetricChartTimeWindow) -> Unit,
    onSelectIndex: (Int) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DayTotalWindowSelector(selected = state.selectedWindow, onSelect = onSelectWindow)
            Spacer(Modifier.height(12.dp))

            if (!state.loaded) {
                CenteredMessage("Loading…")
                return@Column
            }
            if (state.totals.isEmpty()) {
                CenteredMessage("No readings in this window.")
                return@Column
            }

            val selected = state.totals.getOrNull(state.selectedIndex) ?: state.totals.last()
            SelectedBucketHeader(metric, selected, state.unit, today)
            Spacer(Modifier.height(8.dp))
            DayTotalBars(state.totals, state.selectedIndex, onSelectIndex)
            Spacer(Modifier.height(4.dp))
            BucketTicks(totals = state.totals, unit = state.unit, today = today)
        }
    }
}

@Composable
private fun SelectedBucketHeader(
    metric: MetricType,
    selected: DayTotal,
    unit: BucketUnit,
    today: LocalDate,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            bucketLabel(selected.key, unit, today),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            if (selected.hasReadings) formatTotal(selected.total, metric) else "no readings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** The bars themselves; a tap maps x to a slot index. Empty slots draw a baseline tick. */
@Composable
private fun DayTotalBars(
    totals: List<DayTotal>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = barColor.copy(alpha = 0.40f)
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val maxTotal = totals.maxOf { it.total }.coerceAtLeast(1.0)
    val count = totals.size

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .pointerInput(count) {
                detectTapGestures { offset ->
                    val slot = size.width.toFloat() / count
                    onSelectIndex((offset.x / slot).toInt().coerceIn(0, count - 1))
                }
            },
    ) {
        val slot = size.width / count
        val gap = (slot * 0.25f).coerceAtMost(6.dp.toPx())
        val barW = (slot - gap).coerceAtLeast(1f)
        val h = size.height
        totals.forEachIndexed { i, bucket ->
            val x = i * slot + gap / 2
            if (!bucket.hasReadings) {
                drawRect(color = emptyColor, topLeft = Offset(x, h - 2f), size = Size(barW, 2f))
            } else {
                val barH = ((bucket.total / maxTotal) * h).toFloat().coerceAtLeast(3f)
                drawRect(
                    color = if (i == selectedIndex) barColor else dimColor,
                    topLeft = Offset(x, h - barH),
                    size = Size(barW, barH),
                )
            }
        }
    }
}

/** One-letter ticks under up to twelve bars; first and last label when there are more. */
@Composable
private fun BucketTicks(totals: List<DayTotal>, unit: BucketUnit, today: LocalDate) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (totals.size <= 12) {
        Row(modifier = Modifier.fillMaxWidth()) {
            totals.forEach { bucket ->
                Text(
                    bucketTick(bucket.key, unit),
                    style = style,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(bucketLabel(totals.first().key, unit, today), style = style, color = color)
            Text(bucketLabel(totals.last().key, unit, today), style = style, color = color)
        }
    }
}

@Composable
private fun DayTotalWindowSelector(
    selected: MetricChartTimeWindow,
    onSelect: (MetricChartTimeWindow) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dayTotalWindows.forEach { window ->
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
