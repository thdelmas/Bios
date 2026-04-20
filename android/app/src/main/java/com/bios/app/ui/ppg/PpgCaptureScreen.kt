package com.bios.app.ui.ppg

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bios.app.engine.RejectionReason
import com.bios.app.ingest.CaptureResult
import com.bios.app.model.MetricType
import kotlinx.coroutines.delay

/**
 * User-initiated fingertip-PPG capture screen.
 *
 * Flow: check/request CAMERA permission → show instructions → user taps
 * "Start" → 60-second capture with countdown → result card (HR + HRV +
 * SQI, or rejection reason with retry).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PpgCaptureScreen(
    onBack: () -> Unit,
    viewModel: PpgCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HRV Snapshot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                !hasCameraPermission -> PermissionPrompt(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
                uiState is PpgUiState.Idle -> IdleView(
                    onStart = { viewModel.startCapture(lifecycleOwner) }
                )
                uiState is PpgUiState.Capturing -> CapturingView(
                    totalSec = (uiState as PpgUiState.Capturing).totalSec
                )
                uiState is PpgUiState.Complete -> ResultView(
                    result = (uiState as PpgUiState.Complete).result,
                    onRetry = { viewModel.reset() },
                    onDone = onBack
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.FlashOn,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Camera access required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Bios uses your rear camera and flash to measure heart-rate variability from your fingertip. Frames never leave the device — only the extracted HR and HRV numbers are kept.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRequest) { Text("Grant camera access") }
    }
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "One-minute HRV snapshot",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InstructionRow("1.", "Sit still — rest your hand on a surface.")
                InstructionRow("2.", "Place your fingertip fully over the rear camera and flash.")
                InstructionRow("3.", "Keep light pressure — enough to cover the lens, not so hard it blocks blood flow.")
                InstructionRow("4.", "Hold for the full 60 seconds. Bios will analyse and show the result.")
            }
        }
        Text(
            "Not for emergencies or diagnosis. Best used as a baseline snapshot between wearable readings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onStart) { Text("Start capture") }
    }
}

@Composable
private fun InstructionRow(marker: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(24.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CapturingView(totalSec: Int) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(totalSec) {
        elapsed = 0
        while (elapsed < totalSec) {
            delay(1000)
            elapsed += 1
        }
    }
    val remaining = (totalSec - elapsed).coerceAtLeast(0)
    val progress = elapsed.toFloat() / totalSec.coerceAtLeast(1)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp
            )
            Text(
                text = "$remaining",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light
            )
        }
        Text("Keep your fingertip still", style = MaterialTheme.typography.titleMedium)
        Text(
            "The flash is on. Don't move your hand until the countdown ends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultView(result: CaptureResult, onRetry: () -> Unit, onDone: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (result.accepted) AcceptedSummary(result) else RejectedSummary(result)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Retry") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun AcceptedSummary(result: CaptureResult) {
    val hr = result.readings.firstOrNull { it.metricType == MetricType.HEART_RATE.key }
    val hrv = result.readings.firstOrNull { it.metricType == MetricType.HEART_RATE_VARIABILITY.key }

    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp)
    )
    Text(
        "Snapshot saved",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        hr?.let { MetricBadge("Heart rate", String.format("%.0f", it.value), "bpm") }
        hrv?.let { MetricBadge("HRV (RMSSD)", String.format("%.0f", it.value), "ms") }
    }

    QualityRow(result.sqiScore, result.peakCount, result.durationSec)
}

@Composable
private fun RejectedSummary(result: CaptureResult) {
    Icon(
        Icons.Default.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(48.dp)
    )
    Text(
        "No reading saved",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
    val reason = result.rejectionReason ?: RejectionReason.IRREGULAR_RHYTHM
    Text(
        reason.userMessage,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (result.peakCount > 0) {
        QualityRow(result.sqiScore, result.peakCount, result.durationSec)
    }
}

@Composable
private fun MetricBadge(label: String, value: String, unit: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun QualityRow(sqi: Int, peaks: Int, durationSec: Double) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        QualityDot("Quality", "$sqi / 100")
        QualityDot("Peaks", "$peaks")
        QualityDot("Duration", "${durationSec.toInt()} s")
    }
}

@Composable
private fun QualityDot(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .height(6.dp)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
