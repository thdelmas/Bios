package com.bios.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.SourceMetricToggle
import com.bios.app.model.SourceType
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Per-metric source enable/disable surface.
 *
 * Lists every [MetricType] grouped by [MetricDomain]. For each metric the
 * owner can flip individual data sources off so the ingest gate in
 * `IngestManager.applySourceMetricToggles` stops writing that
 * (source, metric) pair on future syncs. Historical rows are not touched.
 *
 * Only sources that have actually produced rows for a given metric are
 * shown — plus any currently-disabled source, so toggling off then on
 * doesn't make the row disappear. This keeps the list short and relevant
 * instead of dumping all 14 `SourceType`s under every metric.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricSourcesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toggleDao = remember { viewModel.db.sourceMetricToggleDao() }
    val readingDao = remember { viewModel.db.metricReadingDao() }
    val sourceDao = remember { viewModel.db.dataSourceDao() }

    // (sourceTypeKey, metricTypeKey) -> enabled. Absence == enabled.
    val toggles by remember {
        toggleDao.allFlow().map { list ->
            list.associate { (it.sourceTypeKey to it.metricTypeKey) to it.enabled }
        }
    }.collectAsState(initial = emptyMap())

    // Per-metric source presence (count by source). Loaded lazily on
    // expand so opening the screen doesn't block on 80+ DAO queries.
    val sourceCounts = remember { mutableStateOf<Map<String, List<SourcePresence>>>(emptyMap()) }
    val sourceIdToType = remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        sourceIdToType.value = sourceDao.getAll().associate { it.id to it.sourceType }
    }

    val expanded = remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Per-metric sources") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Text(
            text = "Disable a source per metric to stop it from feeding that metric on future syncs. Historical readings stay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val byDomain = remember {
            MetricType.entries.groupBy { it.domain }
                .toSortedMap(compareBy { it.name })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            byDomain.forEach { (domain, metrics) ->
                item(key = "h_${domain.name}") {
                    Text(
                        text = domain.name.replace('_', ' '),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(metrics, key = { it.key }) { metric ->
                    MetricCard(
                        metric = metric,
                        isExpanded = expanded.value == metric.key,
                        onToggleExpand = {
                            expanded.value = if (expanded.value == metric.key) null else metric.key
                            // Lazy load on first expand
                            if (expanded.value == metric.key &&
                                metric.key !in sourceCounts.value
                            ) {
                                scope.launch {
                                    val rows = readingDao.sourceCountsForMetric(metric.key)
                                    val mapped = rows.mapNotNull { row ->
                                        val typeKey = sourceIdToType.value[row.sourceId]
                                            ?: return@mapNotNull null
                                        SourcePresence(
                                            sourceTypeKey = typeKey,
                                            deviceName = row.sourceLabel,
                                            count = row.count,
                                        )
                                    }
                                    // Collapse duplicates from multiple DataSource rows
                                    // of the same SourceType (e.g. two paired devices).
                                    val collapsed = mapped.groupBy { it.sourceTypeKey }
                                        .map { (key, rows) ->
                                            SourcePresence(
                                                sourceTypeKey = key,
                                                deviceName = rows.firstNotNullOfOrNull { it.deviceName },
                                                count = rows.sumOf { it.count },
                                            )
                                        }
                                    sourceCounts.value = sourceCounts.value + (metric.key to collapsed)
                                }
                            }
                        },
                        toggles = toggles,
                        sourcesWithData = sourceCounts.value[metric.key].orEmpty(),
                        onSetEnabled = { sourceKey, enabled ->
                            scope.launch {
                                toggleDao.upsert(
                                    SourceMetricToggle(
                                        sourceTypeKey = sourceKey,
                                        metricTypeKey = metric.key,
                                        enabled = enabled,
                                    )
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class SourcePresence(
    val sourceTypeKey: String,
    val deviceName: String?,
    val count: Int,
)

@Composable
private fun MetricCard(
    metric: MetricType,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    toggles: Map<Pair<String, String>, Boolean>,
    sourcesWithData: List<SourcePresence>,
    onSetEnabled: (sourceKey: String, enabled: Boolean) -> Unit,
) {
    // Union the empirical set with any source the owner has previously
    // toggled off for this metric — so a "disabled, never had data" row
    // remains visible and reversible.
    val disabledForMetric = toggles
        .filter { it.key.second == metric.key && !it.value }
        .map { it.key.first }
    val sources = (sourcesWithData.map { it.sourceTypeKey } + disabledForMetric).distinct()

    val enabledCount = sources.count { typeKey ->
        toggles[typeKey to metric.key] != false
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metric.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            sources.isEmpty() -> "No source data yet"
                            else -> "$enabledCount / ${sources.size} sources enabled"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (sources.isEmpty()) {
                        Text(
                            text = "No source has produced a reading for this metric yet. Toggles will appear here once data lands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        sources.forEach { typeKey ->
                            val presence = sourcesWithData.firstOrNull { it.sourceTypeKey == typeKey }
                            val enabled = toggles[typeKey to metric.key] != false
                            SourceToggleRow(
                                sourceTypeKey = typeKey,
                                deviceName = presence?.deviceName,
                                count = presence?.count ?: 0,
                                enabled = enabled,
                                onCheckedChange = { onSetEnabled(typeKey, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceToggleRow(
    sourceTypeKey: String,
    deviceName: String?,
    count: Int,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sourceDisplayName(sourceTypeKey, deviceName),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (count > 0) {
                Text(
                    text = "$count readings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = onCheckedChange)
    }
}

private fun sourceDisplayName(typeKey: String, deviceName: String?): String {
    val pretty = SourceType.entries.firstOrNull { it.key == typeKey }
        ?.name?.replace('_', ' ')
        ?: typeKey
    return if (deviceName != null) "$pretty — $deviceName" else pretty
}
