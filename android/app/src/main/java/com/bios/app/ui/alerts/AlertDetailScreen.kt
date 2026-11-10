package com.bios.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.alerts.AlertDetail
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.AlertManager
import com.bios.app.alerts.ConditionPatterns
import com.bios.app.alerts.MeasuredSignal
import com.bios.app.config.RegionConfigProvider
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.components.FeedbackForm
import com.bios.app.ui.components.FeedbackInput
import com.bios.app.ui.components.FeedbackSummary
import com.bios.app.ui.theme.BiosTokens
import java.text.DateFormat
import java.util.Date

/**
 * Where an alert notification tap lands (docs/specs/alert-detail.md): what was
 * measured in real units, which other signals agree, common causes as
 * possibilities, what to watch, when to seek care now, and the journal entry.
 * Pull-side: the owner opened it.
 */
@Composable
fun AlertDetailScreen(
    anomalyId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPattern: (String) -> Unit,
    onOpenTrend: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<AlertDetail?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(anomalyId) {
        detail = viewModel.loadAlertDetail(anomalyId)
        loaded = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // No app bar: the Scaffold already pads the status bar, and the tier
        // meter below is the screen's heading (criterion 7).
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        val d = detail
        when {
            d != null -> AlertDetailBody(
                detail = d,
                onOpenPattern = onOpenPattern,
                onOpenTrend = onOpenTrend,
                onAcknowledge = {
                    viewModel.acknowledgeAlert(anomalyId)
                    detail = d.copy(anomaly = d.anomaly.copy(acknowledged = true))
                },
                onSaveFeedback = { input ->
                    viewModel.saveAlertFeedback(
                        anomalyId = anomalyId,
                        feltSick = input.feltSick,
                        visitedDoctor = input.visitedDoctor,
                        diagnosis = input.diagnosis,
                        symptoms = input.symptoms,
                        notes = input.notes,
                        outcomeAccurate = input.outcomeAccurate,
                    )
                    detail = d.copy(anomaly = d.anomaly.withFeedback(input))
                },
            )
            loaded -> CenteredText("This alert is no longer stored on this device.")
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AlertDetailBody(
    detail: AlertDetail,
    onOpenPattern: (String) -> Unit,
    onOpenTrend: (String) -> Unit,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (FeedbackInput) -> Unit,
) {
    val anomaly = detail.anomaly
    val deviating = detail.signals.filter { it.deviating }
    val others = detail.signals.filterNot { it.deviating }
    val pattern = remember(anomaly.patternId) {
        anomaly.patternId?.let { id -> ConditionPatterns.all.firstOrNull { it.id == id } }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AlertHeader(anomaly)
        MeasuredSection(anomaly, deviating, detail.now)
        pattern?.takeIf { it.risks.isNotBlank() }?.let { p ->
            Section("Why this matters") { Body(p.risks) }
            TextButton(onClick = { onOpenPattern(p.id) }) { Text("Read full context") }
        }
        if (others.isNotEmpty()) OtherSignalsSection(others, detail.now)
        if (detail.signals.isNotEmpty()) {
            Text(
                AlertDetailContent.STRIP_LEGEND,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CausesSection(deviating)
        anomaly.suggestedAction?.let { Section("Suggested next step") { Body(it) } }
        Section("Worth watching") { Bullets(AlertDetailContent.WORTH_WATCHING) }
        SeekCareCard()
        Text(
            AlertDetailContent.ONE_ALERT_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        deviating.firstOrNull()?.let { top ->
            OutlinedButton(onClick = { onOpenTrend(top.metricKey) }, modifier = Modifier.fillMaxWidth()) {
                Text("See ${AlertDetailContent.label(top.metricKey)} trend")
            }
        }
        ActionsSection(anomaly, onAcknowledge, onSaveFeedback)
        Text(
            AlertManager.resolveDisclaimer(LocalContext.current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlertHeader(anomaly: Anomaly) {
    val tier = AlertTier.fromLevel(anomaly.severity)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TierMeter(tier)
        Spacer(Modifier.height(4.dp))
        Text(anomaly.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(anomaly.detectedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeasuredSection(anomaly: Anomaly, deviating: List<MeasuredSignal>, now: Map<String, MeasuredSignal>) {
    Section("What Bios measured, last 24 hours") {
        if (deviating.isEmpty()) {
            Body(anomaly.explanation)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                deviating.forEach { s ->
                    val status = if (s.above) "above your usual range" else "below your usual range"
                    SignalStripRow(s, status, AlertDetailContent.nowLine(s, now[s.metricKey]))
                }
            }
        }
    }
}

@Composable
private fun OtherSignalsSection(others: List<MeasuredSignal>, now: Map<String, MeasuredSignal>) {
    Section("Other signals, same 24 hours") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            others.forEach {
                SignalStripRow(it, "within your usual range", AlertDetailContent.nowLine(it, now[it.metricKey]))
            }
        }
    }
}

@Composable
private fun CausesSection(deviating: List<MeasuredSignal>) {
    deviating.take(2).forEach { s ->
        val dir = if (s.above) "raise" else "lower"
        Section("Things that commonly $dir ${AlertDetailContent.label(s.metricKey)}") {
            Bullets(AlertDetailContent.commonCauses(s.metricKey, s.above))
        }
    }
}

@Composable
private fun SeekCareCard() {
    val context = LocalContext.current
    val region = remember { RegionConfigProvider.forDevice(context) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                AlertDetailContent.SEEK_CARE_HEADING,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Bullets(AlertDetailContent.SEEK_CARE_NOW, MaterialTheme.colorScheme.onErrorContainer)
            Text(
                AlertDetailContent.emergencyLine(region.emergencyNumber, region.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ActionsSection(
    anomaly: Anomaly,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (FeedbackInput) -> Unit,
) {
    if (!anomaly.acknowledged) {
        Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) { Text("Acknowledge") }
    }
    Section("What happened? (your journal)") {
        if (anomaly.feedbackAt != null) FeedbackSummary(anomaly) else FeedbackForm(onSubmit = onSaveFeedback)
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullets(items: List<String>, color: Color = Color.Unspecified) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = color) }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Anomaly.withFeedback(input: FeedbackInput) = copy(
    feedbackAt = System.currentTimeMillis(),
    feltSick = input.feltSick,
    visitedDoctor = input.visitedDoctor,
    diagnosis = input.diagnosis,
    symptoms = input.symptoms,
    notes = input.notes,
    outcomeAccurate = input.outcomeAccurate,
)
