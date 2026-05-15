package com.bios.app.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.contracts.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.model.TrendDirection
import com.bios.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    viewModel: AppViewModel,
    initialMetric: MetricType? = null
) {
    val baselines by viewModel.baselines.collectAsState()
    val coverage by viewModel.metricCoverage.collectAsState()
    var selectedMetric by remember(initialMetric) {
        mutableStateOf(initialMetric ?: MetricType.HEART_RATE)
    }

    val trackableMetrics = listOf(
        MetricType.HEART_RATE to "Heart Rate",
        MetricType.HEART_RATE_VARIABILITY to "HRV",
        MetricType.RESTING_HEART_RATE to "Resting HR",
        MetricType.BLOOD_OXYGEN to "SpO2",
        MetricType.RESPIRATORY_RATE to "Resp. Rate",
        MetricType.STEPS to "Steps",
        MetricType.SKIN_TEMPERATURE_DEVIATION to "Skin Temp",
        MetricType.SLEEP_DURATION to "Sleep",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trends", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        // Metric picker
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            trackableMetrics.forEach { (metric, name) ->
                FilterChip(
                    selected = selectedMetric == metric,
                    onClick = { selectedMetric = metric },
                    label = { Text(name) }
                )
            }
        }

        // Baseline summary
        val selectedBaseline = baselines.find { it.metricType == selectedMetric.key }

        if (selectedBaseline != null) {
            BaselineSummaryCard(selectedBaseline)
        } else {
            val metricLabel = trackableMetrics.firstOrNull { it.first == selectedMetric }?.second
                ?: selectedMetric.readableName
            NoBaselineCard(metricLabel, coverage[selectedMetric])
        }

        // All baselines
        if (baselines.isNotEmpty()) {
            Text("All Baselines", style = MaterialTheme.typography.titleMedium)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    baselines.forEachIndexed { index, baseline ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                MetricType.fromKey(baseline.metricType)?.readableName ?: baseline.metricType,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    formatStat(baseline.mean),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TrendBadge(TrendDirection.valueOf(baseline.trend))
                            }
                        }
                        if (index < baselines.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BaselineSummaryCard(baseline: PersonalBaseline) {
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
                StatCell("Mean", formatStat(baseline.mean))
                StatCell("Std Dev", formatStat(baseline.stdDev))
                StatCell("Range", "${formatStat(baseline.p5)} - ${formatStat(baseline.p95)}")
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

private fun formatStat(value: Double): String {
    return when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        else -> String.format("%.1f", value)
    }
}
