package com.bios.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.ingest.SyncWorker
import com.bios.app.model.AlertTier
import com.bios.app.model.HealthEventType
import com.bios.app.model.MetricType
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.AlertCard
import com.bios.app.ui.components.MetricCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val unacknowledged by viewModel.unacknowledgedAlerts.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val lastSync by viewModel.ingestManager.lastSyncTime.collectAsState()
    val isSyncing by viewModel.ingestManager.isSyncing.collectAsState()

    PullToRefreshBox(
        isRefreshing = isSyncing,
        onRefresh = { viewModel.refresh() }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "Bios",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            lastSync?.let { ts ->
                val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                Text(
                    "Synced ${timeFormat.format(Date(ts))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Stale data warning
        val staleThresholdMillis = SyncWorker.STALE_THRESHOLD_HOURS * 3600 * 1000L
        val isStale = lastSync != null &&
            (System.currentTimeMillis() - lastSync!!) > staleThresholdMillis
        if (isStale) {
            StaleDataBanner()
        }

        // Status card
        StatusCard(
            alertCount = unacknowledged.size,
            hasAdvisory = unacknowledged.any { AlertTier.fromLevel(it.severity) >= AlertTier.ADVISORY },
            lastSync = lastSync,
            isSyncing = isSyncing
        )

        // Quick symptom log
        QuickSymptomCard(onLogSymptom = { title ->
            viewModel.createHealthEvent(
                type = HealthEventType.SYMPTOM,
                title = title,
                description = null
            )
        })

        // Active alerts
        if (unacknowledged.isNotEmpty()) {
            Text("Active Alerts", style = MaterialTheme.typography.titleMedium)
            unacknowledged.forEach { anomaly ->
                AlertCard(
                    anomaly = anomaly,
                    onAcknowledge = { viewModel.acknowledgeAlert(anomaly.id) },
                    onSaveFeedback = { input ->
                        viewModel.saveAlertFeedback(
                            anomalyId = anomaly.id,
                            feltSick = input.feltSick,
                            visitedDoctor = input.visitedDoctor,
                            diagnosis = input.diagnosis,
                            symptoms = input.symptoms,
                            notes = input.notes,
                            outcomeAccurate = input.outcomeAccurate
                        )
                    }
                )
            }
        }

        // Today's vitals
        Text("Today's Vitals", style = MaterialTheme.typography.titleMedium)

        val metrics = listOf(
            Triple(MetricType.HEART_RATE, "Heart Rate", Icons.Default.Favorite),
            Triple(MetricType.HEART_RATE_VARIABILITY, "HRV", Icons.Default.ShowChart),
            Triple(MetricType.BLOOD_OXYGEN, "SpO2", Icons.Default.Air),
            Triple(MetricType.RESPIRATORY_RATE, "Resp. Rate", Icons.Default.Air),
            Triple(MetricType.STEPS, "Steps", Icons.Default.DirectionsWalk),
            Triple(MetricType.SKIN_TEMPERATURE_DEVIATION, "Skin Temp", Icons.Default.Thermostat),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(380.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(metrics) { (metricType, label, icon) ->
                MetricCard(
                    metricType = metricType,
                    label = label,
                    icon = icon,
                    viewModel = viewModel
                )
            }
        }

        // Baseline countdown
        if (dataAge < BaselineEngine.MINIMUM_DATA_DAYS) {
            BaselineCountdown(
                currentDays = dataAge,
                requiredDays = BaselineEngine.MINIMUM_DATA_DAYS
            )
        }
    }
    } // PullToRefreshBox
}

@Composable
fun StatusCard(
    alertCount: Int,
    hasAdvisory: Boolean,
    lastSync: Long?,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Today's Status",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        isSyncing -> "Syncing..."
                        alertCount == 0 -> "All vitals within normal range"
                        else -> "$alertCount alert${if (alertCount == 1) "" else "s"} need your attention"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (alertCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = when {
                    hasAdvisory -> Icons.Default.Warning
                    alertCount > 0 -> Icons.Default.Info
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = when {
                    hasAdvisory -> Color(0xFFFF9800)
                    alertCount > 0 -> Color(0xFFFFC107)
                    else -> Color(0xFF4CAF50)
                },
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun BaselineCountdown(currentDays: Int, requiredDays: Int) {
    val remaining = requiredDays - currentDays

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Building Your Baseline",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios needs $remaining more day${if (remaining == 1) "" else "s"} of data to establish your personal baseline and start detecting patterns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { currentDays.toFloat() / requiredDays.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun QuickSymptomCard(onLogSymptom: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "How are you feeling?",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            if (submitted) {
                Text(
                    "Logged! Check your Journal for details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("e.g., metallic taste, headache...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onLogSymptom(text.trim())
                                text = ""
                                submitted = true
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Log symptom",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaleDataBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Health data may be outdated. Open Health Connect to check your wearable connection.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D4C00)
            )
        }
    }
}
