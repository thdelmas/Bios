package com.bios.app.ui.notice

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.BaselineEngine
import com.bios.app.model.CompanionGrant
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.AlertCard
import com.bios.app.ui.diagnostics.DiagnosticCard
import androidx.compose.runtime.LaunchedEffect

/**
 * What Bios noticed. Merges the previous Alerts and Diagnostics tabs and
 * surfaces pending companion-grant approvals — the three things the
 * owner needs to *hear* from the instrument. Empty by design when there's
 * nothing to say.
 */
@Composable
fun NoticeScreen(
    viewModel: AppViewModel,
    onNavigateToCondition: (String) -> Unit,
    onNavigateToCompanions: () -> Unit,
    onNavigateToReference: () -> Unit,
) {
    val unacknowledged by viewModel.unacknowledgedAlerts.collectAsState()
    val recent by viewModel.recentAlerts.collectAsState()
    val results by viewModel.diagnosticResults.collectAsState()
    val coverage by viewModel.metricCoverage.collectAsState()
    val dataAge by viewModel.ingestManager.dataAgeDays.collectAsState()
    val companionGrants by viewModel.db.companionGrantDao()
        .observeAll()
        .collectAsState(initial = emptyList())
    val pendingCompanions = companionGrants.count { it.state == CompanionGrant.STATE_PENDING }
    val acknowledged = recent.filter { it.acknowledged }

    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }

    val baselineReady = dataAge >= BaselineEngine.MINIMUM_DATA_DAYS
    val anyDataReceived = coverage.values.any { it.hasAnyData }
    val nothingToSay = unacknowledged.isEmpty() &&
        acknowledged.isEmpty() &&
        results.isEmpty() &&
        pendingCompanions == 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Notice",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNavigateToReference) {
                Icon(
                    Icons.AutoMirrored.Filled.LibraryBooks,
                    contentDescription = "Longevity reference",
                )
            }
        }

        if (pendingCompanions > 0) {
            PendingCompanionCard(
                count = pendingCompanions,
                onReview = onNavigateToCompanions,
            )
        }

        if (unacknowledged.isNotEmpty()) {
            SectionHeader("Needs attention", badge = unacknowledged.size)
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
                            outcomeAccurate = input.outcomeAccurate,
                        )
                    },
                    onOpenPattern = onNavigateToCondition,
                )
            }
        }

        if (baselineReady && anyDataReceived && results.isNotEmpty()) {
            SectionHeader("Patterns Bios is watching")
            results.forEach { result ->
                DiagnosticCard(result, onNavigateToDetail = onNavigateToCondition)
            }
        } else if (!baselineReady || !anyDataReceived) {
            BaselineBuildingCard(dataAge = dataAge, anyDataReceived = anyDataReceived)
        }

        if (acknowledged.isNotEmpty()) {
            SectionHeader("Recent")
            acknowledged.forEach { anomaly ->
                AlertCard(
                    anomaly = anomaly,
                    onAcknowledge = {},
                    onSaveFeedback = { input ->
                        viewModel.saveAlertFeedback(
                            anomalyId = anomaly.id,
                            feltSick = input.feltSick,
                            visitedDoctor = input.visitedDoctor,
                            diagnosis = input.diagnosis,
                            symptoms = input.symptoms,
                            notes = input.notes,
                            outcomeAccurate = input.outcomeAccurate,
                        )
                    },
                    onOpenPattern = onNavigateToCondition,
                )
            }
        }

        if (nothingToSay && baselineReady && anyDataReceived) {
            QuietState()
        }
    }
}

@Composable
private fun SectionHeader(label: String, badge: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Badge { Text("$badge") }
        }
    }
}

@Composable
private fun PendingCompanionCard(count: Int, onReview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onReview,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "1 app waiting for approval"
                    else "$count apps waiting for approval",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Bios is blocking their access until you decide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun BaselineBuildingCard(dataAge: Int, anyDataReceived: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (!anyDataReceived) "No data received yet" else "Building your baseline",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (!anyDataReceived) {
                    "Bios hasn't received any sensor readings yet. " +
                        "Bios reads from Health Connect — your watch's companion app must write there first."
                } else {
                    val remaining = BaselineEngine.MINIMUM_DATA_DAYS - dataAge
                    "Bios needs $remaining more day${if (remaining == 1) "" else "s"} of data " +
                        "to establish your personal baseline and start surfacing patterns."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (anyDataReceived) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { dataAge.toFloat() / BaselineEngine.MINIMUM_DATA_DAYS.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuietState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
            Text("Quiet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Bios will speak when something matters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
