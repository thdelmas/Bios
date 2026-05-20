package com.bios.app.ui.biomarkers

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.EventPayloadField
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.biomarkers.BiomarkerDashboardAggregator.PanelDisplay
import com.bios.app.ui.biomarkers.BiomarkerDashboardAggregator.Tile
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Biomarker / body-levels dashboard (#137). Slow-rolling clinical
 * snapshot — *"where does my body sit right now"* — sibling to the
 * fast-moving Active Substances surface (#138).
 *
 * Panels render the most-recent reading per [MetricType] with descriptive
 * within / below / above coloring driven by [BiomarkerReferenceRanges].
 * Tap-through routes to the existing trends view so the per-metric
 * time series re-uses one screen.
 *
 * Manifesto-clean: classification is a passthrough of the lab's own
 * flagging, no composite "health score" across panels, no
 * supplement / protocol upsell, no "your X is concerning" framing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiomarkerDashboardScreen(
    viewModel: AppViewModel,
    onNavigateToMetricTrend: (MetricType) -> Unit,
    onBack: () -> Unit,
) {
    var panels by remember { mutableStateOf<List<PanelDisplay>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val dao = viewModel.db.metricReadingDao()
        val payloadDao = viewModel.db.eventPayloadFieldDao()
        val sourceDao = viewModel.db.dataSourceDao()

        val biomarkerMetrics = MetricType.entries.filter { it.domain == MetricDomain.BIOMARKER }
        val readings: List<MetricReading> = biomarkerMetrics.flatMap {
            // Cheap fan-out: BIOMARKER readings are sparse (a few dozen
            // per owner at most), so one fetchLatest per metric is far
            // less work than a full-table scan.
            dao.fetchLatest(it.key, limit = 1)
        }
        val payloadByReadingId: Map<String, List<EventPayloadField>> = readings
            .associate { it.id to payloadDao.fetchForReading(it.id) }
        val sources = sourceDao.getAll().associate { it.id to (it.deviceName ?: it.sourceType) }

        panels = BiomarkerDashboardAggregator.compute(
            readings = readings,
            payloadByReadingId = payloadByReadingId,
            sourceLabels = sources,
            nowMs = now,
        )
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body Levels") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Intro() }
            if (!loaded) {
                item {
                    Text(
                        "Loading biomarker readings…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (panels.none { it.hasAnyReading }) {
                item { EmptyBiomarkerState() }
            } else {
                items(panels, key = { it.panel.id }) { panel ->
                    PanelCard(panel, onNavigateToMetricTrend)
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    Text(
        "Most-recent lab values, grouped by panel. Classification mirrors " +
            "the lab's own flagging — you read the picture.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyBiomarkerState() {
    Text(
        "No biomarker readings yet. Use Settings → Add biomarker entry or " +
            "import a FHIR bundle from a lab.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PanelCard(panel: PanelDisplay, onTrend: (MetricType) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    panel.panel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StalenessChip(panel.oldestAgeDays)
            }
            for (tile in panel.tiles) {
                TileRow(tile, onTrend)
            }
        }
    }
}

@Composable
private fun StalenessChip(ageDays: Long?) {
    val label = when {
        ageDays == null -> "no data"
        ageDays < 30L -> "fresh"
        ageDays < 180L -> "${ageDays / 30L}mo ago"
        else -> "${ageDays / 30L}mo ago"
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TileRow(tile: Tile, onTrend: (MetricType) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onTrend(tile.metricType) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    metricLabel(tile.metricType),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                ValueAndClassification(tile)
            }
            ProvenanceLine(tile)
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ValueAndClassification(tile: Tile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatValue(tile),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (tile.classification != ReferenceRange.Classification.UNKNOWN &&
            tile.classification != ReferenceRange.Classification.WITHIN
        ) {
            Spacer(Modifier.height(0.dp))
            Text(
                "  · ${classificationLabel(tile.classification)}",
                style = MaterialTheme.typography.labelSmall,
                color = classificationColor(tile.classification),
            )
        }
    }
}

@Composable
private fun ProvenanceLine(tile: Tile) {
    val parts = buildList {
        tile.lastReadingTimestampMs?.let { add(formatDate(it)) }
        tile.context.labName?.takeIf { it.isNotBlank() }?.let { add(it) }
        tile.context.specimen?.let { add(it.readable) }
        tile.context.fasting?.let { add(if (it) "fasting" else "non-fasting") }
        tile.sourceLabel?.let { if (tile.context.labName.isNullOrBlank()) add(it) }
        tile.range?.let { add("ref ${rangeLabel(it)}") }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val note = tile.context.note
    if (!note.isNullOrBlank()) {
        Text(
            note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun classificationColor(c: ReferenceRange.Classification): Color = when (c) {
    ReferenceRange.Classification.WITHIN -> MaterialTheme.colorScheme.primary
    ReferenceRange.Classification.BELOW -> MaterialTheme.colorScheme.tertiary
    ReferenceRange.Classification.ABOVE -> MaterialTheme.colorScheme.error
    ReferenceRange.Classification.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun classificationLabel(c: ReferenceRange.Classification): String = when (c) {
    ReferenceRange.Classification.BELOW -> "below ref"
    ReferenceRange.Classification.ABOVE -> "above ref"
    ReferenceRange.Classification.WITHIN -> "within ref"
    ReferenceRange.Classification.UNKNOWN -> "no range"
}

private fun rangeLabel(r: ReferenceRange): String {
    val low = r.low
    val high = r.high
    return when {
        low != null && high != null -> "${formatNumber(low)}–${formatNumber(high)} ${r.units}"
        low != null -> "≥ ${formatNumber(low)} ${r.units}"
        high != null -> "≤ ${formatNumber(high)} ${r.units}"
        else -> r.units
    }
}

private fun formatValue(tile: Tile): String {
    val v = tile.value ?: return "—"
    val units = tile.range?.units ?: tile.metricType.unit.symbol
    return "${formatNumber(v)} $units"
}

private fun formatNumber(value: Double): String {
    // Use one decimal place for sub-100 values (most labs), 0 for larger
    // ones (cholesterol/glucose).
    return if (value < 100.0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        String.format(Locale.US, "%.0f", value)
    }
}

private fun metricLabel(metric: MetricType): String = metric.readableName.replace("Hba1c", "HbA1c")

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
