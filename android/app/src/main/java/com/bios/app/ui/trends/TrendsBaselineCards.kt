package com.bios.app.ui.trends

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.model.PersonalBaseline
import com.bios.app.model.TrendDirection
import com.bios.contracts.MetricUnit

@Composable
fun BaselineSummaryCard(baseline: PersonalBaseline, unit: MetricUnit) {
    val trend = TrendDirection.valueOf(baseline.trend)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Personal Baseline", style = MaterialTheme.typography.titleSmall)
                TrendBadge(trend)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell("Mean", formatMetricValue(baseline.mean, unit))
                StatCell("Std Dev", formatMetricValue(baseline.stdDev, unit))
                StatCell("Range", "${formatMetricValue(baseline.p5, unit)} – ${formatMetricValue(baseline.p95, unit)}")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Trend: ${baseline.trend.lowercase()} (${String.format("%+.2f", baseline.trendSlope)}/day) | Window: ${baseline.windowDays} days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrendBadge(trend: TrendDirection) {
    val (arrow, color) = when (trend) {
        TrendDirection.RISING -> "↗" to MaterialTheme.colorScheme.error
        TrendDirection.STABLE -> "→" to MaterialTheme.colorScheme.primary
        TrendDirection.FALLING -> "↘" to MaterialTheme.colorScheme.tertiary
    }
    Text(arrow, color = color, fontWeight = FontWeight.Bold)
}

@Composable
fun NoBaselineCard(
    metricLabel: String,
    coverage: BaselineEngine.Coverage?
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val (title, body) = noBaselineMessage(metricLabel, coverage)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun noBaselineMessage(
    metricLabel: String,
    coverage: BaselineEngine.Coverage?
): Pair<String, String> {
    if (coverage == null || !coverage.hasAnyData) {
        return "No $metricLabel data yet" to
            "Bios hasn't received any $metricLabel readings. " +
            "Check that your watch is sending $metricLabel to Health Connect, " +
            "or that the source you use (Gadgetbridge, Oura, etc.) is connected and syncing."
    }
    // Owner has data, but every row is self-reported (zero sensor rows in the
    // baseline window). Baselines stay sensor-only by design
    // (docs/SELF_REPORTED_DATA_HOME.md decision 3) — say that plainly instead
    // of pretending there's no data at all.
    if (coverage.sensorSamplesInWindow == 0 && coverage.totalSamples > 0) {
        val n = coverage.totalSamples
        return "$metricLabel — manual entries only" to
            "You have $n manual ${if (n == 1) "entry" else "entries"} on file. " +
            "Bios computes baselines from sensor data only — your entries still " +
            "show in trends and exports, but they don't drive the baseline math."
    }
    val staleHours = coverage.lastTimestamp?.let {
        (System.currentTimeMillis() - it) / (3600L * 1000)
    } ?: Long.MAX_VALUE
    if (staleHours >= 48) {
        val days = (staleHours / 24).toInt()
        return "$metricLabel feed looks stale" to
            "Last $metricLabel reading was $days day${if (days == 1) "" else "s"} ago. " +
            "Bios has ${coverage.totalSamples} reading${if (coverage.totalSamples == 1) "" else "s"} on file, " +
            "but the source seems to have stopped syncing."
    }
    if (!coverage.meetsBaselineMinimum) {
        return "Building $metricLabel baseline" to
            "Bios has ${coverage.sensorSamplesInWindow} sensor reading${if (coverage.sensorSamplesInWindow == 1) "" else "s"} " +
            "in the last ${BaselineEngine.DEFAULT_WINDOW_DAYS} days. " +
            "Need at least ${BaselineEngine.MIN_SAMPLES_FOR_BASELINE} to compute a baseline."
    }
    return "Baseline pending" to
        "Bios has ${coverage.sensorSamplesInWindow} recent $metricLabel readings. " +
        "Baseline will compute on the next sync."
}
