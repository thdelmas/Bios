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

@Composable
fun AlertCard(
    anomaly: Anomaly,
    onAcknowledge: () -> Unit,
    onSaveFeedback: (String) -> Unit = {},
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
    onSaveFeedback: (String) -> Unit,
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
                FeedbackForm(onSubmit = { note ->
                    onSaveFeedback(note)
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

/** The owner's journal for an alert: one free-text note. */
@Composable
internal fun FeedbackForm(onSubmit: (String) -> Unit) {
    var note by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Journal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("What happened?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            Button(
                onClick = { onSubmit(note.trim()) },
                enabled = note.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

/**
 * The owner's saved journal entry. Entries written by earlier versions can
 * carry a second free-text field; both texts are shown as written. Answers
 * to questions that are no longer asked stay stored and are not shown.
 */
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
            listOfNotNull(anomaly.symptoms, anomaly.notes).filter { it.isNotBlank() }.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
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

