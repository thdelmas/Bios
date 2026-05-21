package com.bios.app.ui.immunisations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.ImmunizationRepo
import com.bios.app.model.ImmunizationRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Immunisation Record. Closes audit gap §2.3.
 *
 * Free-text + CVX-coded entries; both flow through the same
 * [ImmunizationRepo]. The screening-cadence engine (#155) reads this
 * surface to compute "due for shingles" / "due for flu this season"
 * prompts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmunisationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) {
        ImmunizationRepo(BiosDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<ImmunizationRecord>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        records = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Immunisation Record") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Immunisation record", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Recording your vaccines lets Bios surface what's coming due " +
                            "(flu this season, shingles after 50, Tdap booster every 10 years) " +
                            "and travels with the data you share with a clinician. " +
                            "No reminders, no nudges — record on your own terms; review when you ask.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add vaccine")
                    }
                }
            }

            if (records.isEmpty()) {
                Text(
                    "No vaccines on record. Tap \"Add vaccine\" to record what you've received.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(records, key = { it.id }) { rec ->
                        ImmunisationRow(
                            record = rec,
                            onDelete = {
                                scope.launch {
                                    repo.remove(rec.id)
                                    refresh()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddImmunisationDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, cvxCode, date, doseNumber, lotNumber, note ->
                scope.launch {
                    repo.add(
                        vaccineName = name,
                        occurrenceDate = date,
                        cvxCode = cvxCode,
                        doseNumber = doseNumber,
                        lotNumber = lotNumber,
                        note = note,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ImmunisationRow(
    record: ImmunizationRecord,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    record.vaccineName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                record.doseNumber?.let {
                    Text(
                        "Dose $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("Given ${formatDate(record.occurrenceDate)}")
                    record.cvxCode?.let { append(" • CVX $it") }
                    record.lotNumber?.let { append(" • Lot $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            record.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { showActions = !showActions }) {
                Text(if (showActions) "Hide actions" else "Actions")
            }
            if (showActions) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddImmunisationDialog(
    onDismiss: () -> Unit,
    onSave: (
        name: String, cvxCode: String?, date: Long,
        doseNumber: Int?, lotNumber: String?, note: String?
    ) -> Unit,
) {
    var showCatalogPicker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VaccineCatalogEntry?>(null) }
    var freeTextName by remember { mutableStateOf("") }
    var doseText by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val effectiveName = selected?.displayName ?: freeTextName
    val effectiveCvx = selected?.cvxCode

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add vaccine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showCatalogPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selected?.displayName ?: "Pick from catalog")
                }
                if (selected == null) {
                    OutlinedTextField(
                        value = freeTextName,
                        onValueChange = { freeTextName = it },
                        label = { Text("Or type vaccine name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Given on: ${formatDate(date)}")
                }
                OutlinedTextField(
                    value = doseText,
                    onValueChange = { v -> doseText = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Dose number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lot,
                    onValueChange = { lot = it.take(40) },
                    label = { Text("Lot number (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        effectiveName.trim(),
                        effectiveCvx,
                        date,
                        doseText.toIntOrNull(),
                        lot.trim().takeIf { it.isNotBlank() },
                        note.trim().takeIf { it.isNotBlank() },
                    )
                },
                enabled = effectiveName.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCatalogPicker) {
        AlertDialog(
            onDismissRequest = { showCatalogPicker = false },
            title = { Text("Pick vaccine") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        TextButton(
                            onClick = {
                                selected = null
                                showCatalogPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Other (type name below)") }
                    }
                    items(VaccineCatalog.entries) { entry ->
                        TextButton(
                            onClick = {
                                selected = entry
                                freeTextName = ""
                                showCatalogPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(entry.displayName) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCatalogPicker = false }) { Text("Cancel") }
            }
        )
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
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
