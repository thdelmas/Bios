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
import com.bios.app.ingest.GarminApiAdapter
import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.ingest.WhoopApiAdapter
import com.bios.app.ingest.WithingsApiAdapter
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.biomarkers.FhirImportSummaryDialog
import kotlinx.coroutines.launch
import java.io.File

/**
 * Data & sources — where the owner's data comes from (wearable/API
 * connections, Health Connect) and where it can go (export). Extracted
 * from [SettingsScreen] so the "Self" tab stays a short hub focused on
 * identity and privacy; this screen carries the connection/dialog/export
 * state that made the Self tab a 500-line scroll.
 *
 * [onNavigate] forwards the few sub-routes this screen links to
 * (data_coverage, metric_sources, ble_pair).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit = {},
    onNavigate: (route: String) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & sources") },
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
            DataSourcesCard(viewModel = viewModel, onNavigate = onNavigate)
            YourDataCard(viewModel = viewModel)
        }
    }
}

/**
 * A token-backed wearable/API connection: the connect/disconnect row plus
 * its paste-token dialog. Storage differs per provider (Oura has its own
 * store; the rest share apiTokenStore keyed by PROVIDER_KEY), so the
 * save/clear/initial-state are passed in as plain lambdas.
 */
private class TokenSource(
    val rowName: String,
    val dialogName: String,
    val helperText: String,
    val initialConnected: Boolean,
    val onSave: (String) -> Unit,
    val onClear: () -> Unit,
)

@Composable
private fun DataSourcesCard(
    viewModel: AppViewModel,
    onNavigate: (route: String) -> Unit,
) {
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val tokenSources = listOf(
        TokenSource(
            "Oura Ring", "Oura", SettingsHelperText.OURA,
            viewModel.ouraTokenStore.hasToken(),
            { viewModel.ouraTokenStore.saveToken(it) },
            { viewModel.ouraTokenStore.clearToken() },
        ),
        apiTokenSource(viewModel, "Withings", SettingsHelperText.WITHINGS, WithingsApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "WHOOP", SettingsHelperText.WHOOP, WhoopApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "Garmin", SettingsHelperText.GARMIN, GarminApiAdapter.PROVIDER_KEY),
        apiTokenSource(viewModel, "Polar", SettingsHelperText.POLAR, PolarApiAdapter.PROVIDER_KEY),
    )

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Data Sources", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Health Connect")
                Text(
                    if (hasPermissions) "Connected" else "Not Connected",
                    color = if (hasPermissions) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
            tokenSources.forEach { source ->
                Spacer(Modifier.height(4.dp))
                ApiTokenSourceRow(source)
            }

            Spacer(Modifier.height(4.dp))
            ConnectableSourceRow(
                name = "Air-quality sensor (BLE)",
                isConnected = viewModel.bleAirQualityAdapter.isPaired,
                // Both routes go to the pair screen — unpair lives there.
                onConnect = { onNavigate("ble_pair") },
                onDisconnect = { onNavigate("ble_pair") },
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onNavigate("data_coverage") }, modifier = Modifier.fillMaxWidth()) {
                Text("Data coverage")
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { onNavigate("metric_sources") }, modifier = Modifier.fillMaxWidth()) {
                Text("Per-metric sources")
            }
        }
    }
}

private fun apiTokenSource(
    viewModel: AppViewModel,
    name: String,
    helperText: String,
    providerKey: String,
) = TokenSource(
    rowName = name,
    dialogName = name,
    helperText = helperText,
    initialConnected = viewModel.apiTokenStore.hasToken(providerKey),
    onSave = { viewModel.apiTokenStore.saveToken(providerKey, it) },
    onClear = { viewModel.apiTokenStore.clearToken(providerKey) },
)

@Composable
private fun ApiTokenSourceRow(source: TokenSource) {
    var connected by remember { mutableStateOf(source.initialConnected) }
    var showDialog by remember { mutableStateOf(false) }
    ConnectableSourceRow(
        name = source.rowName,
        isConnected = connected,
        onConnect = { showDialog = true },
        onDisconnect = { source.onClear(); connected = false },
    )
    if (showDialog) {
        PasteTokenDialog(
            providerName = source.dialogName,
            helperText = source.helperText,
            onConnect = { token -> source.onSave(token); connected = true; showDialog = false },
            onDismiss = { showDialog = false },
        )
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
            Spacer(Modifier.height(4.dp))
            ExportButton(
                label = "Export as JSON",
                enabled = totalReadings > 0,
                mimeType = "application/json",
                chooserTitle = "Export Bios Data",
            ) { DataExporter(it, viewModel.db).exportToFile() }
            Spacer(Modifier.height(4.dp))
            ExportButton(
                label = "Export as CSV",
                enabled = totalReadings > 0,
                mimeType = "application/zip",
                chooserTitle = "Export Bios Data (CSV)",
            ) { DataExporter(it, viewModel.db).exportToCsvZip() }
            Spacer(Modifier.height(4.dp))
            ExportButton(
                label = "Export as FHIR Bundle (for doctors)",
                enabled = totalReadings > 0,
                mimeType = "application/fhir+json",
                chooserTitle = "Share FHIR Bundle with your doctor",
            ) { FhirExporter(it, viewModel.db).exportToFhirBundle() }
            Spacer(Modifier.height(4.dp))
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
 * Shared export-and-share button: runs [produceFile] off the main thread,
 * wraps the result in a FileProvider URI, and fires a share chooser.
 * Manages its own in-flight state so each export is independent.
 */
@Composable
private fun ExportButton(
    label: String,
    enabled: Boolean,
    mimeType: String,
    chooserTitle: String,
    produceFile: suspend (android.content.Context) -> File,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            isExporting = true
            scope.launch {
                try {
                    val file = produceFile(context)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
                } finally {
                    isExporting = false
                }
            }
        },
        enabled = enabled && !isExporting,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (isExporting) "Exporting..." else label)
    }
}
