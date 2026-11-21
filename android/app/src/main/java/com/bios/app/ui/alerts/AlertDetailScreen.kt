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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.alerts.AlertDetail
import com.bios.app.alerts.AlertDetailContent
import com.bios.app.alerts.AlertManager
import com.bios.app.alerts.MeasuredSignal
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
 * Where an alert notification tap lands: the tier as a size of deviation,
 * what was measured in real units against the owner's usual range, the other
 * watched metrics over the same 24 hours, acknowledge and the owner's journal.
 */
@Composable
fun AlertDetailScreen(
    anomalyId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
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
                onOpenTrend = onOpenTrend,
                onAcknowledge = {
                    viewModel.acknowledgeAlert(anomalyId)
                    detail = d.copy(anomaly = d.anomaly.copy(acknowledged = true))
                },
                onSaveFeedback = { input ->
                    viewModel.saveAlertFeedback(anomalyId, input)
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
    onOpenTrend: (String) -> Unit,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (FeedbackInput) -> Unit,
) {
    val anomaly = detail.anomaly
    val deviating = detail.signals.filter { it.deviating }
    val others = detail.signals.filterNot { it.deviating }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AlertHeader(anomaly)
        MeasuredSection(anomaly, deviating, detail.now)
        if (others.isNotEmpty()) OtherSignalsSection(others, detail.now)
        if (detail.signals.isNotEmpty()) {
            Text(
                AlertDetailContent.STRIP_LEGEND,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            AlertDetailContent.HOW_COMPARED,
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
            AlertManager.DISCLAIMER,
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
    Section("Other metrics, same 24 hours") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            others.forEach {
                SignalStripRow(it, "within your usual range", AlertDetailContent.nowLine(it, now[it.metricKey]))
            }
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
    Section("Your journal") {
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
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Anomaly.withFeedback(input: FeedbackInput) = copy(
    feedbackAt = System.currentTimeMillis(),
    feltSick = input.feltSick,
    symptoms = input.symptoms,
    notes = input.notes,
    outcomeAccurate = input.outcomeAccurate,
)
