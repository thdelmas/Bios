package com.bios.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.ui.components.metricMatchesQuery
import com.bios.contracts.MetricType

/**
 * Owner-driven curation of the "Today's Vitals" grid: add any contract
 * metric, remove, and reorder. Pull-side by construction — the owner opens
 * this sheet, the owner decides; Bios proposes nothing.
 *
 * The working copy only persists on Save, so a dismissed sheet leaves the
 * stored selection untouched. At least one tile must remain — an empty
 * grid is indistinguishable from a broken one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalsEditSheet(
    tiles: List<MetricType>,
    onSave: (List<MetricType>) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember { mutableStateOf(tiles) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Vitals tiles", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose what the Read grid shows. Top to bottom here is left to right there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AddMetricSearch(
                existing = working,
                onAdd = { working = working + it },
            )

            working.forEachIndexed { index, metric ->
                TileRow(
                    metric = metric,
                    canMoveUp = index > 0,
                    canMoveDown = index < working.lastIndex,
                    canRemove = working.size > 1,
                    onMove = { delta ->
                        val target = index + delta
                        working = working.toMutableList()
                            .apply { add(target, removeAt(index)) }
                    },
                    onRemove = { working = working - metric },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { working = VitalsTileStore.DEFAULT_TILES }) {
                    Text("Reset to default")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(working) }) {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Search over every contract metric not already on the grid; tapping a
 * result appends it to the working set. Mirrors HomeScreen's
 * MetricSearchBar anatomy so the two search surfaces feel identical.
 */
@Composable
private fun AddMetricSearch(
    existing: List<MetricType>,
    onAdd: (MetricType) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results by remember(existing) {
        derivedStateOf {
            if (query.isBlank()) emptyList()
            else MetricType.entries
                .filter { it !in existing && metricMatchesQuery(it, query) }
                .sortedBy { it.readableName }
                .take(12)
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Add a metric — Weight, RHR, ApoB…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (results.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(results) { metric ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAdd(metric)
                                query = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(metric.readableName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            metric.unit.symbol.ifEmpty { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileRow(
    metric: MetricType,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            vitalsTileIcon(metric),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            vitalsTileLabel(metric),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Default.Close, contentDescription = "Remove tile")
        }
    }
}
