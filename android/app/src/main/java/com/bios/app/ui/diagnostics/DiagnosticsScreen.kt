package com.bios.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToReference: () -> Unit = {},
    onNavigateToPipelineHealth: () -> Unit = {}
) {
    val results by viewModel.diagnosticResults.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val coverage by viewModel.metricCoverage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Health Diagnostics") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onNavigateToPipelineHealth) {
                    Icon(Icons.Default.Speed, contentDescription = "Pipeline health")
                }
                IconButton(onClick = onNavigateToReference) {
                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = "Longevity reference")
                }
            }
        )

        val anyDataReceived = coverage.values.any { it.hasAnyData }
        if (!anyDataReceived) {
            NoDataReceivedState()
        } else if (dataAge < BaselineEngine.MINIMUM_DATA_DAYS) {
            InsufficientDataState(dataAge, coverage)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { result ->
                    DiagnosticCard(result, onNavigateToDetail = onNavigateToDetail)
                }
            }
        }
    }
}

@Composable
private fun InsufficientDataState(
    dataAge: Int,
    coverage: Map<MetricType, BaselineEngine.Coverage>
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Building Your Baseline",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            val remaining = BaselineEngine.MINIMUM_DATA_DAYS - dataAge
            Text(
                "Diagnostics need at least ${BaselineEngine.MINIMUM_DATA_DAYS} days of ingested data to establish your personal baselines. " +
                    "Bios has $dataAge day${if (dataAge == 1) "" else "s"} so far — $remaining more needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { dataAge.toFloat() / BaselineEngine.MINIMUM_DATA_DAYS.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            // Per-metric breakdown so the owner can see at a glance which feeds
            // are silent — the previous "X more days needed" message hid the
            // case where one source was working but HR specifically had zero
            // readings.
            CoverageBreakdown(coverage)
        }
    }
}

@Composable
private fun NoDataReceivedState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("No data received yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bios hasn't received any sensor readings. " +
                    "If you wear a watch every day, the watch's companion app needs to write to Health Connect — " +
                    "Bios reads from Health Connect, it doesn't pair with the watch directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoverageBreakdown(coverage: Map<MetricType, BaselineEngine.Coverage>) {
    val rows = coverage.entries
        .sortedBy { it.key.readableName }
        .filter { it.value.totalSamples > 0 || it.key == MetricType.HEART_RATE }
    if (rows.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Per-metric ingestion",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            for ((metric, cov) in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(metric.readableName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (cov.totalSamples == 0) "no data"
                        else "${cov.sensorSamplesInWindow} in 14d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
