package com.bios.app.ui.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.AlertCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScreen(viewModel: AppViewModel) {
    val entries by viewModel.timelineEntries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshTimeline()
    }

    if (entries.isEmpty()) {
        EmptyTimeline()
        return
    }

    // Group by date
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val grouped = remember(entries) {
        entries.groupBy { dateFormat.format(Date(it.detectedAt)) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text(
                "Health Journal",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${entries.size} entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // Stats summary
            StatsSummary(entries)
            Spacer(Modifier.height(8.dp))
        }

        grouped.forEach { (dateKey, dayEntries) ->
            item {
                val date = dateFormat.parse(dateKey)
                Text(
                    displayFormat.format(date!!),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(dayEntries, key = { it.id }) { anomaly ->
                TimelineEntry(
                    anomaly = anomaly,
                    timeFormat = timeFormat,
                    onAcknowledge = { viewModel.acknowledgeAlert(anomaly.id) },
                    onSaveFeedback = { input ->
                        viewModel.saveAlertFeedback(
                            anomalyId = anomaly.id,
                            feltSick = input.feltSick,
                            visitedDoctor = input.visitedDoctor,
                            diagnosis = input.diagnosis,
                            symptoms = input.symptoms,
                            notes = input.notes,
                            outcomeAccurate = input.outcomeAccurate
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineEntry(
    anomaly: Anomaly,
    timeFormat: SimpleDateFormat,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (com.bios.app.ui.components.FeedbackInput) -> Unit
) {
    val tier = AlertTier.fromLevel(anomaly.severity)

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline rail
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = tierColor(tier),
                modifier = Modifier.size(10.dp)
            )
            HorizontalDivider(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                timeFormat.format(Date(anomaly.detectedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            AlertCard(
                anomaly = anomaly,
                onAcknowledge = onAcknowledge,
                onSaveFeedback = onSaveFeedback
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatsSummary(entries: List<Anomaly>) {
    val withFeedback = entries.count { it.feedbackAt != null }
    val accurate = entries.count { it.outcomeAccurate == true }
    val inaccurate = entries.count { it.outcomeAccurate == false }
    val doctorVisits = entries.count { it.visitedDoctor == true }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Alerts", entries.size.toString())
            StatItem("Journaled", "$withFeedback/${entries.size}")
            if (accurate + inaccurate > 0) {
                val pct = (accurate * 100) / (accurate + inaccurate)
                StatItem("Accuracy", "$pct%")
            }
            if (doctorVisits > 0) {
                StatItem("Dr. Visits", doctorVisits.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyTimeline() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No journal entries yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "When Bios detects something unusual, it will appear here. " +
                    "You can record how you're feeling and track your health over time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun tierColor(tier: AlertTier): Color {
    return when (tier) {
        AlertTier.OBSERVATION -> Color.Gray
        AlertTier.NOTICE -> Color(0xFFFFC107)
        AlertTier.ADVISORY -> Color(0xFFFF9800)
        AlertTier.URGENT -> Color(0xFFF44336)
    }
}
