package com.bios.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.ui.theme.BiosTokens
import org.json.JSONObject
import kotlin.math.abs

/** What the owner writes about an alert. Earlier versions also asked two more questions; stored answers are still shown. */
data class FeedbackInput(
    val feltSick: Boolean? = null,
    val symptoms: String? = null,
    val notes: String? = null,
    val outcomeAccurate: Boolean? = null
)

@Composable
fun AlertCard(
    anomaly: Anomaly,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (FeedbackInput) -> Unit = {},
    onOpenDetail: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val tier = AlertTier.fromLevel(anomaly.severity)
    val tierColor = BiosTokens.tierColor(tier)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = tierColor.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(tierColor.copy(alpha = 0.2f))
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(tier, tierColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    anomaly.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (anomaly.feedbackAt != null) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Journal entry saved",
                        tint = BiosTokens.success,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                AlertCardBody(anomaly, tierColor, onAcknowledge, onSaveFeedback, onOpenDetail)
            }
        }
    }
}

/** Expanded card: the measured lines, the deviations, then acknowledge and the owner's journal. */
@Composable
private fun AlertCardBody(
    anomaly: Anomaly,
    tierColor: Color,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (FeedbackInput) -> Unit,
    onOpenDetail: (() -> Unit)?,
) {
    var showFeedback by remember { mutableStateOf(false) }
    val hasFeedback = anomaly.feedbackAt != null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))

        AlertSection(label = "What Bios measured") {
            ParagraphText(anomaly.explanation)
        }

        DeviationScoresList(anomaly.deviationScores)

        onOpenDetail?.let { open ->
            TextButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
                Text("See the measurements")
            }
        }

        if (hasFeedback) {
            FeedbackSummary(anomaly)
        }

        if (!anomaly.acknowledged) {
            Button(
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = tierColor)
            ) {
                Text("Acknowledge")
            }
        } else if (!hasFeedback) {
            if (!showFeedback) {
                OutlinedButton(onClick = { showFeedback = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add a journal entry")
                }
            }
            AnimatedVisibility(visible = showFeedback) {
                FeedbackForm(onSubmit = { input ->
                    onSaveFeedback(input)
                    showFeedback = false
                })
            }
        }
    }
}

@Composable
private fun AlertSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/** Renders body text split on blank lines so long explanations are scannable. */
@Composable
private fun ParagraphText(text: String) {
    val paragraphs = remember(text) {
        text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        paragraphs.forEach { para ->
            Text(
                para,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun FeedbackForm(onSubmit: (FeedbackInput) -> Unit) {
    var feltSick by remember { mutableStateOf<Boolean?>(null) }
    var symptoms by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var outcomeAccurate by remember { mutableStateOf<Boolean?>(null) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Health Journal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Did you feel sick?
            YesNoQuestion("Did you feel sick?", feltSick) { feltSick = it }

            // Was the alert accurate?
            YesNoQuestion("Was this alert accurate?", outcomeAccurate) {
                outcomeAccurate = it
            }

            // Symptoms
            OutlinedTextField(
                value = symptoms,
                onValueChange = { symptoms = it },
                label = { Text("Symptoms you noticed") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Button(
                onClick = {
                    onSubmit(
                        FeedbackInput(
                            feltSick = feltSick,
                            symptoms = symptoms.ifBlank { null },
                            notes = notes.ifBlank { null },
                            outcomeAccurate = outcomeAccurate
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun YesNoQuestion(
    label: String,
    value: Boolean?,
    onChanged: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = value == true,
                onClick = { onChanged(true) },
                label = { Text("Yes") }
            )
            FilterChip(
                selected = value == false,
                onClick = { onChanged(false) },
                label = { Text("No") }
            )
        }
    }
}

@Composable
internal fun FeedbackSummary(anomaly: Anomaly) {
    val tint = BiosTokens.success
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Your Journal Entry",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )

            anomaly.feltSick?.let {
                Text(
                    if (it) "Felt sick: Yes" else "Felt sick: No",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            anomaly.outcomeAccurate?.let {
                Text(
                    if (it) "Alert was accurate" else "Alert was not accurate",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            anomaly.visitedDoctor?.let {
                Text(
                    if (it) "Visited doctor" else "Did not visit doctor",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            anomaly.diagnosis?.let {
                Text("Diagnosis: $it", style = MaterialTheme.typography.bodySmall)
            }
            anomaly.symptoms?.let {
                Text("Symptoms: $it", style = MaterialTheme.typography.bodySmall)
            }
            anomaly.notes?.let {
                Text("Notes: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SeverityBadge(tier: AlertTier, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = tier.label.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DeviationScoresList(scoresJson: String) {
    val entries = remember(scoresJson) {
        try {
            val scores = JSONObject(scoresJson)
            scores.keys().asSequence().map { key ->
                key to scores.getDouble(key)
            }.sortedByDescending { abs(it.second) }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    if (entries.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            entries.forEach { (key, zScore) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        key.replace("_", " ")
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        String.format("%+.1fσ", zScore),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (abs(zScore) > 2) BiosTokens.attention else BiosTokens.warning
                    )
                }
            }
        }
    }
}

