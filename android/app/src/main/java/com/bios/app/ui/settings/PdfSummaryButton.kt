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
    encrypt: Boolean = false,
    passphrase: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var focalMetric by remember { mutableStateOf(MetricType.HEART_RATE) }
    var ownerNote by remember { mutableStateOf("") }

    suspend fun producePdf(): java.io.File =
        PdfReportExporter(context, viewModel.db).exportToPdf(
            focalMetric = focalMetric,
            ownerNote = ownerNote.trim().ifBlank { null },
        )

    // Save-to-device path: the system picker returns a destination URI, then we
    // generate the PDF (with the metric/note the owner just chose), optionally
    // wrap it in an encrypted zip, and stream it there. Works offline with no
    // other app installed — the reliable path on degoogled builds where the
    // share sheet may be empty. CreateDocument's mime is fixed at composition,
    // so we keep one launcher per delivered type and pick by [encrypt] on tap.
    fun onSaveTarget(uri: android.net.Uri?) {
        if (uri == null) return
        isExporting = true
        scope.launch {
            try {
                val file = deliverExport(context, producePdf(), encrypt, passphrase)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
            } finally {
                isExporting = false
            }
        }
    }
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> onSaveTarget(uri) }
    val saveZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> onSaveTarget(uri) }

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
        PdfSummaryDialog(
            focalMetric = focalMetric,
            onFocalChange = { focalMetric = it },
            ownerNote = ownerNote,
            onNoteChange = { ownerNote = it.take(240) },
            isExporting = isExporting,
            onSave = {
                showDialog = false
                if (encrypt) saveZipLauncher.launch("bios_doctor_summary.pdf.zip")
                else savePdfLauncher.launch("bios_doctor_summary.pdf")
            },
            onShare = {
                showDialog = false
                isExporting = true
                scope.launch {
                    try {
                        val file = deliverExport(context, producePdf(), encrypt, passphrase)
                        shareFile(
                            context, file,
                            if (encrypt) "application/zip" else "application/pdf",
                            "Share summary with your doctor",
                        )
                    } finally {
                        isExporting = false
                    }
                }
            },
            onDismiss = { showDialog = false },
        )
    }
}

/** Options dialog for the PDF summary: focal-metric chips, optional note, and
 *  the save / share / cancel actions. Extracted to keep [PdfSummaryButton] short. */
@Composable
private fun PdfSummaryDialog(
    focalMetric: MetricType,
    onFocalChange: (MetricType) -> Unit,
    ownerNote: String,
    onNoteChange: (String) -> Unit,
    isExporting: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                            onClick = { onFocalChange(metric) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ownerNote,
                    onValueChange = onNoteChange,
                    label = { Text("Note for the doctor (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 2,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !isExporting, onClick = onSave) { Text("Save to device") }
                TextButton(enabled = !isExporting, onClick = onShare) { Text("Share") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
