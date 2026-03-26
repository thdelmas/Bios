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
import com.bios.app.model.ActionItem
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.model.HealthEvent
import com.bios.app.model.HealthEventStatus
import com.bios.app.model.HealthEventType
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.AlertCard
import com.bios.app.ui.components.HealthEventCard
import com.bios.app.ui.components.eventTypeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface TimelineItem {
    val timestamp: Long
    val id: String

    data class AlertItem(val anomaly: Anomaly) : TimelineItem {
        override val timestamp = anomaly.detectedAt
        override val id = anomaly.id
    }
    data class EventItem(val event: HealthEvent) : TimelineItem {
        override val timestamp = event.createdAt
        override val id = event.id
    }
}

@Composable
fun TimelineScreen(
    viewModel: AppViewModel,
    onRequestEventSheet: (parentEventId: String?) -> Unit
) {
    val anomalies by viewModel.timelineEntries.collectAsState()
    val healthEvents by viewModel.healthEvents.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshTimeline()
    }

    // Merge into unified timeline (only show root events, not child follow-ups)
    val rootEvents = remember(healthEvents) {
        healthEvents.filter { it.parentEventId == null }
    }
    val items = remember(anomalies, rootEvents) {
        val alertItems = anomalies.map { TimelineItem.AlertItem(it) }
        val eventItems = rootEvents.map { TimelineItem.EventItem(it) }
        (alertItems + eventItems).sortedByDescending { it.timestamp }
    }

    if (items.isEmpty()) {
        EmptyTimeline(onLogSymptom = { onRequestEventSheet(null) })
        return
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val grouped = remember(items) {
        items.groupBy { dateFormat.format(Date(it.timestamp)) }
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
                "${items.size} entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            StatsSummary(anomalies, healthEvents)
            Spacer(Modifier.height(8.dp))
        }

        grouped.forEach { (dateKey, dayItems) ->
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

            items(dayItems, key = { it.id }) { item ->
                when (item) {
                    is TimelineItem.AlertItem -> TimelineAlertEntry(
                        anomaly = item.anomaly,
                        timeFormat = timeFormat,
                        onAcknowledge = { viewModel.acknowledgeAlert(item.anomaly.id) },
                        onSaveFeedback = { input ->
                            viewModel.saveAlertFeedback(
                                anomalyId = item.anomaly.id,
                                feltSick = input.feltSick,
                                visitedDoctor = input.visitedDoctor,
                                diagnosis = input.diagnosis,
                                symptoms = input.symptoms,
                                notes = input.notes,
                                outcomeAccurate = input.outcomeAccurate
                            )
                        }
                    )
                    is TimelineItem.EventItem -> TimelineEventEntry(
                        event = item.event,
                        timeFormat = timeFormat,
                        viewModel = viewModel,
                        onAddFollowUp = { onRequestEventSheet(item.event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineAlertEntry(
    anomaly: Anomaly,
    timeFormat: SimpleDateFormat,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (com.bios.app.ui.components.FeedbackInput) -> Unit
) {
    val tier = AlertTier.fromLevel(anomaly.severity)

    Row(modifier = Modifier.fillMaxWidth()) {
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
                modifier = Modifier.width(1.dp).weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

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
private fun TimelineEventEntry(
    event: HealthEvent,
    timeFormat: SimpleDateFormat,
    viewModel: AppViewModel,
    onAddFollowUp: () -> Unit
) {
    val eventType = try { HealthEventType.valueOf(event.type) } catch (_: Exception) { HealthEventType.NOTE }
    var actionItems by remember { mutableStateOf(emptyList<ActionItem>()) }
    var childEvents by remember { mutableStateOf(emptyList<HealthEvent>()) }

    LaunchedEffect(event.id) {
        actionItems = viewModel.getActionItemsForEvent(event.id)
        childEvents = viewModel.getChildEvents(event.id)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Icon(
                Icons.Default.Circle,
                contentDescription = null,
                tint = eventTypeColor(eventType),
                modifier = Modifier.size(10.dp)
            )
            HorizontalDivider(
                modifier = Modifier.width(1.dp).weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                timeFormat.format(Date(event.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            HealthEventCard(
                event = event,
                actionItems = actionItems,
                childEvents = childEvents,
                onStatusChange = { viewModel.updateHealthEventStatus(event.id, it) },
                onAddFollowUp = onAddFollowUp,
                onToggleAction = { id, completed -> viewModel.toggleActionItem(id, completed) },
                onDeleteAction = { id -> viewModel.deleteActionItem(id) },
                onAddAction = { desc -> viewModel.createActionItem(event.id, desc) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatsSummary(anomalies: List<Anomaly>, healthEvents: List<HealthEvent>) {
    val withFeedback = anomalies.count { it.feedbackAt != null }
    val accurate = anomalies.count { it.outcomeAccurate == true }
    val inaccurate = anomalies.count { it.outcomeAccurate == false }
    val doctorVisits = anomalies.count { it.visitedDoctor == true } +
        healthEvents.count { it.type == HealthEventType.DOCTOR_VISIT.name }
    val openSymptoms = healthEvents.count {
        it.status == HealthEventStatus.OPEN.name &&
            it.type == HealthEventType.SYMPTOM.name
    }

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
            StatItem("Alerts", anomalies.size.toString())
            if (anomalies.isNotEmpty()) {
                StatItem("Journaled", "$withFeedback/${anomalies.size}")
            }
            if (accurate + inaccurate > 0) {
                val pct = (accurate * 100) / (accurate + inaccurate)
                StatItem("Accuracy", "$pct%")
            }
            if (doctorVisits > 0) {
                StatItem("Dr. Visits", doctorVisits.toString())
            }
            if (openSymptoms > 0) {
                StatItem("Open", openSymptoms.toString())
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
private fun EmptyTimeline(onLogSymptom: () -> Unit) {
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
                    "You can also log symptoms, doctor visits, and treatments yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onLogSymptom) {
                Text("Log a symptom")
            }
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
