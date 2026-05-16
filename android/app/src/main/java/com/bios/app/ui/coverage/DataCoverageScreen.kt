package com.bios.app.ui.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.CoverageRoute
import com.bios.app.engine.CoverageRouteKind
import com.bios.app.engine.MetricCoverage
import com.bios.app.engine.MetricCoverageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-metric coverage view: lists every metric Bios cares about along
 * with a LIVE / STALE / MISSING badge, the last-seen timestamp, and the
 * routes that could fill the gap (Connect Oura, Add Lab Values, etc.).
 *
 * Sort order surfaces problems first: MISSING → STALE → LIVE, so the
 * owner's attention lands on the gaps without scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCoverageScreen(
    viewModel: DataCoverageViewModel,
    onBack: () -> Unit,
    onNavigate: (route: String) -> Unit
) {
    val coverage by viewModel.coverage.collectAsState()
    val sorted = remember(coverage) { coverage.sortedWith(StatusComparator) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Coverage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Loading coverage…", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { OverviewBanner(sorted) }
            items(sorted, key = { it.metricType.key }) { entry ->
                CoverageRow(entry, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun OverviewBanner(rows: List<MetricCoverage>) {
    val missing = rows.count { it.status == MetricCoverageStatus.MISSING }
    val stale = rows.count { it.status == MetricCoverageStatus.STALE }
    val live = rows.count { it.status == MetricCoverageStatus.LIVE }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Snapshot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                "$live live · $stale stale · $missing missing",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Stale and missing metrics show suggested routes below — tap a chip to set it up or register the data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoverageRow(
    entry: MetricCoverage,
    onNavigate: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(entry.status)
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.metricType.readableName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        timestampLabel(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(entry.status)
            }

            if (entry.status != MetricCoverageStatus.LIVE) {
                Spacer(Modifier.size(8.dp))
                RouteChips(entry.routes, onNavigate)
            }
        }
    }
}

@Composable
private fun StatusDot(status: MetricCoverageStatus) {
    val color = when (status) {
        MetricCoverageStatus.LIVE -> MaterialTheme.colorScheme.primary
        MetricCoverageStatus.STALE -> MaterialTheme.colorScheme.tertiary
        MetricCoverageStatus.MISSING -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun StatusBadge(status: MetricCoverageStatus) {
    val (label, color) = when (status) {
        MetricCoverageStatus.LIVE -> "Live" to MaterialTheme.colorScheme.primary
        MetricCoverageStatus.STALE -> "Stale" to MaterialTheme.colorScheme.tertiary
        MetricCoverageStatus.MISSING -> "Missing" to MaterialTheme.colorScheme.error
    }
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteChips(routes: List<CoverageRoute>, onNavigate: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Unconfigured routes first (they're the actionable CTAs); already-
        // configured routes go last as a confirmation that the path is set
        // up and Bios is waiting on a sync.
        for (route in routes.sortedBy { it.isConfigured }) {
            val label = if (route.isConfigured) "✓ ${route.displayName}" else route.actionLabel()
            AssistChip(
                onClick = { route.deepLink?.let(onNavigate) },
                enabled = route.deepLink != null || route.isConfigured,
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = if (route.isConfigured) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                }
            )
        }
    }
}

private fun CoverageRoute.actionLabel(): String = when (kind) {
    CoverageRouteKind.HEALTH_CONNECT -> "Enable Health Connect"
    CoverageRouteKind.API_ADAPTER -> "Connect $displayName"
    CoverageRouteKind.DIRECT_SENSOR -> "Pair a wearable"
    CoverageRouteKind.PHONE_SENSOR -> "Allow phone sensor"
    CoverageRouteKind.PPG_CAPTURE -> "Take HRV snapshot"
    CoverageRouteKind.MANUAL_ENTRY -> "Add value manually"
    CoverageRouteKind.FHIR_IMPORT -> "Import FHIR file"
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.US)
private fun timestampLabel(entry: MetricCoverage): String {
    val ts = entry.lastTimestamp ?: return "Never recorded"
    return "Last reading: ${dateFormat.format(Date(ts))}"
}

/** Stable ordering: MISSING first, then STALE, then LIVE; ties broken by
 *  metric key for determinism across recompositions. */
private object StatusComparator : Comparator<MetricCoverage> {
    override fun compare(a: MetricCoverage, b: MetricCoverage): Int {
        val byStatus = statusRank(a.status).compareTo(statusRank(b.status))
        return if (byStatus != 0) byStatus else a.metricType.key.compareTo(b.metricType.key)
    }

    private fun statusRank(s: MetricCoverageStatus): Int = when (s) {
        MetricCoverageStatus.MISSING -> 0
        MetricCoverageStatus.STALE -> 1
        MetricCoverageStatus.LIVE -> 2
    }
}
