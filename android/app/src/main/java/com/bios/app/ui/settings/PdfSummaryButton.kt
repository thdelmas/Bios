package com.bios.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bios.app.export.PdfReportExporter
import com.bios.app.ui.AppViewModel
import com.bios.contracts.MetricType
import kotlinx.coroutines.launch

/**
 * Fourth export button alongside JSON / CSV / FHIR. Self-contained so
 * [SettingsScreen] only needs to call this composable — keeps that file
 * under the 500-line limit and groups all PDF-summary UI in one place.
 *
 * The dialog asks for two owner inputs: a focal metric (the chart axis the
 * doctor will look at first) and an optional free-text note (one to two
 * lines, e.g., "Visit on 2026-05-22 — fatigue + palpitations"). Both
 * mirror the MVP scope in issue #104.
 */
@Composable
fun PdfSummaryButton(
    viewModel: AppViewModel,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var focalMetric by remember { mutableStateOf(MetricType.HEART_RATE) }
    var ownerNote by remember { mutableStateOf("") }

    // Save-to-device path: the system picker returns a destination URI, then we
    // generate the PDF (with the metric/note the owner just chose) and stream it
    // there. Works offline with no other app installed — the reliable path on
    // degoogled builds where the share sheet may be empty.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                try {
                    val file = PdfReportExporter(context, viewModel.db).exportToPdf(
                        focalMetric = focalMetric,
                        ownerNote = ownerNote.trim().ifBlank { null },
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    isExporting = false
                }
            }
        }
    }

    OutlinedButton(
        onClick = { showDialog = true },
        enabled = enabled && !isExporting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isExporting) "Generating PDF…" else "PDF summary (for doctors)")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("PDF summary") },
            text = {
                Column {
                    Text(
                        "Pick the metric to feature on the chart and (optionally) " +
                            "add a short note for the doctor. The PDF is generated " +
                            "on-device — save it here or share it.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Focal metric")
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PDF_FOCAL_METRICS.forEach { (metric, label) ->
                            FilterChip(
                                selected = focalMetric == metric,
                                onClick = { focalMetric = metric },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = ownerNote,
                        onValueChange = { ownerNote = it.take(240) },
                        label = { Text("Note for the doctor (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        enabled = !isExporting,
                        onClick = {
                            showDialog = false
                            saveLauncher.launch("bios_doctor_summary.pdf")
                        },
                    ) { Text("Save to device") }
                    TextButton(
                        enabled = !isExporting,
                        onClick = {
                            val pickedNote = ownerNote.trim().ifBlank { null }
                            val pickedMetric = focalMetric
                            showDialog = false
                            isExporting = true
                            scope.launch {
                                try {
                                    val file = PdfReportExporter(context, viewModel.db).exportToPdf(
                                        focalMetric = pickedMetric,
                                        ownerNote = pickedNote,
                                    )
                                    shareFile(
                                        context, file, "application/pdf",
                                        "Share summary with your doctor",
                                    )
                                } finally {
                                    isExporting = false
                                }
                            }
                        },
                    ) { Text("Share") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private val PDF_FOCAL_METRICS = listOf(
    MetricType.HEART_RATE to "Heart Rate",
    MetricType.HEART_RATE_VARIABILITY to "HRV",
    MetricType.RESTING_HEART_RATE to "Resting HR",
    MetricType.BLOOD_OXYGEN to "SpO2",
    MetricType.RESPIRATORY_RATE to "Resp. Rate",
    MetricType.SKIN_TEMPERATURE_DEVIATION to "Skin Temp",
    MetricType.SLEEP_DURATION to "Sleep",
    MetricType.STEPS to "Steps",
)
