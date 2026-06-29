package com.bios.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bios.app.engine.BaselineEngine
import com.bios.app.export.DataExporter
import com.bios.app.export.FhirExporter
import com.bios.app.export.FhirImportSummary
import com.bios.app.export.FhirImporter
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.biomarkers.FhirImportSummaryDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * Your data & export — the owner's own data, and the ways to take it off the
 * device: open formats (JSON/CSV), a FHIR bundle for clinicians, and a
 * one-page PDF summary. Surfaced as its own route off the Self tab so export
 * is one tap from the landing page instead of buried inside the wearable
 * connection screen — owners kept failing to find it (export discoverability).
 *
 * The export/import controls themselves live here ([YourDataCard] and the
 * buttons it hosts); [DataSourcesScreen] is now scoped to connections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your data & export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Your data is yours. Save a copy to the device in an open format, " +
                    "or share a clinician-ready summary. Save works offline with no " +
                    "other app installed; nothing leaves until you choose where it goes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            YourDataCard(viewModel = viewModel)
        }
    }
}

@Composable
private fun YourDataCard(viewModel: AppViewModel) {
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    var totalReadings by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        totalReadings = viewModel.db.metricReadingDao().countAll()
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your Data", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            SettingsRow("Data Age", "$dataAge days")
            SettingsRow("Total Readings", "$totalReadings")
            SettingsRow(
                "Baseline Status",
                if (dataAge >= BaselineEngine.MINIMUM_DATA_DAYS) "Active"
                else "${BaselineEngine.MINIMUM_DATA_DAYS - dataAge} days remaining"
            )

            Spacer(Modifier.height(8.dp))
            ImportFhirButton(viewModel = viewModel)
            Spacer(Modifier.height(12.dp))
            ExportRow(
                label = "JSON — all data",
                enabled = totalReadings > 0,
                mimeType = "application/json",
                suggestedName = "bios_export.json",
                shareChooserTitle = "Export Bios Data",
            ) { DataExporter(it, viewModel.db).exportToFile() }
            Spacer(Modifier.height(12.dp))
            ExportRow(
                label = "CSV — all data (zip)",
                enabled = totalReadings > 0,
                mimeType = "application/zip",
                suggestedName = "bios_export.zip",
                shareChooserTitle = "Export Bios Data (CSV)",
            ) { DataExporter(it, viewModel.db).exportToCsvZip() }
            Spacer(Modifier.height(12.dp))
            ExportRow(
                label = "FHIR bundle — for doctors",
                enabled = totalReadings > 0,
                mimeType = "application/fhir+json",
                suggestedName = "bios_fhir.json",
                shareChooserTitle = "Share FHIR Bundle with your doctor",
            ) { FhirExporter(it, viewModel.db).exportToFhirBundle() }
            Spacer(Modifier.height(12.dp))
            PdfSummaryButton(viewModel = viewModel, enabled = totalReadings > 0)
        }
    }
}

/**
 * Quick import of lab results from a FHIR R4 JSON file (Bundle or single
 * Observation) into the biomarker store — the read-side mirror of the
 * "Export as FHIR Bundle" button. Reuses [FhirImporter] and the shared
 * [FhirImportSummaryDialog], so accepted/skipped reporting stays identical
 * to the biomarker entry screen's import path. Imports route through
 * [AppViewModel.biomarkerEntryRepo]'s SELF_REPORTED write path, so engines
 * keep ignoring them per docs/SELF_REPORTED_DATA_HOME.md.
 */
@Composable
private fun ImportFhirButton(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<FhirImportSummary?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                try {
                    summary = FhirImporter.importFromUri(
                        context = context,
                        uri = uri,
                        repo = viewModel.biomarkerEntryRepo,
                    )
                    viewModel.refreshRecentBiomarkers()
                } finally {
                    importing = false
                }
            }
        }
    }

    OutlinedButton(
        onClick = {
            launcher.launch(arrayOf("application/json", "application/fhir+json", "*/*"))
        },
        enabled = !importing,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (importing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (importing) "Importing…" else "Import lab results (FHIR)")
    }

    summary?.let { s ->
        FhirImportSummaryDialog(summary = s, onDismiss = { summary = null })
    }
}

/**
 * One export format, offered two ways:
 *
 *  - **Save to device** — the Storage Access Framework ([CreateDocument])
 *    writes the file straight to a location the owner picks. This needs no
 *    other app installed, so it is the reliable path on degoogled builds
 *    (LETHE) where the share sheet can be empty. Listed first for that reason.
 *  - **Share** — the original [ACTION_SEND] chooser, for handing the file to
 *    mail / a clinician app / cloud when one is present.
 *
 * Both run [produceFile] off the main thread; [produceFile] writes a temp
 * copy to cache, which Save streams to the chosen URI and Share exposes via
 * FileProvider. Manages its own in-flight state so each row is independent.
 */
@Composable
private fun ExportRow(
    label: String,
    enabled: Boolean,
    mimeType: String,
    suggestedName: String,
    shareChooserTitle: String,
    produceFile: suspend (android.content.Context) -> File,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    // SAF: the system picker returns the destination URI; we then produce the
    // export and stream its bytes there. A null URI means the owner backed out.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType)
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val file = produceFile(context)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { saveLauncher.launch(suggestedName) },
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save to device")
            }
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            shareFile(context, produceFile(context), mimeType, shareChooserTitle)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = enabled && !busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Share")
            }
        }
    }
}

/** Hand [file] to the system share sheet via FileProvider (read-only grant). */
internal fun shareFile(
    context: android.content.Context,
    file: File,
    mimeType: String,
    chooserTitle: String,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}
