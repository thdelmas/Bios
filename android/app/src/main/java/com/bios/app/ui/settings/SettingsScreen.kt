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
import com.bios.app.ingest.GarminApiAdapter
import com.bios.app.ingest.PolarApiAdapter
import com.bios.app.ingest.WhoopApiAdapter
import com.bios.app.ingest.WithingsApiAdapter
import com.bios.app.model.CompanionGrant
import com.bios.app.model.PrivacyTier
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.privacy.ContributionWorker
import com.bios.app.push.PushRegistrationManager
import com.bios.app.ui.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToCompanions: () -> Unit = {},
    onNavigateToDataCoverage: () -> Unit = {},
    onNavigateToBlePair: () -> Unit = {},
    onNavigateToMedications: () -> Unit = {},
    onNavigateToImmunisations: () -> Unit = {},
    onNavigateToPreventiveCare: () -> Unit = {},
    onNavigateToRiskProfile: () -> Unit = {},
    onNavigateToPhysiologyState: () -> Unit = {},
    onNavigateToFrailAssessment: () -> Unit = {},
    onNavigateToGoalsOfCare: () -> Unit = {},
    onNavigateToHeadacheDiary: () -> Unit = {},
    onNavigateToFastStroke: () -> Unit = {},
    onNavigateToEsasCapture: () -> Unit = {},
    onNavigateToTraditionalMedicine: () -> Unit = {},
    onNavigateToEnvironmentalContext: () -> Unit = {},
    onNavigateToEmergencyContacts: () -> Unit = {},
    onNavigateToEcgStrips: () -> Unit = {},
    onNavigateToSurgicalRecovery: () -> Unit = {},
    onNavigateToInterventionEvents: () -> Unit = {},
    onNavigateToTreatmentCourses: () -> Unit = {},
    onNavigateToAnthropometry: () -> Unit = {},
    onNavigateToMetricReadingsDebug: () -> Unit = {},
    onNavigateToSeizureTimeline: () -> Unit = {},
    onNavigateToMetricSources: () -> Unit = {},
) {
    val context = LocalContext.current
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    val scope = rememberCoroutineScope()
    var totalReadings by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var showOuraDialog by remember { mutableStateOf(false) }
    var isOuraConnected by remember { mutableStateOf(viewModel.ouraTokenStore.hasToken()) }
    var showWithingsDialog by remember { mutableStateOf(false) }
    var isWithingsConnected by remember { mutableStateOf(viewModel.apiTokenStore.hasToken(WithingsApiAdapter.PROVIDER_KEY)) }
    var showWhoopDialog by remember { mutableStateOf(false) }
    var isWhoopConnected by remember { mutableStateOf(viewModel.apiTokenStore.hasToken(WhoopApiAdapter.PROVIDER_KEY)) }
    var showGarminDialog by remember { mutableStateOf(false) }
    var isGarminConnected by remember { mutableStateOf(viewModel.apiTokenStore.hasToken(GarminApiAdapter.PROVIDER_KEY)) }
    var showPolarDialog by remember { mutableStateOf(false) }
    var isPolarConnected by remember { mutableStateOf(viewModel.apiTokenStore.hasToken(PolarApiAdapter.PROVIDER_KEY)) }
    val companionGrants by viewModel.db.companionGrantDao()
        .observeAll()
        .collectAsState(initial = emptyList())
    val pendingCompanionCount = companionGrants.count { it.state == CompanionGrant.STATE_PENDING }
    val approvedCompanionCount = companionGrants.count { it.state == CompanionGrant.STATE_GRANTED }
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
        Text("Self", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        IdentityCard(
            onNavigateToMedications = onNavigateToMedications,
            onNavigateToImmunisations = onNavigateToImmunisations,
            onNavigateToPreventiveCare = onNavigateToPreventiveCare,
            onNavigateToRiskProfile = onNavigateToRiskProfile,
            onNavigateToPhysiologyState = onNavigateToPhysiologyState,
            onNavigateToFrailAssessment = onNavigateToFrailAssessment,
            onNavigateToGoalsOfCare = onNavigateToGoalsOfCare,
            onNavigateToHeadacheDiary = onNavigateToHeadacheDiary,
            onNavigateToFastStroke = onNavigateToFastStroke,
            onNavigateToEsasCapture = onNavigateToEsasCapture,
            onNavigateToTraditionalMedicine = onNavigateToTraditionalMedicine,
            onNavigateToEnvironmentalContext = onNavigateToEnvironmentalContext,
            onNavigateToEmergencyContacts = onNavigateToEmergencyContacts,
            onNavigateToEcgStrips = onNavigateToEcgStrips,
            onNavigateToSurgicalRecovery = onNavigateToSurgicalRecovery,
            onNavigateToInterventionEvents = onNavigateToInterventionEvents,
            onNavigateToTreatmentCourses = onNavigateToTreatmentCourses,
            onNavigateToAnthropometry = onNavigateToAnthropometry,
        )

        // Privacy — what can leave, and who can access. Highest-stakes surface.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Privacy", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))

                CompanionAppsRow(
                    pendingCount = pendingCompanionCount,
                    approvedCount = approvedCompanionCount,
                    onClick = onNavigateToCompanions
                )

                Spacer(Modifier.height(12.dp))
                Text("Tier", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
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
                        PrivacyTier.COMMUNITY -> "Send anonymous aggregates. Off by default. Aggregation happens on-device before transmission — raw data never leaves."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))
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
                ConnectableSourceRow(
                    name = "Oura Ring",
                    isConnected = isOuraConnected,
                    onConnect = { showOuraDialog = true },
                    onDisconnect = {
                        viewModel.ouraTokenStore.clearToken()
                        isOuraConnected = false
                    },
                )

                Spacer(Modifier.height(4.dp))
                ConnectableSourceRow(
                    name = "Withings",
                    isConnected = isWithingsConnected,
                    onConnect = { showWithingsDialog = true },
                    onDisconnect = {
                        viewModel.apiTokenStore.clearToken(
                            WithingsApiAdapter.PROVIDER_KEY
                        )
                        isWithingsConnected = false
                    },
                )

                Spacer(Modifier.height(4.dp))
                ConnectableSourceRow(
                    name = "WHOOP",
                    isConnected = isWhoopConnected,
                    onConnect = { showWhoopDialog = true },
                    onDisconnect = {
                        viewModel.apiTokenStore.clearToken(WhoopApiAdapter.PROVIDER_KEY)
                        isWhoopConnected = false
                    },
                )

                Spacer(Modifier.height(4.dp))
                ConnectableSourceRow(
                    name = "Garmin",
                    isConnected = isGarminConnected,
                    onConnect = { showGarminDialog = true },
                    onDisconnect = {
                        viewModel.apiTokenStore.clearToken(GarminApiAdapter.PROVIDER_KEY)
                        isGarminConnected = false
                    },
                )

                Spacer(Modifier.height(4.dp))
                ConnectableSourceRow(
                    name = "Polar",
                    isConnected = isPolarConnected,
                    onConnect = { showPolarDialog = true },
                    onDisconnect = {
                        viewModel.apiTokenStore.clearToken(PolarApiAdapter.PROVIDER_KEY)
                        isPolarConnected = false
                    },
                )

                Spacer(Modifier.height(4.dp))
                ConnectableSourceRow(
                    name = "Air-quality sensor (BLE)",
                    isConnected = viewModel.bleAirQualityAdapter.isPaired,
                    // Both routes go to the pair screen — unpair lives there.
                    onConnect = onNavigateToBlePair,
                    onDisconnect = onNavigateToBlePair,
                )

                Spacer(Modifier.height(8.dp))
                SettingsActionButton("Data coverage", onNavigateToDataCoverage)
                Spacer(Modifier.height(4.dp))
                SettingsActionButton("Per-metric sources", onNavigateToMetricSources)
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
                Spacer(Modifier.height(4.dp))
                PdfSummaryButton(viewModel = viewModel, enabled = totalReadings > 0)
            }
        }

        SettingsNotificationsCard()

        SettingsFeedbackCard()

        SettingsPhoneSleepCard()

        SettingsSeizureDetectionCard(onViewDetections = onNavigateToSeizureTimeline)

        SettingsDiagnosticsCard()

        // About — long-press Version row opens the metric_readings debug screen (#253).
        SettingsAboutCard(onLongPressVersion = onNavigateToMetricReadingsDebug)
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
        PasteTokenDialog(
            providerName = "Oura",
            helperText = SettingsHelperText.OURA,
            onConnect = { token ->
                viewModel.ouraTokenStore.saveToken(token); isOuraConnected = true; showOuraDialog = false
            },
            onDismiss = { showOuraDialog = false }
        )
    }
    if (showWithingsDialog) {
        PasteTokenDialog(
            providerName = "Withings",
            helperText = SettingsHelperText.WITHINGS,
            onConnect = { token ->
                viewModel.apiTokenStore.saveToken(WithingsApiAdapter.PROVIDER_KEY, token); isWithingsConnected = true; showWithingsDialog = false
            },
            onDismiss = { showWithingsDialog = false }
        )
    }
    if (showWhoopDialog) {
        PasteTokenDialog(
            providerName = "WHOOP",
            helperText = SettingsHelperText.WHOOP,
            onConnect = { token ->
                viewModel.apiTokenStore.saveToken(WhoopApiAdapter.PROVIDER_KEY, token); isWhoopConnected = true; showWhoopDialog = false
            },
            onDismiss = { showWhoopDialog = false }
        )
    }
    if (showGarminDialog) {
        PasteTokenDialog(
            providerName = "Garmin",
            helperText = SettingsHelperText.GARMIN,
            onConnect = { token ->
                viewModel.apiTokenStore.saveToken(GarminApiAdapter.PROVIDER_KEY, token); isGarminConnected = true; showGarminDialog = false
            },
            onDismiss = { showGarminDialog = false }
        )
    }
    if (showPolarDialog) {
        PasteTokenDialog(
            providerName = "Polar",
            helperText = SettingsHelperText.POLAR,
            onConnect = { token ->
                viewModel.apiTokenStore.saveToken(PolarApiAdapter.PROVIDER_KEY, token); isPolarConnected = true; showPolarDialog = false
            },
            onDismiss = { showPolarDialog = false }
        )
    }
}

@Composable
private fun SettingsActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private fun saveTier(context: Context, tier: PrivacyTier) {
    context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE).edit().putString("privacy_tier", tier.name).apply()
}
