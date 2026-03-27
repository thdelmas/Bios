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
import com.bios.app.model.MetricType
import com.bios.app.model.PersonalBaseline
import com.bios.app.ui.AppViewModel
import kotlin.math.abs

@Composable
fun MetricCard(
    metricType: MetricType,
    label: String,
    icon: ImageVector,
    viewModel: AppViewModel,
    refreshKey: Any? = null
) {
    var latestValue by remember { mutableStateOf<Double?>(null) }
    var baseline by remember { mutableStateOf<PersonalBaseline?>(null) }

    LaunchedEffect(metricType, refreshKey) {
        latestValue = viewModel.getLatestReading(metricType)?.value
        baseline = viewModel.getBaseline(metricType)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                baseline?.let { bl ->
                    latestValue?.let { v ->
                        DeviationIndicator(v, bl)
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

private fun formatValue(value: Double, metricType: MetricType): String {
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
        else -> String.format("%.1f", value)
    }
}
