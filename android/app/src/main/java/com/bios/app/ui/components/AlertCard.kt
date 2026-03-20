package com.bios.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import org.json.JSONObject
import kotlin.math.abs

@Composable
fun AlertCard(
    anomaly: Anomaly,
    onAcknowledge: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val tier = AlertTier.fromLevel(anomaly.severity)
    val tierColor = tierColor(tier)

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
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(tier, tierColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    anomaly.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))

                    // Explanation
                    Text(
                        anomaly.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Deviation scores
                    DeviationScoresList(anomaly.deviationScores)

                    // Suggested action
                    anomaly.suggestedAction?.let { action ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("💡 ", style = MaterialTheme.typography.bodySmall)
                            Text(
                                action,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Acknowledge button
                    if (!anomaly.acknowledged) {
                        Button(
                            onClick = onAcknowledge,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tierColor
                            )
                        ) {
                            Text("Acknowledge")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(tier: AlertTier, color: Color) {
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
                        color = if (abs(zScore) > 2) Color(0xFFFF9800) else Color(0xFFFFC107)
                    )
                }
            }
        }
    }
}

private fun tierColor(tier: AlertTier): Color {
    return when (tier) {
        AlertTier.OBSERVATION -> Color.Gray
        AlertTier.NOTICE -> Color(0xFFFFC107)
        AlertTier.ADVISORY -> Color(0xFFFF9800)
        AlertTier.URGENT -> Color(0xFFF44336)
    }
}
