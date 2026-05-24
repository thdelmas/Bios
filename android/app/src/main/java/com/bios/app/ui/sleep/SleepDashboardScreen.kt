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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.SleepRegularityCalculator
import com.bios.app.ingest.PhoneSleepWorker
import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch
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
    onSeeWearableRecommendations: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var windowDays by remember { mutableStateOf(7) }
    var refreshTick by remember { mutableStateOf(0) }
    var nights by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var sourceLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var inferring by remember { mutableStateOf(false) }
    var inferenceMessage by remember { mutableStateOf<String?>(null) }
    var bufferedSamples by remember { mutableStateOf(0) }
    var lastSampleMs by remember { mutableStateOf<Long?>(null) }
    var longestQuietMs by remember { mutableStateOf(0L) }
    var signalBreakdown by remember {
        mutableStateOf<com.bios.app.engine.PhoneSleepInference.SignalBreakdown?>(null)
    }
    // #245 — banner state: gate-derived + persisted dismissed timestamp.
    var bannerDismissedAtMs by remember {
        mutableStateOf(WearableRecommendationStore.getDismissedAtMs(context))
    }

    LaunchedEffect(windowDays, refreshTick) {
        val end = System.currentTimeMillis()
        val start = end - windowDays.toLong() * 24L * 3600L * 1000L
        nights = viewModel.db.metricReadingDao()
            .fetch(MetricType.SLEEP_DURATION.key, start, end)
            .sortedByDescending { it.timestamp }
        sourceLabels = viewModel.db.dataSourceDao().getAll()
            .associate { it.id to (it.deviceName ?: it.sourceType) }
        bufferedSamples = viewModel.db.phoneSleepSampleDao().count()
        lastSampleMs = viewModel.db.phoneSleepSampleDao().lastTimestamp()
        // Compute the longest screen-off stretch in the inference window so
        // the empty-state diagnostic can show how close the buffer is to the
        // 4h floor — turns "NoSleepWindow" into "you have 2h 30m, need 4h".
        val bufferStart = end - PhoneSleepWorker.FORCE_WINDOW_MS
        val samples = viewModel.db.phoneSleepSampleDao()
            .fetchInRange(bufferStart, end)
            .map {
                com.bios.app.engine.PhoneSleepInference.Sample(
                    timestamp = it.timestamp,
                    screenOff = it.screenOff,
                    charging = it.charging,
                    ambientLightLux = it.ambientLightLux,
                    accelMagnitudeVar = it.accelMagnitudeVar,
                )
            }
        longestQuietMs = com.bios.app.engine.PhoneSleepInference.longestScreenOffMs(samples)
        signalBreakdown = com.bios.app.engine.PhoneSleepInference.signalBreakdown(samples)
    }

    val onInferNow: () -> Unit = {
        if (!inferring) {
            scope.launch {
                inferring = true
                inferenceMessage = null
                val result = PhoneSleepWorker.runImmediateInference(context)
                inferenceMessage = describe(result)
                inferring = false
                refreshTick++
            }
        }
    }

    val regularity = remember(nights, windowDays) {
        SleepRegularityCalculator.derive(
            nights, sourceId = "dashboard-preview", windowDays = windowDays
        )
    }

    // #245 — wearable-recommendation banner: pure-function gate over the
    // last 7 calendar nights, honoring a 30-day cooldown after dismissal.
    // Both `remember` calls must live at the Composable scope (not
    // inside the LazyColumn lambda).
    val recentNights = remember(nights) {
        WearableRecommendationGate.binByNight(
            readingsNewestFirst = nights,
            localDayKey = { ms ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = ms
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis / (24L * 60L * 60L * 1000L)
            },
        )
    }
    val showWearableBanner = remember(recentNights, bannerDismissedAtMs) {
        WearableRecommendationGate.shouldShow(
            recentNights = recentNights,
            dismissedAtMs = bannerDismissedAtMs,
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
                },
                actions = {
                    IconButton(onClick = onInferNow, enabled = !inferring) {
                        if (inferring) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(4.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Run phone inference now"
                            )
                        }
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
            if (showWearableBanner) {
                item {
                    WearableRecommendationBanner(
                        onSeeRecommendations = onSeeWearableRecommendations,
                        onDismiss = {
                            val now = System.currentTimeMillis()
                            WearableRecommendationStore.markDismissedNow(context, now)
                            bannerDismissedAtMs = now
                        },
                    )
                }
            }
            inferenceMessage?.let { msg ->
                item { InferenceResultCard(msg) }
            }
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
                item {
                    EmptyStateCard(
                        inferring = inferring,
                        onInferNow = onInferNow,
                        bufferedSamples = bufferedSamples,
                        lastSampleMs = lastSampleMs,
                        longestQuietMs = longestQuietMs,
                        signalBreakdown = signalBreakdown,
                    )
                }
            } else {
                items(nights, key = { it.id }) { night ->
                    NightRow(night, sourceLabels)
                }
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

private fun describe(result: PhoneSleepWorker.ImmediateResult): String = when (result) {
    is PhoneSleepWorker.ImmediateResult.Written ->
        "Inferred ${result.count} reading(s) from the last 36h buffer."
    PhoneSleepWorker.ImmediateResult.NoSensor ->
        "No accelerometer on this device — phone-only inference can't run."
    PhoneSleepWorker.ImmediateResult.NotEnoughSamples ->
        "Not enough samples buffered yet (need ${PhoneSleepWorker.MIN_SAMPLES_FOR_INFERENCE}+). " +
            "Keep the app installed through a night and try again."
    PhoneSleepWorker.ImmediateResult.NoSleepWindow ->
        "No 4h+ continuous screen-off stretch found in the last 36h — " +
            "the inference needs a long quiet window."
}
