package com.bios.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bios.app.engine.BaselineEngine
import com.bios.app.export.DataExporter
import com.bios.app.export.FhirExporter
import com.bios.app.model.PrivacyTier
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.privacy.ContributionWorker
import com.bios.app.push.PushRegistrationManager
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: AppViewModel, onNavigateToPrivacy: () -> Unit = {}) {
    val context = LocalContext.current
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val scope = rememberCoroutineScope()

    var totalReadings by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showOuraDialog by remember { mutableStateOf(false) }
    var isOuraConnected by remember { mutableStateOf(viewModel.ouraTokenStore.hasToken()) }
    var privacyTier by remember {
        val prefs = context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        val tier = prefs.getString("privacy_tier", PrivacyTier.PRIVATE.name)
        mutableStateOf(PrivacyTier.valueOf(tier ?: PrivacyTier.PRIVATE.name))
    }

    LaunchedEffect(Unit) {
        totalReadings = viewModel.db.metricReadingDao().countAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        // Data Sources
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
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Oura Ring")
                    if (isOuraConnected) {
                        Text("Connected", color = MaterialTheme.colorScheme.primary)
                    } else {
                        TextButton(onClick = { showOuraDialog = true }) {
                            Text("Connect")
                        }
                    }
                }
                if (isOuraConnected) {
                    TextButton(
                        onClick = {
                            viewModel.ouraTokenStore.clearToken()
                            isOuraConnected = false
                        }
                    ) {
                        Text("Disconnect Oura", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Your Data
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Your Data", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                SettingsRow("Data Age", "$dataAge days")
                SettingsRow("Total Readings", "$totalReadings")
                SettingsRow("Baseline Status",
                    if (dataAge >= BaselineEngine.MINIMUM_DATA_DAYS) "Active"
                    else "${BaselineEngine.MINIMUM_DATA_DAYS - dataAge} days remaining"
                )

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            try {
                                val exporter = DataExporter(context, viewModel.db)
                                val file = exporter.exportToFile()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Export Bios Data")
                                )
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && totalReadings > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isExporting) "Exporting..." else "Export as JSON")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            try {
                                val exporter = DataExporter(context, viewModel.db)
                                val file = exporter.exportToCsvZip()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Export Bios Data (CSV)")
                                )
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && totalReadings > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export as CSV")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        scope.launch {
                            try {
                                val exporter = FhirExporter(context, viewModel.db)
                                val file = exporter.exportToFhirBundle()
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/fhir+json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share FHIR Bundle with your doctor")
                                )
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting && totalReadings > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export as FHIR Bundle (for doctors)")
                }
            }
        }

        // Notifications
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notifications", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                var digestEnabled by remember {
                    mutableStateOf(DailyDigestWorker.isEnabled(context))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Digest", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Morning summary of your vitals at 8 AM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = digestEnabled,
                        onCheckedChange = {
                            digestEnabled = it
                            DailyDigestWorker.setEnabled(context, it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var pushEnabled by remember {
                    mutableStateOf(PushRegistrationManager.isEnabled(context))
                }
                var pushDistributor by remember {
                    mutableStateOf(PushRegistrationManager.getDistributorName(context))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Push Notifications", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (pushEnabled && pushDistributor != null)
                                "Via $pushDistributor — no Google required"
                            else
                                "Receive population health signals without polling. Requires a UnifiedPush distributor (e.g. ntfy).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { enabled ->
                            pushEnabled = enabled
                            if (enabled) {
                                PushRegistrationManager.setEnabled(context, true)
                                PushRegistrationManager.register(context)
                            } else {
                                PushRegistrationManager.unregister(context)
                            }
                            pushDistributor = PushRegistrationManager.getDistributorName(context)
                        }
                    )
                }
            }
        }

        // Privacy Tier
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy Tier", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = privacyTier == PrivacyTier.PRIVATE,
                        onClick = {
                            privacyTier = PrivacyTier.PRIVATE
                            saveTier(context, PrivacyTier.PRIVATE)
                            ContributionWorker.cancel(context)
                        },
                        label = { Text("Private") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = privacyTier == PrivacyTier.COMMUNITY,
                        onClick = {
                            privacyTier = PrivacyTier.COMMUNITY
                            saveTier(context, PrivacyTier.COMMUNITY)
                            ContributionWorker.enqueueNextContribution(context)
                        },
                        label = { Text("Community") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (privacyTier) {
                        PrivacyTier.PRIVATE -> "Your data never leaves this device. Zero-knowledge architecture."
                        PrivacyTier.COMMUNITY -> "Anonymous statistical patterns (never raw data) help improve detection for all users. Aggregation happens on-device before transmission."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Privacy
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                SettingsRow("Data Location", "On-device only")
                SettingsRow("Encryption", "AES-256 (SQLCipher)")

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNavigateToPrivacy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy Dashboard")
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete All Data")
                }
            }
        }

        // Feedback & Community
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Feedback", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Help improve Bios by reporting detection accuracy, requesting features, or flagging issues.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/thdelmas/Bios/discussions"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open GitHub Discussions")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/thdelmas/Bios/issues/new"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Report an Issue")
                }
            }
        }

        // About
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SettingsRow("Version", "0.2.0")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data?") },
            text = { Text("This will permanently delete all your health data, baselines, and alerts from Bios. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.bios.app.platform.DataDestroyer.destroyAll(context)
                        totalReadings = 0
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showOuraDialog) {
        var tokenInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOuraDialog = false },
            title = { Text("Connect Oura Ring") },
            text = {
                Column {
                    Text(
                        "Enter your Oura personal access token. You can create one at cloud.ouraring.com/personal-access-tokens.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            viewModel.ouraTokenStore.saveToken(tokenInput.trim())
                            isOuraConnected = true
                            showOuraDialog = false
                        }
                    }
                ) { Text("Connect") }
            },
            dismissButton = {
                TextButton(onClick = { showOuraDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun saveTier(context: Context, tier: PrivacyTier) {
    context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("privacy_tier", tier.name)
        .apply()
}
