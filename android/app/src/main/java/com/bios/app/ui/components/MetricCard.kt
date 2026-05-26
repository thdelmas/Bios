package com.bios.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
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
import com.bios.app.ui.theme.BiosTokens
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
    onClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    var latestValue by remember { mutableStateOf<Double?>(null) }
    var latestTimestamp by remember { mutableStateOf<Long?>(null) }
    var baseline by remember { mutableStateOf<PersonalBaseline?>(null) }

    LaunchedEffect(metricType, refreshKey) {
        if (metricType in cumulativeDailyMetrics) {
            // Cumulative-daily metrics emit per-sample-window: the latest
            // reading is whatever happened in the last bucket, not today's
            // total. Sum since local midnight instead. Age would only mean
            // "last bucket received," not "data freshness," so suppress it.
            latestValue = viewModel.getTodaySum(metricType)
            latestTimestamp = null
        } else {
            val reading = viewModel.getLatestReading(metricType)
            latestValue = reading?.value
            latestTimestamp = reading?.timestamp
        }
        baseline = viewModel.getBaseline(metricType)
    }

    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    val modifier = Modifier.fillMaxWidth()
    if (onClick != null) {
        Card(modifier = modifier, onClick = onClick, colors = colors) {
            MetricCardBody(metricType, label, icon, latestValue, latestTimestamp, baseline, onInfoClick)
        }
    } else {
        Card(modifier = modifier, colors = colors) {
            MetricCardBody(metricType, label, icon, latestValue, latestTimestamp, baseline, onInfoClick)
        }
    }
}

@Composable
private fun MetricCardBody(
    metricType: MetricType,
    label: String,
    icon: ImageVector,
    latestValue: Double?,
    latestTimestamp: Long?,
    baseline: PersonalBaseline?,
    onInfoClick: (() -> Unit)?,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                if (onInfoClick != null && MetricExplanations.forMetric(metricType) != null) {
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "About this metric",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val ageMillis = latestTimestamp?.let { System.currentTimeMillis() - it }
        val stale = ageMillis != null && isStale(metricType, ageMillis)

        Text(
            text = latestValue?.let { formatValue(it, metricType) } ?: "--",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (stale) MaterialTheme.colorScheme.onSurfaceVariant
                    else Color.Unspecified
        )

        val ageText = ageMillis?.let { formatRelativeTime(it) }
        Text(
            text = when {
                stale && ageText != null -> "$label · $ageText — open your watch app to refresh"
                ageText != null -> "$label · $ageText"
                else -> label
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviationIndicator(value: Double, baseline: PersonalBaseline) {
    val z = baseline.zScore(value)
    val abs = abs(z)
    val text = if (abs < 1.0) "OK" else String.format("%.1fσ", z)
    val color = when {
        abs < 1.0 -> BiosTokens.success
        abs < 2.0 -> BiosTokens.warning
        else -> BiosTokens.attention
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

/**
 * How old a reading can be before it stops being treated as "live" on the
 * Read tab. Null means no staleness check — daily-rhythm metrics (sleep,
 * skin-temp deviation) are inherently 12-36h old by design and shouldn't be
 * dimmed for it. Returns the threshold in milliseconds.
 *
 * Why these specific metrics: vital signs that wearables emit continuously
 * (HR, HRV, respiration, SpO2) should reflect the last sync window — if
 * they don't, the upstream companion app isn't syncing and the displayed
 * value is misleading rather than informative.
 *
 * Why 30 min: typical wearable companion apps (Coros, Garmin) batch their
 * Health Connect writes every ~20 min when allowed to wake in background.
 * A 10-min threshold tripped on nearly every reading and turned the nudge
 * into noise; 30 min lets the normal cadence breathe but still catches the
 * "hours stale" cases the threshold is actually for.
 */
internal fun staleThresholdMillis(metricType: MetricType): Long? = when (metricType) {
    MetricType.HEART_RATE,
    MetricType.HEART_RATE_VARIABILITY,
    MetricType.RESPIRATORY_RATE,
    MetricType.BLOOD_OXYGEN -> 30L * 60_000L
    else -> null
}

internal fun isStale(metricType: MetricType, ageMillis: Long): Boolean {
    val threshold = staleThresholdMillis(metricType) ?: return false
    return ageMillis > threshold
}

/**
 * Reading age, in the smallest unit that's still legible.
 * `ageMillis` may be negative if a sample's timestamp is slightly in the
 * future (clock skew between Coros app and phone) — treat as "just now".
 */
internal fun formatRelativeTime(ageMillis: Long): String {
    val seconds = ageMillis / 1000
    if (seconds < 60) return "just now"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

internal fun formatValue(value: Double, metricType: MetricType): String {
    return when (metricType) {
        MetricType.HEART_RATE, MetricType.RESTING_HEART_RATE, MetricType.RESPIRATORY_RATE ->
            "${value.toInt()}"
        MetricType.HEART_RATE_VARIABILITY ->
            "${value.toInt()} ms"
        MetricType.BLOOD_OXYGEN ->
            "${value.toInt()}%"
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
        MetricType.SLEEP_EFFICIENCY ->
            "${value.toInt()}%"
        else -> String.format("%.1f", value)
    }
}
