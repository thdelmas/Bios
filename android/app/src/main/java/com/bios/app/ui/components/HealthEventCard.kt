package com.bios.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.bios.app.model.ActionItem
import com.bios.app.model.HealthEvent
import com.bios.app.model.HealthEventStatus
import com.bios.app.model.HealthEventType
import com.bios.app.ui.journal.ActionItemList

@Composable
fun HealthEventCard(
    event: HealthEvent,
    actionItems: List<ActionItem>,
    childEvents: List<HealthEvent>,
    onStatusChange: (HealthEventStatus) -> Unit,
    onAddFollowUp: () -> Unit,
    onToggleAction: (String, Boolean) -> Unit,
    onDeleteAction: (String) -> Unit,
    onAddAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val eventType = try { HealthEventType.valueOf(event.type) } catch (_: Exception) { HealthEventType.NOTE }
    val color = eventTypeColor(eventType)
    val status = try { HealthEventStatus.valueOf(event.status) } catch (_: Exception) { HealthEventStatus.OPEN }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.2f))
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventTypeBadge(eventType, color)
                Spacer(Modifier.width(8.dp))
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status) { newStatus -> onStatusChange(newStatus) }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))

                    // Description
                    event.description?.let { desc ->
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Child events (linked follow-ups)
                    if (childEvents.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Follow-ups",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                childEvents.forEach { child ->
                                    val childType = try {
                                        HealthEventType.valueOf(child.type)
                                    } catch (_: Exception) { HealthEventType.NOTE }
                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            eventTypeIcon(childType),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = eventTypeColor(childType)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "${childType.label}: ${child.title}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action items
                    if (actionItems.isNotEmpty() || status == HealthEventStatus.OPEN) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Action Items",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                ActionItemList(
                                    items = actionItems,
                                    onToggle = onToggleAction,
                                    onDelete = onDeleteAction,
                                    onAdd = if (status == HealthEventStatus.OPEN) onAddAction else null
                                )
                            }
                        }
                    }

                    // Add follow-up button
                    if (status == HealthEventStatus.OPEN) {
                        OutlinedButton(
                            onClick = onAddFollowUp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Add follow-up")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTypeBadge(type: HealthEventType, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                eventTypeIcon(type),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Text(
                text = type.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun StatusChip(
    status: HealthEventStatus,
    onStatusChange: (HealthEventStatus) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = status == HealthEventStatus.RESOLVED,
            onClick = { showMenu = true },
            label = {
                Text(
                    when (status) {
                        HealthEventStatus.OPEN -> "Open"
                        HealthEventStatus.RESOLVED -> "Resolved"
                        HealthEventStatus.DISMISSED -> "Dismissed"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            HealthEventStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onStatusChange(s)
                        showMenu = false
                    }
                )
            }
        }
    }
}

fun eventTypeIcon(type: HealthEventType): ImageVector {
    return when (type) {
        HealthEventType.SYMPTOM -> Icons.Default.Warning
        HealthEventType.HYPOTHESIS -> Icons.Default.Lightbulb
        HealthEventType.DOCTOR_VISIT -> Icons.Default.LocalHospital
        HealthEventType.DIAGNOSIS -> Icons.Default.MedicalServices
        HealthEventType.TREATMENT -> Icons.Default.Healing
        HealthEventType.NOTE -> Icons.Default.StickyNote2
    }
}

fun eventTypeColor(type: HealthEventType): Color {
    return when (type) {
        HealthEventType.SYMPTOM -> Color(0xFFFF9800)
        HealthEventType.HYPOTHESIS -> Color(0xFF9C27B0)
        HealthEventType.DOCTOR_VISIT -> Color(0xFF2196F3)
        HealthEventType.DIAGNOSIS -> Color(0xFF009688)
        HealthEventType.TREATMENT -> Color(0xFF4CAF50)
        HealthEventType.NOTE -> Color.Gray
    }
}
