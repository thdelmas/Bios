package com.bios.app.ui.biomarkers

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
import com.bios.app.export.FhirImportSummary
import com.bios.app.export.FhirImporter
import com.bios.app.model.MetricReading
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricDomain
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val recent by viewModel.recentBiomarkers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        Text("Drawn on: ${formatDate(selectedDate)}")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.addManualBiomarker(selected, parsed!!, selectedDate)
                            valueText = ""
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
                        RecentBiomarkerRow(reading)
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

@Composable
private fun RecentBiomarkerRow(reading: MetricReading) {
    val metric = MetricType.fromKey(reading.metricType)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                metric?.readableName ?: reading.metricType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatValue(reading.value)} ${metric?.unit?.symbol.orEmpty()}  ·  ${formatDate(reading.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

private fun formatValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.2f".format(value)
