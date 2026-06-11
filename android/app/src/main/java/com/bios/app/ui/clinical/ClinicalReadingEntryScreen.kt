package com.bios.app.ui.clinical

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.ManualReadingRepo
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bundle-style entry surface for clinical visits: one shared timestamp +
 * clinic + visit note span N readings. Triage rows (BP / SpO2 / pulse /
 * temp / RR / pain / GCS / O2 flow) get logged as one event the way a
 * clinician records them on a chart. Single-reading entries (owner reading
 * a cuff at home) work the same way with the bundle metadata left blank.
 *
 * Writes route through [ManualReadingRepo.addBundle] → SELF_REPORTED →
 * engine-isolated (docs/SELF_REPORTED_DATA_HOME.md decision 3).
 *
 * Biomarker (lab) entries stay on the dedicated [com.bios.app.ui.biomarkers.BiomarkerEntryScreen]
 * — they carry a different provenance vocabulary (lab/fasting/specimen)
 * that doesn't apply at the bedside. Per-row UI (metric picker, value
 * field, AVPU shortcut) lives in [ClinicalReadingRows].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalReadingEntryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    initialPreset: MeasurementPreset = MeasurementPreset.TRIAGE,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = viewModel.manualReadingRepo

    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var clinicName by remember { mutableStateOf("") }
    var visitNote by remember { mutableStateOf("") }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var preset by remember { mutableStateOf(initialPreset) }
    val rows = remember { mutableStateListOf<ReadingRowState>() }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    if (rows.isEmpty()) {
        defaultRowsFor(preset).forEach { rows.add(it) }
    }

    val sourceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Same fallback as biomarker entry — keep URI in-memory only
                // when the provider won't grant persistable permission.
            }
            sourceUri = picked
        }
    }

    val anyValid = rows.any { it.isFilled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add clinical reading") },
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Self-reported",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Readings you enter — from a cuff, pulse-ox, thermometer, scale, " +
                            "body-composition station, or a triage chart a clinician handed you — " +
                            "stay on your device. They show up in trends and FHIR export, but " +
                            "the baseline engine and anomaly detector ignore them. Single-point " +
                            "manual readings would otherwise poison sensor baselines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Captured on: ${formatClinicalDate(timestamp)}")
                    }

                    OutlinedTextField(
                        value = clinicName,
                        onValueChange = { clinicName = it },
                        label = { Text("Clinic / facility (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = visitNote,
                        onValueChange = { visitNote = it },
                        label = { Text("Visit note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Triage chart (PDF or photo)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { sourceLauncher.launch(CLINICAL_SOURCE_MIME_TYPES) },
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
            }

            Text(
                "Readings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = preset == MeasurementPreset.TRIAGE,
                    onClick = {
                        if (preset != MeasurementPreset.TRIAGE) {
                            preset = MeasurementPreset.TRIAGE
                            rows.clear()
                            defaultRowsFor(MeasurementPreset.TRIAGE).forEach { rows.add(it) }
                        }
                    },
                    label = { Text("Triage") },
                )
                FilterChip(
                    selected = preset == MeasurementPreset.BODY_COMPOSITION,
                    onClick = {
                        if (preset != MeasurementPreset.BODY_COMPOSITION) {
                            preset = MeasurementPreset.BODY_COMPOSITION
                            rows.clear()
                            defaultRowsFor(MeasurementPreset.BODY_COMPOSITION).forEach { rows.add(it) }
                        }
                    },
                    label = { Text("Body composition") },
                )
                FilterChip(
                    selected = preset == MeasurementPreset.BLOOD_PRESSURE,
                    onClick = {
                        if (preset != MeasurementPreset.BLOOD_PRESSURE) {
                            preset = MeasurementPreset.BLOOD_PRESSURE
                            rows.clear()
                            defaultRowsFor(MeasurementPreset.BLOOD_PRESSURE).forEach { rows.add(it) }
                        }
                    },
                    label = { Text("Blood pressure") },
                )
            }

            rows.forEachIndexed { index, row ->
                ReadingRowCard(
                    state = row,
                    onChange = { rows[index] = it },
                    onRemove = { rows.removeAt(index) },
                    canRemove = rows.size > 1,
                )
            }

            FilledTonalButton(
                onClick = { rows.add(ReadingRowState()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Add another reading")
            }

            OutlinedButton(
                onClick = {
                    val bundle = rows.mapNotNull { it.toBundleReading() }
                    if (bundle.isEmpty()) {
                        saveMessage = "Add at least one filled reading before saving."
                        return@OutlinedButton
                    }
                    val capturedAt = timestamp
                    val clinic = clinicName.ifBlank { null }
                    val note = visitNote.ifBlank { null }
                    val uri = sourceUri?.toString()
                    scope.launch {
                        repo.addBundle(
                            timestamp = capturedAt,
                            clinicName = clinic,
                            visitNote = note,
                            sourceUri = uri,
                            readings = bundle,
                        )
                        saveMessage = "Saved ${bundle.size} reading${if (bundle.size == 1) "" else "s"}."
                        rows.clear()
                        defaultRowsFor(preset).forEach { rows.add(it) }
                        clinicName = ""
                        visitNote = ""
                        sourceUri = null
                    }
                },
                enabled = anyValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            saveMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { timestamp = it }
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

private val CLINICAL_SOURCE_MIME_TYPES: Array<String> = arrayOf(
    "image/jpeg",
    "image/png",
    "application/pdf",
)

private fun releasePersistableRead(context: Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: SecurityException) {
        // Permission was never persisted or already revoked — nothing to release.
    }
}

private fun formatClinicalDate(epochMs: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(epochMs))
