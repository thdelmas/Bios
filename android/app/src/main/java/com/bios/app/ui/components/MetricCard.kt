package com.bios.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.contracts.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.ui.AppViewModel
import kotlin.math.abs

/**
 * Metrics that arrive as per-window samples but represent a cumulative
 * day count — the card should sum today's values, not show the latest.
 * `internal` so tests can pin the set's contents directly.
 */
internal val cumulativeDailyMetrics = setOf(
    MetricType.STEPS,
    MetricType.ACTIVE_CALORIES,
    MetricType.ACTIVE_MINUTES,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricCard(
    metricType: MetricType,
    label: String,
    icon: ImageVector,
    viewModel: AppViewModel,
    refreshKey: Any? = null,
    onClick: (() -> Unit)? = null
) {
    var latestValue by remember { mutableStateOf<Double?>(null) }
    var baseline by remember { mutableStateOf<PersonalBaseline?>(null) }

    LaunchedEffect(metricType, refreshKey) {
        latestValue = if (metricType in cumulativeDailyMetrics) {
            // Cumulative-daily metrics emit per-sample-window: the latest
            // reading is whatever happened in the last bucket, not today's
            // total. Sum since local midnight instead.
            viewModel.getTodaySum(metricType)
        } else {
            viewModel.getLatestReading(metricType)?.value
        }
        baseline = viewModel.getBaseline(metricType)
    }

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    val modifier = Modifier.fillMaxWidth()
    if (onClick != null) {
        Card(modifier = modifier, onClick = onClick, colors = colors) {
            MetricCardBody(metricType, label, icon, latestValue, baseline)
        }
    } else {
        Card(modifier = modifier, colors = colors) {
            MetricCardBody(metricType, label, icon, latestValue, baseline)
        }
    }
}

@Composable
private fun MetricCardBody(
    metricType: MetricType,
    label: String,
    icon: ImageVector,
    latestValue: Double?,
    baseline: PersonalBaseline?
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            // Suppress the deviation indicator for cumulative-daily metrics:
            // the value shown is today's sum but the baseline is computed
            // from per-window readings — z-score across the two is nonsense.
            // A daily-sum baseline is a future enhancement.
            if (metricType !in cumulativeDailyMetrics) {
                baseline?.let { bl ->
                    latestValue?.let { v ->
                        DeviationIndicator(v, bl)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = latestValue?.let { formatValue(it, metricType) } ?: "--",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviationIndicator(value: Double, baseline: PersonalBaseline) {
    val z = baseline.zScore(value)
    val (text, color) = when {
        abs(z) < 1.0 -> "OK" to Color(0xFF4CAF50)
        abs(z) < 2.0 -> String.format("%.1fσ", z) to Color(0xFFFFC107)
        else -> String.format("%.1fσ", z) to Color(0xFFFF9800)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

internal fun formatValue(value: Double, metricType: MetricType): String {
    return when (metricType) {
        MetricType.HEART_RATE, MetricType.RESTING_HEART_RATE, MetricType.RESPIRATORY_RATE ->
            "${value.toInt()}"
        MetricType.HEART_RATE_VARIABILITY ->
            "${value.toInt()} ms"
        MetricType.BLOOD_OXYGEN ->
            "${(value * 100).toInt()}%"
        MetricType.STEPS ->
            "${value.toInt()}"
        MetricType.SKIN_TEMPERATURE_DEVIATION -> {
            val sign = if (value >= 0) "+" else ""
            "$sign${String.format("%.1f", value)}°"
        }
        MetricType.SLEEP_DURATION -> {
            val totalMinutes = (value / 60).toInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            "${hours}h ${minutes}m"
        }
        else -> String.format("%.1f", value)
    }
}
