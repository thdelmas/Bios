package com.bios.app.ui.trends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.RecentEntry
import com.bios.app.ui.coverageFor
import com.bios.app.ui.deleteReading
import com.bios.app.ui.deleteReadings
import com.bios.app.ui.getRecentReadings
import com.bios.app.ui.recomputeBaseline
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch

/**
 * Per-metric dashboard. Renders the metric name as a clickable header
 * (opens the metric-browser sheet for switching), then a small line chart
 * of the recent values, the personal baseline (or a status card when one
 * is missing), and a list of recent entries with single + multi-select
 * delete. Lab biomarker + reproductive metrics have dedicated dashboards;
 * everything else lands here via the global search bar or the Home cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    viewModel: AppViewModel,
    initialMetric: MetricType? = null,
) {
    val baselines by viewModel.baselines.collectAsState()
    val coverage by viewModel.metricCoverage.collectAsState()
    var selectedMetric by remember(initialMetric) {
        mutableStateOf(initialMetric ?: MetricType.HEART_RATE)
    }
    var showBrowser by remember { mutableStateOf(false) }
    var recentEntries by remember { mutableStateOf<List<RecentEntry>>(emptyList()) }
    var entriesLoaded by remember(selectedMetric) { mutableStateOf(false) }
    var entriesRefreshKey by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<RecentEntry?>(null) }
    var selectedEntryIds by remember(selectedMetric) { mutableStateOf(emptySet<String>()) }
    var pendingBatchDelete by remember { mutableStateOf(false) }
    var liveCoverage by remember(selectedMetric) { mutableStateOf<BaselineEngine.Coverage?>(null) }
    var chartWindow by remember(selectedMetric) {
        mutableStateOf(MetricChartTimeWindow.DEFAULT)
    }
    var chartReadings by remember { mutableStateOf<List<MetricReading>>(emptyList()) }
    var chartLoaded by remember(selectedMetric, chartWindow) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedMetric, entriesRefreshKey) {
        entriesLoaded = false
        recentEntries = viewModel.getRecentReadings(selectedMetric, limit = 30)
        entriesLoaded = true
        // Pre-baked coverage map only covers TRACKED_METRICS (HR/HRV/etc.).
        // For everything else (Weight, BMI, biomarkers, ...) compute on demand.
        liveCoverage = viewModel.coverageFor(selectedMetric)
    }

    LaunchedEffect(selectedMetric, chartWindow, entriesRefreshKey) {
        chartLoaded = false
        val now = System.currentTimeMillis()
        val raw = viewModel.db.metricReadingDao().fetch(
            metricType = selectedMetric.key,
            startMillis = chartWindow.startMillis(now),
            endMillis = now,
        )
        // Downsample on the UI thread is cheap — fetch already returns
        // sorted ASC, and the helper preserves first + last.
        chartReadings = downsampleForChart(raw)
        chartLoaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showBrowser = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedMetric.readableName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Browse metrics")
        }

        MetricChart(
            metric = selectedMetric,
            readings = chartReadings,
            loaded = chartLoaded,
            selectedWindow = chartWindow,
            onSelectWindow = { chartWindow = it },
        )

        val selectedBaseline = baselines.find { it.metricType == selectedMetric.key }
        if (selectedBaseline != null) {
            BaselineSummaryCard(selectedBaseline, selectedMetric.unit)
        } else {
            NoBaselineCard(
                metricLabel = selectedMetric.readableName,
                coverage = liveCoverage ?: coverage[selectedMetric],
            )
        }

        RecentEntriesSection(
            metric = selectedMetric,
            entries = recentEntries,
            loaded = entriesLoaded,
            selectedIds = selectedEntryIds,
            onLongPress = { id -> selectedEntryIds = selectedEntryIds + id },
            onToggle = { id ->
                selectedEntryIds = if (id in selectedEntryIds) selectedEntryIds - id
                                   else selectedEntryIds + id
            },
            onDeleteOne = { entry -> pendingDelete = entry },
            onCancelSelection = { selectedEntryIds = emptySet() },
            onRequestBatchDelete = { pendingBatchDelete = true },
        )
    }

    if (showBrowser) {
        MetricBrowserSheet(
            selected = selectedMetric,
            onSelect = { metric ->
                selectedMetric = metric
                showBrowser = false
            },
            onDismiss = { showBrowser = false },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this entry?") },
            text = {
                Text(
                    "${formatMetricValue(entry.reading.value, selectedMetric.unit)} on " +
                        "${formatEntryTimestamp(entry.reading.timestamp)} — " +
                        "this is permanent."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = entry.reading.id
                    val metric = selectedMetric
                    pendingDelete = null
                    coroutineScope.launch {
                        viewModel.deleteReading(id)
                        viewModel.recomputeBaseline(metric)
                        entriesRefreshKey += 1
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (pendingBatchDelete) {
        val count = selectedEntryIds.size
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("Delete $count ${if (count == 1) "entry" else "entries"}?") },
            text = { Text("This is permanent.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedEntryIds.toList()
                    val metric = selectedMetric
                    pendingBatchDelete = false
                    selectedEntryIds = emptySet()
                    coroutineScope.launch {
                        viewModel.deleteReadings(ids)
                        viewModel.recomputeBaseline(metric)
                        entriesRefreshKey += 1
                    }
                }) { Text("Delete $count") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchDelete = false }) { Text("Cancel") }
            },
        )
    }
}
