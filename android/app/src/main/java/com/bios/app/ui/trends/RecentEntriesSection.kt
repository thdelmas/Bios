package com.bios.app.ui.trends

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.ui.RecentEntry
import com.bios.contracts.MetricType

/**
 * Adds the "Recent entries" header + entry rows as LazyColumn items so the
 * full per-metric history can render without composing every row eagerly.
 * Lives as a LazyListScope extension because the per-metric screen needs
 * virtualization — Steps can have tens of thousands of entries and the
 * old Column(verticalScroll) implementation OOM'd at 256 MB.
 */
internal fun LazyListScope.recentEntriesSection(
    metric: MetricType,
    entries: List<RecentEntry>,
    loaded: Boolean,
    selectedIds: Set<String>,
    onLongPress: (String) -> Unit,
    onToggle: (String) -> Unit,
    onDeleteOne: (RecentEntry) -> Unit,
    onCancelSelection: () -> Unit,
    onRequestBatchDelete: () -> Unit,
) {
    val inSelectMode = selectedIds.isNotEmpty()

    item(key = "recent-entries-header") {
        RecentEntriesHeader(
            inSelectMode = inSelectMode,
            selectedCount = selectedIds.size,
            onRequestBatchDelete = onRequestBatchDelete,
            onCancelSelection = onCancelSelection,
        )
    }

    if (!loaded) {
        item(key = "recent-entries-loading") { RecentEntriesPlaceholder("Loading…") }
        return
    }

    if (entries.isEmpty()) {
        item(key = "recent-entries-empty") {
            RecentEntriesPlaceholder("No ${metric.readableName.lowercase()} entries yet.")
        }
        return
    }

    if (!inSelectMode) {
        item(key = "recent-entries-hint") {
            Text(
                "Long-press a row to multi-select.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    itemsIndexed(
        entries,
        key = { _, entry -> entry.reading.id },
    ) { index, entry ->
        Column {
            EntryRow(
                metric = metric,
                entry = entry,
                inSelectMode = inSelectMode,
                isSelected = entry.reading.id in selectedIds,
                onLongPress = { onLongPress(entry.reading.id) },
                onToggle = { onToggle(entry.reading.id) },
                onDelete = { onDeleteOne(entry) },
            )
            if (index < entries.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RecentEntriesHeader(
    inSelectMode: Boolean,
    selectedCount: Int,
    onRequestBatchDelete: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (inSelectMode) "$selectedCount selected" else "Recent entries",
            style = MaterialTheme.typography.titleMedium,
        )
        if (inSelectMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRequestBatchDelete) { Text("Delete") }
                IconButton(onClick = onCancelSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
            }
        }
    }
}

@Composable
private fun RecentEntriesPlaceholder(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    metric: MetricType,
    entry: RecentEntry,
    inSelectMode: Boolean,
    isSelected: Boolean,
    onLongPress: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowBg = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(
                onClick = { if (inSelectMode) onToggle() },
                onLongClick = onLongPress,
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMetricValue(entry.reading.value, metric.unit),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatEntryTimestamp(entry.reading.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.context.clinicName?.takeIf { it.isNotBlank() }?.let { src ->
                Text(
                    "From: $src",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.context.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!inSelectMode) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
