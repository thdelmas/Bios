package com.bios.app.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug screen for the metric_readings table (#253).
 *
 * Lists every MetricType with at least one row, count-descending, and
 * expands the top [TOP_METRICS_FOR_SOURCE_BREAKDOWN] to a per-DataSource
 * breakdown so the owner can see which adapter is producing the volume
 * dominating the encrypted DB.
 *
 * Read-only: no mutations from this screen. Reachable via a long-press
 * easter egg on the Version row in Settings — kept off the main nav so
 * release-build owners don't stumble into it, but accessible when an
 * adapter is silently failing and the row count for that source has
 * dropped to zero.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricReadingsDebugScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var rows by remember { mutableStateOf<List<MetricReadingDao.MetricTypeCountRow>>(emptyList()) }
    var totalReadings by remember { mutableIntStateOf(0) }
    var sourceBreakdowns by remember {
        mutableStateOf<Map<String, List<MetricReadingDao.SourceCountRow>>>(emptyMap())
    }

    LaunchedEffect(Unit) {
        val dao = viewModel.db.metricReadingDao()
        totalReadings = dao.countAll()
        val counts = dao.metricTypeCounts()
        rows = counts
        // Pre-fetch the per-source breakdown for the top metric types so
        // expansion doesn't latency-spike when the owner scrolls.
        sourceBreakdowns = counts.take(TOP_METRICS_FOR_SOURCE_BREAKDOWN)
            .associate { it.metricType to dao.sourceCountsForMetric(it.metricType) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("metric_readings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total rows", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "%,d".format(totalReadings),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${rows.size} distinct metric types",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(rows, key = { it.metricType }) { row ->
                MetricRowCard(row, sourceBreakdowns[row.metricType])
            }
        }
    }
}

@Composable
private fun MetricRowCard(
    row: MetricReadingDao.MetricTypeCountRow,
    sources: List<MetricReadingDao.SourceCountRow>?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    row.metricType,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "%,d".format(row.count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            row.lastTimestamp?.let {
                Text(
                    "last: ${formatTimestamp(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!sources.isNullOrEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "by source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                sources.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            source.sourceLabel?.takeIf { it.isNotBlank() } ?: source.sourceId,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "%,d".format(source.count),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * How many of the top-volume metric types get an expanded per-DataSource
 * breakdown. The issue calls out "HR / steps / sleep" — three is enough
 * to expose the canonical vendor-drift case (HR coming half from HC and
 * half from a paired wearable) without overwhelming the screen.
 */
private const val TOP_METRICS_FOR_SOURCE_BREAKDOWN: Int = 3
