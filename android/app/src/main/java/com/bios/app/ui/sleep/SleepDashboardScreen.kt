package com.bios.app.ui.sleep

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.SleepRegularityCalculator
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pull-side sleep dashboard (issue #135 — v1).
 *
 * Visualizes Bios's canonical sleep data without ever pushing a score,
 * nudge, or coaching loop at the owner — the manifesto's "instrument,
 * not coach" stance applied to the sleep surface. The owner picks a
 * window, the dashboard renders the trend; evaluation belongs to the
 * owner.
 *
 * v1 surfaces: window selector, regularity panel (composite + bedtime/
 * wake/midpoint variance), per-night duration list with source +
 * confidence provenance. Out of scope for v1 (separate follow-ups):
 * bedtime/wake heatmap, per-night stage breakdown, correlation prompts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDashboardScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLogSleepManually: () -> Unit = {},
) {
    var windowDays by remember { mutableStateOf(7) }
    var nights by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var sourceLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(windowDays) {
        val end = System.currentTimeMillis()
        val start = end - windowDays.toLong() * 24L * 3600L * 1000L
        nights = viewModel.db.metricReadingDao()
            .fetch(MetricType.SLEEP_DURATION.key, start, end)
            .sortedByDescending { it.timestamp }
        sourceLabels = viewModel.db.dataSourceDao().getAll()
            .associate { it.id to (it.deviceName ?: it.sourceType) }
    }

    val regularity = remember(nights, windowDays) {
        SleepRegularityCalculator.derive(
            nights, sourceId = "dashboard-preview", windowDays = windowDays
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { WindowSelector(windowDays) { windowDays = it } }
            item { RegularityCard(regularity) }
            item { SummaryCard(nights) }
            item {
                Text(
                    "Recent nights",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (nights.isEmpty()) {
                item { EmptySleepCard(onLogSleepManually) }
            } else {
                items(nights, key = { it.id }) { night ->
                    NightRow(night, sourceLabels)
                }
            }
        }
    }
}

/**
 * Empty-state guidance for the sleep dashboard. Explicit about every
 * way the bus can populate so phone-only owners aren't left guessing:
 * a wearable / Health Connect feed, the W2F companion (screen-off
 * inference), or a manual entry through the existing
 * `sleep_entry` route. The CTA opens the manual-entry flow directly
 * since that's the only path the owner can act on without leaving
 * Bios. Phone-side auto-derivation (`PhoneSleepAdapter` from #134)
 * lands separately — the orchestration worker is the next layer.
 */
@Composable
private fun EmptySleepCard(onLogSleepManually: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "No sleep readings yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Bios renders sleep that lands on its bus. Any of these " +
                    "fills the dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "• Connect a wearable (Oura, WHOOP, Garmin, Withings, Polar)" +
                    "\n• Enable Health Connect with a sleep-tracking app" +
                    "\n• Install W2F with the Bios integration toggled on " +
                    "(screen-off inference)" +
                    "\n• Log a night manually below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onLogSleepManually,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log a sleep night")
            }
        }
    }
}

@Composable
private fun WindowSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(7, 30, 90).forEach { days ->
            FilterChip(
                selected = selected == days,
                onClick = { onSelect(days) },
                label = { Text("${days}d") }
            )
        }
    }
}

@Composable
private fun RegularityCard(result: SleepRegularityCalculator.Result?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Regularity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (result == null) {
                Text(
                    "Need at least ${SleepRegularityCalculator.MIN_NIGHTS} nights in the window " +
                        "to compute a regularity score.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val bedtime = payloadLong(result, SleepRegularityCalculator.Fields.BEDTIME_VARIANCE_MIN)
            val wake = payloadLong(result, SleepRegularityCalculator.Fields.WAKE_VARIANCE_MIN)
            val midpoint = payloadLong(result, SleepRegularityCalculator.Fields.MIDPOINT_VARIANCE_MIN)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell("Score", String.format(Locale.US, "%.0f", result.reading.value))
                StatCell("Bedtime σ", "${bedtime}m")
                StatCell("Midpoint σ", "${midpoint}m")
                StatCell("Wake σ", "${wake}m")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Score keys off midpoint variance. " +
                    "Lower variance across the window means more regular timing — the " +
                    "single sleep signal most consistently linked to long-term outcomes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryCard(nights: List<MetricReading>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Duration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (nights.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val seconds = nights.map { it.value.toLong() }
            val avg = seconds.average().toLong()
            val min = seconds.min()
            val max = seconds.max()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell("Nights", "${nights.size}")
                StatCell("Average", formatDuration(avg))
                StatCell("Shortest", formatDuration(min))
                StatCell("Longest", formatDuration(max))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NightRow(reading: MetricReading, sourceLabels: Map<String, String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatDuration(reading.value.toLong()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                ConfidenceBadge(reading.confidence)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatDate(reading.timestamp)} · ${sourceLabels[reading.sourceId] ?: "unknown source"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConfidenceBadge(level: Int) {
    val tier = ConfidenceTier.fromLevel(level)
    val color = when (tier) {
        ConfidenceTier.CLINICAL, ConfidenceTier.HIGH -> MaterialTheme.colorScheme.primary
        ConfidenceTier.MEDIUM -> MaterialTheme.colorScheme.tertiary
        ConfidenceTier.LOW, ConfidenceTier.VENDOR_DERIVED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        tier.name.lowercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun payloadLong(
    result: SleepRegularityCalculator.Result,
    fieldKey: String,
): Long = result.payload.first { it.fieldKey == fieldKey }.longValue ?: 0L

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}

private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
