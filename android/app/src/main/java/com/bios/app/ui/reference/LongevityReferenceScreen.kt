package com.bios.app.ui.reference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.alerts.BiomarkerReference
import com.bios.app.alerts.BiomarkerReferences
import com.bios.app.model.MetricType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongevityReferenceScreen(
    trackedMetrics: Set<MetricType>,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("What Longevity Science Tracks") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "How clinical biomarkers relate to your wearable data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Longevity research tracks dozens of clinical biomarkers through blood tests and imaging. Many have wearable-derived proxies that Bios can monitor continuously. This reference explains the connections — it does not prescribe any protocol.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            // Coverage summary
            item {
                CoverageSummaryCard(trackedMetrics)
            }

            // Biomarker reference cards
            items(BiomarkerReferences.all) { biomarker ->
                BiomarkerCard(biomarker, trackedMetrics)
            }

            // Sources
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sources",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                val sources = listOf(
                    "Blueprint protocol — publicly available at blueprint.bryanjohnson.com",
                    "Levine ME (2018) — An epigenetic biomarker of aging (PhenoAge)",
                    "Belsky DW et al. (2020) — DunedinPACE, a DNA methylation biomarker of the pace of aging",
                    "Attia P (2023) — Outlive: The Science and Art of Longevity"
                )
                sources.forEach { source ->
                    Text(
                        source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CoverageSummaryCard(trackedMetrics: Set<MetricType>) {
    val allProxyMetrics = BiomarkerReferences.all
        .flatMap { it.proxyMetrics }
        .toSet()
    val covered = allProxyMetrics.intersect(trackedMetrics)
    val missing = allProxyMetrics - trackedMetrics

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Your Coverage",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "${covered.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "metrics tracked",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column {
                    Text(
                        "${missing.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "need additional device",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column {
                    Text(
                        "${BiomarkerReferences.all.size}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "biomarkers referenced",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (missing.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Not yet tracked: ${missing.joinToString { it.readableName }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BiomarkerCard(
    biomarker: BiomarkerReference,
    trackedMetrics: Set<MetricType>
) {
    var expanded by remember { mutableStateOf(false) }

    val proxyStatus = biomarker.proxyMetrics.map { metric ->
        metric to (metric in trackedMetrics)
    }
    val allTracked = proxyStatus.all { it.second }
    val someTracked = proxyStatus.any { it.second }

    val statusColor = when {
        allTracked -> Color(0xFF4CAF50)
        someTracked -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    val statusLabel = when {
        allTracked -> "Fully tracked"
        someTracked -> "Partially tracked"
        else -> "Not tracked"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        biomarker.clinicalName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = statusColor
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                biomarker.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Proxy metrics status
                Text(
                    "Wearable proxy signals",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                proxyStatus.forEach { (metric, tracked) ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (tracked) Icons.Default.CheckCircle
                            else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (tracked) Color(0xFF4CAF50) else Color.Gray
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            metric.readableName,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!tracked) {
                            Text(
                                " — needs additional device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "How Bios approximates this",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    biomarker.proxyExplanation,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Why it matters",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    biomarker.whyItMatters,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Normal ranges",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    biomarker.normalRange,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Limitations",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    biomarker.limitations,
                    style = MaterialTheme.typography.bodySmall
                )

                if (biomarker.citations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "References",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    biomarker.citations.forEach { citation ->
                        Text(
                            citation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
