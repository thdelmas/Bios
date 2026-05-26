package com.bios.app.ui.esas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.EsasReportRepo
import com.bios.app.model.EsasReport
import kotlinx.coroutines.launch

/**
 * Settings → Identity → ESAS-r symptom report.
 *
 * Owner-administered nine-item 0-10 numeric-rating-scale capture for
 * symptom burden, per Watanabe 2011 (ESAS-r; J Pain Symptom Manage).
 * Closes audit gap §2.8 (issue #185). Mirrors the shape of
 * [com.bios.app.ui.physiology.FrailAssessmentScreen] — pull-side only,
 * no notification on save, no reminder to record.
 *
 * The capture screen also lists prior reports below the slider stack,
 * each row showing timestamp + a compact per-item readout. Deletion
 * is available per row.
 *
 * Manifesto guards (mirrored from the model header):
 *  - The owner answers; Bios never infers symptom burden from biomarkers.
 *  - No push notification fires on save; no reminder fires to fill in.
 *  - No streaks, no gamification — the list is a list, nothing more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EsasCaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { EsasReportRepo(BiosDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()

    var pain by remember { mutableIntStateOf(0) }
    var tiredness by remember { mutableIntStateOf(0) }
    var drowsiness by remember { mutableIntStateOf(0) }
    var nausea by remember { mutableIntStateOf(0) }
    var appetite by remember { mutableIntStateOf(0) }
    var shortnessOfBreath by remember { mutableIntStateOf(0) }
    var depression by remember { mutableIntStateOf(0) }
    var anxiety by remember { mutableIntStateOf(0) }
    var wellbeing by remember { mutableIntStateOf(0) }
    var otherLabel by remember { mutableStateOf("") }
    var otherScore by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var recorded by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<EsasReport>>(emptyList()) }

    suspend fun refresh() {
        reports = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESAS-r symptom report") },
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
            IntroCard()

            EsasSliderRow(
                title = "Pain",
                detail = "0 = no pain, 10 = worst possible pain.",
                value = pain,
                onChange = { pain = it },
            )
            EsasSliderRow(
                title = "Tiredness",
                detail = "Lack of energy. 0 = no tiredness, 10 = worst.",
                value = tiredness,
                onChange = { tiredness = it },
            )
            EsasSliderRow(
                title = "Drowsiness",
                detail = "Feeling sleepy. 0 = none, 10 = worst.",
                value = drowsiness,
                onChange = { drowsiness = it },
            )
            EsasSliderRow(
                title = "Nausea",
                detail = "0 = none, 10 = worst.",
                value = nausea,
                onChange = { nausea = it },
            )
            EsasSliderRow(
                title = "Lack of appetite",
                detail = "0 = best appetite, 10 = worst lack of appetite.",
                value = appetite,
                onChange = { appetite = it },
            )
            EsasSliderRow(
                title = "Shortness of breath",
                detail = "0 = none, 10 = worst.",
                value = shortnessOfBreath,
                onChange = { shortnessOfBreath = it },
            )
            EsasSliderRow(
                title = "Depression",
                detail = "Feeling sad. 0 = none, 10 = worst.",
                value = depression,
                onChange = { depression = it },
            )
            EsasSliderRow(
                title = "Anxiety",
                detail = "Feeling nervous. 0 = none, 10 = worst.",
                value = anxiety,
                onChange = { anxiety = it },
            )
            EsasSliderRow(
                title = "Wellbeing",
                detail = "Overall feeling. 0 = best wellbeing, 10 = worst.",
                value = wellbeing,
                onChange = { wellbeing = it },
            )

            OtherSymptomCard(
                label = otherLabel,
                score = otherScore,
                onLabelChange = { otherLabel = it },
                onScoreChange = { otherScore = it },
            )

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Note (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Anything you want to remember about this report") },
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repo.add(
                            painScore = pain,
                            tirednessScore = tiredness,
                            drowsinessScore = drowsiness,
                            nauseaScore = nausea,
                            appetiteScore = appetite,
                            shortnessOfBreathScore = shortnessOfBreath,
                            depressionScore = depression,
                            anxietyScore = anxiety,
                            wellbeingScore = wellbeing,
                            otherSymptomLabel = otherLabel,
                            otherSymptomScore = if (otherLabel.isNotBlank()) otherScore else null,
                            note = note,
                        )
                        recorded = true
                        refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save report")
            }

            if (recorded) {
                Text(
                    "Report saved on-device. It will only leave Bios if you export it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Recent reports", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (reports.isEmpty()) {
                Text(
                    "No reports recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                reports.take(20).forEach { report ->
                    EsasReportRow(
                        report = report,
                        onDelete = {
                            scope.launch {
                                repo.remove(report.id)
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Why this matters",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "The Edmonton Symptom Assessment System — revised (ESAS-r) is the " +
                    "global standard owner-reported instrument for tracking symptom " +
                    "burden. Nine 0-10 ratings plus an optional symptom you name " +
                    "yourself. Useful for palliative care, oncology treatment toxicity, " +
                    "post-surgical recovery, and any chronic condition where you want " +
                    "a structured record of how you actually feel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "You administer this yourself. Bios never infers these scores from " +
                    "biomarkers, never reminds you to fill one in, and never shares " +
                    "a report unless you explicitly export it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EsasSliderRow(
    title: String,
    detail: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$value / 10", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = EsasReport.MIN_SCORE.toFloat()..EsasReport.MAX_SCORE.toFloat(),
                steps = EsasReport.MAX_SCORE - EsasReport.MIN_SCORE - 1,
            )
        }
    }
}

@Composable
private fun OtherSymptomCard(
    label: String,
    score: Int,
    onLabelChange: (String) -> Unit,
    onScoreChange: (Int) -> Unit,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Other symptom (optional)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Name a symptom not on the standard list — e.g. \"constipation\", " +
                    "\"neuropathy\", \"itching\" — and rate it 0-10.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text("Symptom name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (label.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Rating", style = MaterialTheme.typography.bodyMedium)
                    Text("$score / 10", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = score.toFloat(),
                    onValueChange = { onScoreChange(it.toInt()) },
                    valueRange = EsasReport.MIN_SCORE.toFloat()..EsasReport.MAX_SCORE.toFloat(),
                    steps = EsasReport.MAX_SCORE - EsasReport.MIN_SCORE - 1,
                )
            }
        }
    }
}

@Composable
private fun EsasReportRow(report: EsasReport, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                formatTimestamp(report.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("Pain ${report.painScore}")
                    append(" • Tired ${report.tirednessScore}")
                    append(" • Drowsy ${report.drowsinessScore}")
                    append(" • Nausea ${report.nauseaScore}")
                    append(" • Appetite ${report.appetiteScore}")
                    append(" • SOB ${report.shortnessOfBreathScore}")
                    append(" • Depression ${report.depressionScore}")
                    append(" • Anxiety ${report.anxietyScore}")
                    append(" • Wellbeing ${report.wellbeingScore}")
                    if (report.otherSymptomLabel != null && report.otherSymptomScore != null) {
                        append(" • ${report.otherSymptomLabel} ${report.otherSymptomScore}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private val rowDateFormat = java.text.SimpleDateFormat("MMM d, yyyy HH:mm", java.util.Locale.US)
private fun formatTimestamp(millis: Long): String = rowDateFormat.format(java.util.Date(millis))
