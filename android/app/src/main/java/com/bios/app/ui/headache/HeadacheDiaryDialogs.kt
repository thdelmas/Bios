package com.bios.app.ui.headache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.model.HeadacheType
import com.bios.app.model.MigraineTrigger

/**
 * Add-entry dialogs for the headache & migraine diary surface
 * (issue #207). Split into its own file so [HeadacheDiaryScreen] stays
 * under the 500-line cap.
 *
 * Both dialogs follow the same shape used by [com.bios.app.ui.medications]
 * and [com.bios.app.ui.bbt] — `OutlinedTextField`s, a `Slider` for 0-10
 * intensity, and chip-row selection where the input is enum-bounded.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddMigraineAttackDialog(
    onDismiss: () -> Unit,
    onSave: (
        onsetMs: Long,
        peakIntensity: Int,
        aura: Boolean,
        triggers: Set<MigraineTrigger>,
        medicationTaken: String?,
        notes: String?,
    ) -> Unit,
) {
    val onsetMs = remember { System.currentTimeMillis() }
    var peakIntensity by remember { mutableIntStateOf(5) }
    var aura by remember { mutableStateOf(false) }
    var triggers by remember { mutableStateOf(setOf<MigraineTrigger>()) }
    var medication by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log migraine attack") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Peak intensity: $peakIntensity/10", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = peakIntensity.toFloat(),
                    onValueChange = { peakIntensity = it.toInt().coerceIn(0, 10) },
                    valueRange = 0f..10f,
                    steps = 9,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Checkbox(checked = aura, onCheckedChange = { aura = it })
                    Text("With aura", style = MaterialTheme.typography.bodySmall)
                }
                Text("Triggers", style = MaterialTheme.typography.labelMedium)
                TriggerChipRow(
                    selected = triggers,
                    onToggle = { trigger ->
                        triggers = if (trigger in triggers) triggers - trigger else triggers + trigger
                    },
                )
                OutlinedTextField(
                    value = medication,
                    onValueChange = { medication = it },
                    label = { Text("Acute treatment (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    onsetMs,
                    peakIntensity,
                    aura,
                    triggers,
                    medication.trim().takeIf { it.isNotBlank() },
                    notes.trim().takeIf { it.isNotBlank() },
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TriggerChipRow(
    selected: Set<MigraineTrigger>,
    onToggle: (MigraineTrigger) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MigraineTrigger.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (trigger in row) {
                    FilterChip(
                        selected = trigger in selected,
                        onClick = { onToggle(trigger) },
                        label = {
                            Text(trigger.name.lowercase().replace('_', ' '))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddHeadacheLogDialog(
    onDismiss: () -> Unit,
    onSave: (
        timestampMs: Long,
        intensity: Int,
        type: HeadacheType,
        durationMinutes: Int?,
        medicationTaken: String?,
        notes: String?,
    ) -> Unit,
) {
    val timestampMs = remember { System.currentTimeMillis() }
    var intensity by remember { mutableIntStateOf(5) }
    var type by remember { mutableStateOf(HeadacheType.TENSION) }
    var duration by remember { mutableStateOf("") }
    var medication by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log headache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Intensity: $intensity/10", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = intensity.toFloat(),
                    onValueChange = { intensity = it.toInt().coerceIn(0, 10) },
                    valueRange = 0f..10f,
                    steps = 9,
                )
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeadacheType.entries.forEach { ht ->
                        FilterChip(
                            selected = type == ht,
                            onClick = { type = ht },
                            label = { Text(ht.name.lowercase()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration in minutes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = medication,
                    onValueChange = { medication = it },
                    label = { Text("Acute treatment (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    timestampMs,
                    intensity,
                    type,
                    duration.trim().toIntOrNull(),
                    medication.trim().takeIf { it.isNotBlank() },
                    notes.trim().takeIf { it.isNotBlank() },
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
