package com.bios.app.ui.biomarkers

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bios.app.ui.AppViewModel
import java.io.File

/**
 * "Scan lab report" entry card — the on-device OCR ingestion surface (Phase
 * 10), split out of [BiomarkerEntryScreen] to keep that file under the
 * 500-line cap. Self-contained: owns its capture state and the pick / camera /
 * permission launchers. When a scan finishes it routes to the review screen
 * via [onOpenScanReview]; nothing is written until the owner confirms there.
 */
@Composable
fun LabScanEntryCard(
    viewModel: AppViewModel,
    onOpenScanReview: () -> Unit,
) {
    val context = LocalContext.current
    val scanning by viewModel.labScan.scanning.collectAsState()
    val pendingScan by viewModel.labScan.pending.collectAsState()
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { picked ->
        if (picked != null) {
            val isPdf = context.contentResolver.getType(picked) == "application/pdf"
            viewModel.labScan.scan(context, picked, isPdf)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) captureUri?.let { viewModel.labScan.scan(context, it, isPdf = false) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchLabCapture(context, cameraLauncher) { captureUri = it }
    }

    // When a scan finishes, hand the owner straight to review-and-confirm.
    // Nothing has been written yet — the review screen is the gate.
    LaunchedEffect(pendingScan) {
        if (pendingScan != null) onOpenScanReview()
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scan lab report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Photograph or pick a PDF/photo of a lab report. It's read on-device — " +
                    "no data leaves your phone — and you confirm every value before it's saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { pickLauncher.launch(LAB_REPORT_MIME_TYPES) },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text("Pick PDF / photo") }
                OutlinedButton(
                    onClick = {
                        if (hasCameraPermission(context)) {
                            launchLabCapture(context, cameraLauncher) { captureUri = it }
                        } else {
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !scanning,
                    modifier = Modifier.weight(1f)
                ) { Text("Take photo") }
            }
            if (scanning) {
                Text(
                    "Reading the report…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Create a fresh app-private capture file under `cacheDir/labocr`, hand its
 * FileProvider URI to [onUri] (so the result callback can find it), and launch
 * the camera. The capture is wiped by `DataDestroyer` along with all health data.
 */
private fun launchLabCapture(
    context: Context,
    launcher: ManagedActivityResultLauncher<Uri, Boolean>,
    onUri: (Uri) -> Unit,
) {
    val dir = File(context.cacheDir, "labocr").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    onUri(uri)
    launcher.launch(uri)
}
