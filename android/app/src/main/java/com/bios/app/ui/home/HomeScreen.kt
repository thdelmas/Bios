package com.bios.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.ingest.SyncWorker
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.MetricCard
import com.bios.app.ui.components.MetricInfoSheet
import com.bios.app.ui.components.metricMatchesQuery
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Read" tab — the instrument the owner reads against their own
 * baseline. Quiet by default: shows vitals, deeper lenses (Active
 * Substances, Body Levels), and only speaks up when there's something to
 * surface (stale data, alerts, cold-start baseline).
 *
 * Things Bios noticed live on Notice. Things the owner reports about
 * themselves live on Log. This screen is the instrument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onNavigateToActiveSubstances: () -> Unit = {},
    onNavigateToBodyLevels: () -> Unit = {},
    onNavigateToMetric: (MetricType) -> Unit = {}
) {
    val unacknowledged by viewModel.unacknowledgedAlerts.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val lastSync by viewModel.ingestManager.lastSyncTime.collectAsState()
    val isSyncing by viewModel.ingestManager.isSyncing.collectAsState()
    var infoMetric by remember { mutableStateOf<MetricType?>(null) }

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.refresh() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    "Bios",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                lastSync?.let { ts ->
                    val timeFormat = remember {
                        SimpleDateFormat("h:mm a", Locale.getDefault())
                    }
                    Text(
                        "Synced ${timeFormat.format(Date(ts))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            MetricSearchBar(onSelect = onNavigateToMetric)

            val staleThresholdMillis = SyncWorker.STALE_THRESHOLD_HOURS * 3600 * 1000L
            val isStale = lastSync != null &&
                (System.currentTimeMillis() - lastSync!!) > staleThresholdMillis
            if (isStale) {
                StaleDataBanner()
            }

            if (unacknowledged.isNotEmpty()) {
                AlertStatusStrip(count = unacknowledged.size)
            }

            Text("Today's Vitals", style = MaterialTheme.typography.titleMedium)

            val metrics = listOf(
                Triple(MetricType.SLEEP_DURATION, "Sleep", Icons.Default.Bedtime),
                Triple(MetricType.SLEEP_EFFICIENCY, "Sleep Eff.", Icons.Default.Percent),
                Triple(MetricType.HEART_RATE, "Heart Rate", Icons.Default.Favorite),
                Triple(MetricType.HEART_RATE_VARIABILITY, "HRV", Icons.AutoMirrored.Filled.ShowChart),
                Triple(MetricType.BLOOD_OXYGEN, "SpO2", Icons.Default.Air),
                Triple(MetricType.RESPIRATORY_RATE, "Resp. Rate", Icons.Default.Air),
                Triple(MetricType.STEPS, "Steps", Icons.AutoMirrored.Filled.DirectionsWalk),
                Triple(MetricType.SKIN_TEMPERATURE_DEVIATION, "Skin Temp", Icons.Default.Thermostat),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(510.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(metrics) { (metricType, label, icon) ->
                    MetricCard(
                        metricType = metricType,
                        label = label,
                        icon = icon,
                        viewModel = viewModel,
                        refreshKey = lastSync,
                        onClick = { onNavigateToMetric(metricType) },
                        onInfoClick = { infoMetric = metricType },
                    )
                }
            }

            HomeEntryCard(
                icon = Icons.Default.Medication,
                title = "Active Substances",
                subtitle = "Caffeine, alcohol, tobacco, cannabis — concentration + event log",
                onClick = onNavigateToActiveSubstances,
            )

            HomeEntryCard(
                icon = Icons.Default.Science,
                title = "Body Levels",
                subtitle = "Biomarker panels — most-recent lab values",
                onClick = onNavigateToBodyLevels,
            )

            if (dataAge < BaselineEngine.MINIMUM_DATA_DAYS) {
                BaselineCountdown(
                    currentDays = dataAge,
                    requiredDays = BaselineEngine.MINIMUM_DATA_DAYS
                )
            }
        }
    }

    infoMetric?.let { metric ->
        MetricInfoSheet(
            metricType = metric,
            onDismiss = { infoMetric = null },
        )
    }
}

/**
 * Single-line cue. The Notice tab badge is the canonical signal; this
 * strip is the textual nudge for owners who landed on Read first. Only
 * renders when count > 0 — silence is the absence.
 */
@Composable
private fun AlertStatusStrip(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "$count ${if (count == 1) "alert" else "alerts"} — see Notice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun BaselineCountdown(currentDays: Int, requiredDays: Int) {
    val remaining = requiredDays - currentDays

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Building Your Baseline",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios needs $remaining more day${if (remaining == 1) "" else "s"} of data to establish your personal baseline and start detecting patterns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { currentDays.toFloat() / requiredDays.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * App-level search to jump straight into a metric's dashboard. Universe is
 * every [MetricType] the contract knows — sensor-only, derived, and manual-
 * entry alike — because the search lands on the trend dashboard, not the
 * entry screen. From the dashboard the "+ New entry" button (visible when
 * the metric allows manual entry) hops to AddReadingScreen pre-filled.
 */
@Composable
private fun MetricSearchBar(onSelect: (MetricType) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by remember {
        derivedStateOf {
            if (query.isBlank()) emptyList()
            else MetricType.entries
                .filter { metricMatchesQuery(it, query) }
                .sortedBy { it.readableName }
                .take(20)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search metrics — Weight, HRV, BMI, ApoB…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (results.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results) { metric ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(metric)
                                    query = ""
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(metric.readableName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                metric.unit.symbol.ifEmpty { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaleDataBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Health data may be outdated. Open Health Connect to check your wearable connection.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D4C00)
            )
        }
    }
}
