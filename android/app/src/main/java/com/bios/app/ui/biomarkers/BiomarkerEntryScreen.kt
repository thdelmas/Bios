package com.bios.app.ui.biomarkers

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Row
import com.bios.app.data.BiomarkerContext
import com.bios.app.export.FhirImportSummary
import com.bios.app.export.FhirImporter
import com.bios.app.model.Specimen
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiomarkerEntryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val biomarkers = remember {
        MetricType.entries.filter { it.domain == MetricDomain.BIOMARKER }
    }
    var selected by remember { mutableStateOf(biomarkers.first()) }
    var valueText by remember { mutableStateOf("") }
    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickerExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<FhirImportSummary?>(null) }
    var labName by remember { mutableStateOf("") }
    var fasting by remember { mutableStateOf<Boolean?>(null) }
    var specimen by remember { mutableStateOf<Specimen?>(null) }
    var specimenExpanded by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var showContext by remember { mutableStateOf(false) }
    val recent by viewModel.recentBiomarkers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val labReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        // OpenDocument returns a content:// URI. Take a persistable read
        // permission so the URI survives reboots and the source app's
        // lifecycle. Without this, the URI works for the current process
        // only and is dead after the next launch.
        if (picked != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                sourceUri = picked
            } catch (_: SecurityException) {
                // Some providers (e.g. cached images) won't grant persistable
                // permission. Keep the URI in-memory only; the entry still
                // saves, but won't survive a reboot. Better than silently
                // dropping the owner's pick.
                sourceUri = picked
            }
        }
    }

    val fhirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    importSummary = FhirImporter.importFromUri(
                        context = context,
                        uri = uri,
                        repo = viewModel.biomarkerEntryRepo
                    )
                    viewModel.refreshRecentBiomarkers()
                } finally {
                    importing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshRecentBiomarkers() }

    val parsed = valueText.toDoubleOrNull()
    val isValid = parsed != null && parsed > 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add lab value") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Self-reported", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Lab values you enter here stay on your device. They show up in trends and FHIR export, " +
                            "but the baseline engine and anomaly detector ignore them — labs are diagnostic, not streaming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ExposedDropdownMenuBox(
                        expanded = pickerExpanded,
                        onExpandedChange = { pickerExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selected.readableName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Biomarker") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pickerExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = pickerExpanded,
                            onDismissRequest = { pickerExpanded = false }
                        ) {
                            biomarkers.forEach { metric ->
                                DropdownMenuItem(
                                    text = { Text(metric.readableName) },
                                    onClick = {
                                        selected = metric
                                        pickerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Value") },
                        suffix = { Text(selected.unit.symbol.ifEmpty { "—" }) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        isError = valueText.isNotEmpty() && !isValid,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Drawn on: ${formatBiomarkerDate(selectedDate)}")
                    }

                    TextButton(
                        onClick = { showContext = !showContext },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showContext) "Hide context" else "Add context (lab, fasting, specimen, note)")
                    }

                    if (showContext) {
                        OutlinedTextField(
                            value = labName,
                            onValueChange = { labName = it },
                            label = { Text("Lab / facility") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Fasting",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = fasting == true,
                                onClick = { fasting = if (fasting == true) null else true },
                                label = { Text("Yes") }
                            )
                            FilterChip(
                                selected = fasting == false,
                                onClick = { fasting = if (fasting == false) null else false },
                                label = { Text("No") }
                            )
                            FilterChip(
                                selected = fasting == null,
                                onClick = { fasting = null },
                                label = { Text("Unknown") }
                            )
                        }

                        ExposedDropdownMenuBox(
                            expanded = specimenExpanded,
                            onExpandedChange = { specimenExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = specimen?.readable ?: "—",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Specimen") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specimenExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = specimenExpanded,
                                onDismissRequest = { specimenExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("—") },
                                    onClick = {
                                        specimen = null
                                        specimenExpanded = false
                                    }
                                )
                                Specimen.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.readable) },
                                        onClick = {
                                            specimen = s
                                            specimenExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note (for your recall)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Lab report (PDF or photo)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { labReportLauncher.launch(LAB_REPORT_MIME_TYPES) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (sourceUri == null) "Attach…" else "Replace")
                            }
                            if (sourceUri != null) {
                                TextButton(onClick = {
                                    sourceUri?.let { releasePersistableRead(context, it) }
                                    sourceUri = null
                                }) { Text("Remove") }
                            }
                        }
                        sourceUri?.let {
                            Text(
                                "Attached: ${it.lastPathSegment ?: it.toString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val bioContext = BiomarkerContext(
                                labName = labName.ifBlank { null },
                                fasting = fasting,
                                specimen = specimen,
                                sourceUri = sourceUri?.toString(),
                                note = note.ifBlank { null }
                            )
                            viewModel.addManualBiomarker(selected, parsed!!, selectedDate, bioContext)
                            valueText = ""
                            labName = ""
                            fasting = null
                            specimen = null
                            note = ""
                            sourceUri = null
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Import from FHIR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Bring in lab results as a FHIR R4 JSON Bundle (Observation resources). " +
                            "Codes outside Bios's biomarker set are skipped and reported back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            fhirLauncher.launch(
                                arrayOf("application/json", "application/fhir+json", "*/*")
                            )
                        },
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (importing) "Importing…" else "Pick a FHIR file")
                    }
                }
            }

            Text("Recent entries", style = MaterialTheme.typography.titleSmall)
            if (recent.isEmpty()) {
                Text(
                    "No lab values entered yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(recent, key = { it.id }) { reading ->
                        RecentBiomarkerRow(reading, viewModel.biomarkerEntryRepo)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    importSummary?.let { summary ->
        FhirImportSummaryDialog(summary = summary, onDismiss = { importSummary = null })
    }
}

@Composable
private fun FhirImportSummaryDialog(summary: FhirImportSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (summary.isFileError) "Import failed"
                else "Imported ${summary.acceptedCount} reading" +
                    if (summary.acceptedCount == 1) "" else "s"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.isFileError) {
                    Text(summary.fileError!!, style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (summary.acceptedCount > 0) {
                        Text(
                            summary.accepted.joinToString("\n") { r ->
                                val mt = r.metricType
                                "• ${mt.readableName}: ${"%.2f".format(r.value)} ${mt.unit.symbol}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (summary.skippedCount > 0) {
                        Text(
                            "Skipped ${summary.skippedCount}:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            summary.skipped.joinToString("\n") { s ->
                                val code = s.loincCode?.let { " [$it]" } ?: ""
                                "• ${s.reason}$code"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/**
 * MIME types the lab-report picker is restricted to. The owner's archive
 * (Files / Drive / camera roll) typically delivers labs as one of these:
 * scanned PDFs from a portal, JPEG/PNG photos from the device camera.
 * Restricting the picker keeps random "PDF or photo or anything else"
 * accidents off the entry surface.
 */
internal val LAB_REPORT_MIME_TYPES: Array<String> = arrayOf(
    "image/jpeg",
    "image/png",
    "application/pdf",
)

private fun releasePersistableRead(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Permission was never persisted (some providers don't allow it),
        // or it has already been revoked. Either way, nothing to release.
    }
}

