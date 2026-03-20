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
import com.bios.app.model.PrivacyTier
import com.bios.app.privacy.ContributionWorker
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val scope = rememberCoroutineScope()

    var totalReadings by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
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
                    Text(if (isExporting) "Exporting..." else "Export All Data")
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

        // Privacy Info
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                SettingsRow("Data Location", "On-device only")
                SettingsRow("Encryption", "AES-256 (SQLCipher)")

                Spacer(Modifier.height(8.dp))
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

        // About
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SettingsRow("Version", "0.1.0 (Prototype)")
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
                        scope.launch {
                            viewModel.db.metricReadingDao().deleteAll()
                            viewModel.db.personalBaselineDao().deleteAll()
                            viewModel.db.anomalyDao().deleteAll()
                            viewModel.db.computedAggregateDao().deleteAll()
                            totalReadings = 0
                        }
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
