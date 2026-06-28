package com.bios.app.ui.biomarkers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bios.app.labocr.Confidence
import com.bios.app.labocr.ExtractedReading
import com.bios.app.labocr.PanelMetadata
import com.bios.app.model.Specimen
import com.bios.app.ui.AppViewModel

/** A single review row's editable state (value text + whether it'll be saved). */
private class ScanRowState(val reading: ExtractedReading) {
    var valueText by mutableStateOf(trimZeros(reading.value))
    var checked by mutableStateOf(reading.confidence == Confidence.HIGH)
    val parsed: Double? get() = valueText.toDoubleOrNull()
}

/**
 * Pre-filled, editable batch review of an OCR'd lab report — the gate where
 * the owner confirms before anything is written (docs LAB_OCR_INGESTION.md
 * §6). Reuses the manual-entry field idioms so there's one editing UX. OCR
 * proposes; nothing persists until the owner taps Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScanReviewScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAddManually: () -> Unit,
) {
    val summary by viewModel.labScan.pending.collectAsState()
    val current = summary

    // Editable state, hoisted above the Scaffold so the date dialog shares its
    // scope. Keyed on `current` so a fresh scan re-seeds the form. remember()
    // is called unconditionally (current may be null) to respect hook rules.
    val rows = remember(current) {
        current?.readings.orEmpty().map { ScanRowState(it) }.toMutableStateList()
    }
    var date by remember(current) { mutableStateOf(current?.panel?.collectionDate) }
    var labName by remember(current) { mutableStateOf(current?.panel?.labName.orEmpty()) }
    var specimen by remember(current) { mutableStateOf(current?.panel?.specimen) }
    var showDatePicker by remember { mutableStateOf(false) }
    var specimenExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review scanned report") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.labScan.discard()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (current == null || current.isFileError) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(current?.fileError ?: "Nothing to review.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = {
                    viewModel.labScan.discard()
                    onBack()
                }) { Text("Back") }
            }
            return@Scaffold
        }

        val canSave = date != null &&
            rows.any { it.checked && (it.parsed?.let { v -> v > 0.0 } == true) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PanelHeaderCard(
                    date = date,
                    labName = labName,
                    specimen = specimen,
                    onPickDate = { showDatePicker = true },
                    onLabNameChange = { labName = it },
                    specimenExpanded = specimenExpanded,
                    onSpecimenExpandedChange = { specimenExpanded = it },
                    onSpecimenChange = { specimen = it },
                )
            }
            item {
                Text(
                    "Found ${rows.size} value${if (rows.size == 1) "" else "s"}. " +
                        "Checked rows will be saved. Amber rows were read with low confidence — verify them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rows) { row -> ScanReadingRow(row) }

            if (current.skipped.isNotEmpty()) {
                item {
                    Text(
                        "Couldn't read (${current.skipped.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(current.skipped) { s ->
                    Text(
                        "• ${s.reason}\n  ${s.rawLine}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { TextButton(onClick = onAddManually) { Text("+ Add a value manually") } }
            }

            item {
                Button(
                    onClick = {
                        val confirmed = rows
                            .filter { it.checked && (it.parsed?.let { v -> v > 0.0 } == true) }
                            .map { it.reading.copy(value = it.parsed!!) }
                        val panel = PanelMetadata(
                            labName = labName.ifBlank { null },
                            collectionDate = date,
                            specimen = specimen,
                            sourceUri = current.panel.sourceUri,
                        )
                        viewModel.labScan.confirm(confirmed, panel)
                        viewModel.refreshRecentBiomarkers()
                        onBack()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (date == null) "Set a date to save" else "Save checked values")
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelHeaderCard(
    date: Long?,
    labName: String,
    specimen: Specimen?,
    onPickDate: () -> Unit,
    onLabNameChange: (String) -> Unit,
    specimenExpanded: Boolean,
    onSpecimenExpandedChange: (Boolean) -> Unit,
    onSpecimenChange: (Specimen?) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Report details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onPickDate, modifier = Modifier.fillMaxWidth()) {
                Text(if (date == null) "Set collection date (required)" else "Drawn on: ${formatBiomarkerDate(date)}")
            }
            OutlinedTextField(
                value = labName,
                onValueChange = onLabNameChange,
                label = { Text("Lab / facility") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(expanded = specimenExpanded, onExpandedChange = onSpecimenExpandedChange) {
                OutlinedTextField(
                    value = specimen?.readable ?: "—",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Specimen") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specimenExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = specimenExpanded, onDismissRequest = { onSpecimenExpandedChange(false) }) {
                    DropdownMenuItem(text = { Text("—") }, onClick = {
                        onSpecimenChange(null); onSpecimenExpandedChange(false)
                    })
                    Specimen.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.readable) }, onClick = {
                            onSpecimenChange(s); onSpecimenExpandedChange(false)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanReadingRow(row: ScanRowState) {
    val low = row.reading.confidence == Confidence.LOW
    val container = if (low) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = container)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.checked, onCheckedChange = { row.checked = it })
                Text(
                    row.reading.metricType.readableName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.valueText,
                    onValueChange = { row.valueText = it.filter { c -> c.isDigit() || c == '.' } },
                    suffix = { Text(row.reading.metricType.unit.symbol.ifEmpty { "—" }) },
                    singleLine = true,
                    isError = row.checked && (row.parsed == null || (row.parsed ?: 0.0) <= 0.0),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                row.reading.rawLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (low) {
                Text(
                    "Low confidence — please verify",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

/** Drop a trailing ".0" so an OCR'd integer doesn't show as "55.0". */
private fun trimZeros(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
