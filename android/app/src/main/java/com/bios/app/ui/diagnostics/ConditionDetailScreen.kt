package com.bios.app.ui.diagnostics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.DeviationDirection
import com.bios.app.ui.AppViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionDetailScreen(
    patternId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val results by viewModel.diagnosticResults.collectAsState()
    val result = remember(patternId, results) {
        results.firstOrNull { it.pattern.id == patternId }
    }
    val pattern = result?.pattern ?: remember(patternId) {
        ConditionPatterns.all.firstOrNull { it.id == patternId }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pattern?.title ?: "Condition Details") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (pattern == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Condition not found.")
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category
            Text(
                pattern.category.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Probability card
            if (result != null) {
                ProbabilityCard(result)
            }

            // Overview
            Text(
                pattern.explanation,
                style = MaterialTheme.typography.bodyMedium
            )

            // Signal breakdown
            if (result != null) {
                SignalBreakdownCard(result)
            }

            if (pattern.earlyDetection.isNotEmpty()) {
                KnowledgeSection(
                    icon = Icons.Default.Visibility,
                    title = "Early Detection",
                    color = Color(0xFF2196F3),
                    content = pattern.earlyDetection
                )
            }

            if (pattern.prevention.isNotEmpty()) {
                KnowledgeSection(
                    icon = Icons.Default.Shield,
                    title = "Prevention",
                    color = Color(0xFF4CAF50),
                    content = pattern.prevention
                )
            }

            if (pattern.healing.isNotEmpty()) {
                KnowledgeSection(
                    icon = Icons.Default.Healing,
                    title = "Healing",
                    color = Color(0xFF009688),
                    content = pattern.healing
                )
            }

            if (pattern.risks.isNotEmpty()) {
                KnowledgeSection(
                    icon = Icons.Default.Warning,
                    title = "Risks If Untreated",
                    color = Color(0xFFFF9800),
                    content = pattern.risks
                )
            }

            // References
            if (pattern.references.isNotEmpty()) {
                Text(
                    "Research References",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                pattern.references.forEach { ref ->
                    Text(
                        ref,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProbabilityCard(result: DiagnosticResult) {
    val pct = (result.probability * 100).toInt()
    val color = probabilityColor(result.probability)

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
                    "Current Match Score",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (result.hasEnoughData) "$pct%" else "Insufficient data",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (result.hasEnoughData) color
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.hasEnoughData) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { result.probability.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${result.activeSignalCount} of ${result.signals.size} signals active (${result.pattern.minActiveSignals} required)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SignalBreakdownCard(result: DiagnosticResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Signal Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            // Header row
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Metric",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "Current",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp)
                )
                Text(
                    "Threshold",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Box(modifier = Modifier.width(20.dp))
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))

            result.signals.forEach { signal ->
                SignalDetailRow(signal)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SignalDetailRow(signal: SignalStatus) {
    val dirLabel = when (signal.direction) {
        DeviationDirection.ABOVE -> ">"
        DeviationDirection.BELOW -> "<"
        DeviationDirection.IRREGULAR -> "||>"
    }
    val thresholdLabel = when (signal.direction) {
        DeviationDirection.ABOVE -> "+${String.format("%.1f", signal.thresholdSigma)}σ"
        DeviationDirection.BELOW -> "-${String.format("%.1f", signal.thresholdSigma)}σ"
        DeviationDirection.IRREGULAR -> "±${String.format("%.1f", signal.thresholdSigma)}σ"
    }

    val valueText = when {
        !signal.hasBaseline -> "—"
        signal.currentZScore == null -> "—"
        else -> {
            val sign = if (signal.currentZScore > 0) "+" else ""
            "$sign${String.format("%.1f", signal.currentZScore)}σ"
        }
    }

    val valueColor = when {
        !signal.hasBaseline || signal.currentZScore == null ->
            Color.Gray
        signal.isActive -> Color(0xFFF44336)
        abs(signal.currentZScore) > signal.thresholdSigma * 0.5 -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            signal.metricType.readableName,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            valueText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.width(64.dp)
        )
        Text(
            "$dirLabel $thresholdLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Icon(
            imageVector = if (signal.isActive) Icons.Default.Circle
                else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (signal.isActive) "Active" else "Inactive",
            modifier = Modifier.size(12.dp),
            tint = if (signal.isActive) Color(0xFFF44336)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

private fun probabilityColor(probability: Double): Color = when {
    probability >= 0.75 -> Color(0xFFF44336)
    probability >= 0.50 -> Color(0xFFFF9800)
    probability >= 0.25 -> Color(0xFFFFC107)
    else -> Color(0xFF4CAF50)
}

@Composable
private fun KnowledgeSection(
    icon: ImageVector,
    title: String,
    color: Color,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
