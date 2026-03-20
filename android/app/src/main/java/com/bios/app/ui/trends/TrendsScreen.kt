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
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.model.TrendDirection
import com.bios.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(viewModel: AppViewModel) {
    val baselines by viewModel.baselines.collectAsState()
    var selectedMetric by remember { mutableStateOf(MetricType.HEART_RATE) }

    val trackableMetrics = listOf(
        MetricType.HEART_RATE to "Heart Rate",
        MetricType.HEART_RATE_VARIABILITY to "HRV",
        MetricType.RESTING_HEART_RATE to "Resting HR",
        MetricType.BLOOD_OXYGEN to "SpO2",
        MetricType.RESPIRATORY_RATE to "Resp. Rate",
        MetricType.STEPS to "Steps",
        MetricType.SKIN_TEMPERATURE_DEVIATION to "Skin Temp",
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
            NoBaselineCard()
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
fun NoBaselineCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No baseline yet", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios needs at least ${BaselineEngine.MINIMUM_DATA_DAYS} days of data to compute a baseline for this metric.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatStat(value: Double): String {
    return when {
        value >= 1000 -> String.format("%.0f", value)
        value >= 100 -> String.format("%.0f", value)
        else -> String.format("%.1f", value)
    }
}
