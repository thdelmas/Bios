package com.bios.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.LatencyPercentiles
import com.bios.app.ui.AppViewModel

/**
 * Detection pipeline health dashboard showing latency SLOs per stage.
 * All measurements are on-device only — no telemetry leaves the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineHealthScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val pipelineSummary by viewModel.pipelineSummary.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshPipelineSummary()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Pipeline Health") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (pipelineSummary.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No pipeline data yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Latency metrics appear after the detection pipeline has run at least once.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Overall SLO status
                item {
                    val totalViolations = pipelineSummary.sumOf { it.sloViolations }
                    OverallStatusCard(totalViolations)
                }

                items(pipelineSummary) { percentiles ->
                    PipelineStageCard(percentiles)
                }
            }
        }
    }
}

@Composable
private fun OverallStatusCard(totalViolations: Int) {
    val healthy = totalViolations == 0
    val color = if (healthy) Color(0xFF4CAF50) else Color(0xFFFF9800)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (healthy) "All SLOs met" else "$totalViolations SLO violation${if (totalViolations > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Detection pipeline latency targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PipelineStageCard(percentiles: LatencyPercentiles) {
    val hasViolations = percentiles.sloViolations > 0
    val sloLabel = percentiles.sloTargetMs?.let { formatDuration(it) } ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    percentiles.stage.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (hasViolations) {
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "${percentiles.sloViolations} violations",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Percentile table
            Row(modifier = Modifier.fillMaxWidth()) {
                PercentileLabel("p50", Modifier.weight(1f))
                PercentileLabel("p90", Modifier.weight(1f))
                PercentileLabel("p99", Modifier.weight(1f))
                PercentileLabel("max", Modifier.weight(1f))
                PercentileLabel("SLO", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                PercentileValue(formatDuration(percentiles.p50), Modifier.weight(1f))
                PercentileValue(formatDuration(percentiles.p90), Modifier.weight(1f))
                PercentileValue(formatDuration(percentiles.p99), Modifier.weight(1f))
                PercentileValue(
                    formatDuration(percentiles.max),
                    Modifier.weight(1f),
                    color = if (percentiles.sloTargetMs != null && percentiles.max > percentiles.sloTargetMs)
                        Color(0xFFF44336) else null
                )
                PercentileValue(sloLabel, Modifier.weight(1f))
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "${percentiles.count} measurements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PercentileLabel(label: String, modifier: Modifier) {
    Text(
        label,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PercentileValue(value: String, modifier: Modifier, color: Color? = null) {
    Text(
        value,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = color ?: MaterialTheme.colorScheme.onSurface
    )
}

private fun formatDuration(ms: Long): String {
    return when {
        ms < 1_000 -> "${ms}ms"
        ms < 60_000 -> "${String.format("%.1f", ms / 1_000.0)}s"
        else -> "${String.format("%.1f", ms / 60_000.0)}m"
    }
}
