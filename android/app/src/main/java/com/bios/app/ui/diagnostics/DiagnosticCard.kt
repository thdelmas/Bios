package com.bios.app.ui.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun DiagnosticCard(result: DiagnosticResult, onNavigateToDetail: (String) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    val pct = (result.probability * 100).toInt()
    val color = probabilityColor(result.probability)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        result.pattern.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        result.pattern.category.name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                            .replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    if (result.hasEnoughData) "$pct%" else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.hasEnoughData) color
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            if (result.hasEnoughData) {
                LinearProgressIndicator(
                    progress = { result.probability.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                Text(
                    "Insufficient baseline data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable detail
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Signal summary
                    Text(
                        "${result.activeSignalCount} of ${result.signals.size} signals active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    // Signal list
                    result.signals.forEach { signal ->
                        SignalRow(signal)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Explanation
                    Text(
                        result.pattern.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Suggested action
                    result.pattern.suggestedAction?.let { action ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                action,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // Learn more
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onNavigateToDetail(result.pattern.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Learn more")
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalRow(signal: SignalStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (signal.isActive) Icons.Default.Circle
                else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = if (signal.isActive) probabilityColor(1.0)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            signal.metricType.readableName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = when {
                !signal.hasBaseline -> "no baseline"
                signal.currentZScore == null -> "no data"
                else -> {
                    val dir = if (signal.currentZScore > 0) "+" else ""
                    "${dir}${String.format("%.1f", signal.currentZScore)}σ"
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                signal.isActive -> probabilityColor(1.0)
                !signal.hasBaseline || signal.currentZScore == null ->
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun probabilityColor(probability: Double): Color = when {
    probability >= 0.75 -> Color(0xFFF44336)
    probability >= 0.50 -> Color(0xFFFF9800)
    probability >= 0.25 -> Color(0xFFFFC107)
    else -> Color(0xFF4CAF50)
}
