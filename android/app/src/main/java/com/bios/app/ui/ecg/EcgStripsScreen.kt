package com.bios.app.ui.ecg

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.EcgStripRepo
import com.bios.app.ingest.AppleHealthEcgImporter
import com.bios.app.model.EcgClassification
import com.bios.app.model.EcgStrip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Identity → ECG strips. Closes audit gap §2.8.
 *
 * Lists every owner-imported single-lead ECG strip by date with the
 * vendor classification, and renders the waveform as a Compose
 * Canvas plot when the owner taps a row.
 *
 * Import surface: SAF picker → Apple Health export XML parser
 * ([AppleHealthEcgImporter]). PDF is intentionally out-of-scope —
 * see the importer's class comment.
 *
 * Pull-side surface — no notifications, no scoring, no nudges. The
 * AlertContentPolicy ban list doesn't constrain this screen (it
 * targets push) but the framing is intentionally neutral anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcgStripsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { EcgStripRepo(BiosDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()

    var strips by remember { mutableStateOf<List<EcgStrip>>(emptyList()) }
    var selectedStrip by remember { mutableStateOf<EcgStrip?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        strips = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    val xmlPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val cr = context.contentResolver
                        cr.openInputStream(uri)?.use { input ->
                            AppleHealthEcgImporter.parse(input)
                        } ?: emptyList()
                    }
                }
                val parsed = result.getOrNull() ?: emptyList()
                for (strip in parsed) repo.insert(strip)
                importMessage = when {
                    result.isFailure -> "Could not read file."
                    parsed.isEmpty() -> "No ECG records found in file."
                    else -> "Imported ${parsed.size} ECG strip(s)."
                }
                refresh()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ECG strips") },
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
                    Text("Imported ECG strips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Single-lead ECG strips from Apple Watch, KardiaMobile, Withings " +
                            "ScanWatch, Samsung Galaxy Watch and others. Bios stores the " +
                            "waveform and the vendor's rhythm classification — it does not " +
                            "interpret the trace. The artefact travels with the FHIR export.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            // Apple Health exports the strip as XML inside a zip
                            // archive. We accept both the raw XML and the broader
                            // mime catch-all in case the file picker can't infer it.
                            xmlPicker.launch(arrayOf("text/xml", "application/xml", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Import from file (Apple Health XML)")
                    }
                    importMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (strips.isEmpty()) {
                Text(
                    "No strips on file yet. Open the Health app on the source device " +
                        "and export your data, then pick the export.xml here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for (strip in strips) {
                    EcgStripRow(strip = strip, onTap = { selectedStrip = strip })
                }
            }

            selectedStrip?.let { strip ->
                Spacer(Modifier.height(8.dp))
                EcgStripDetail(
                    strip = strip,
                    onDelete = {
                        scope.launch {
                            repo.remove(strip.id)
                            selectedStrip = null
                            refresh()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EcgStripRow(strip: EcgStrip, onTap: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                dateFmt.format(Date(strip.timestamp)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${strip.sourceVendor} • ${strip.durationSeconds}s @ ${strip.samplingRateHz}Hz",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Classification: ${classificationLabel(strip.classification)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onTap) { Text("View waveform") }
        }
    }
}

@Composable
private fun EcgStripDetail(strip: EcgStrip, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                dateFmt.format(Date(strip.timestamp)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${strip.sourceVendor} • ${strip.durationSeconds}s @ ${strip.samplingRateHz}Hz • ${strip.leadPlacement.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Vendor classification: ${classificationLabel(strip.classification)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EcgWaveformCanvas(strip = strip)
            OutlinedButton(onClick = onDelete) { Text("Delete strip") }
        }
    }
}

private fun classificationLabel(c: EcgClassification?): String = when (c) {
    EcgClassification.SINUS_RHYTHM -> "Sinus rhythm"
    EcgClassification.ATRIAL_FIBRILLATION -> "Atrial fibrillation"
    EcgClassification.INCONCLUSIVE -> "Inconclusive"
    EcgClassification.OTHER -> "Other"
    null -> "Unspecified"
}
