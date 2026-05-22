package com.bios.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.engine.PhoneSleepInference
import com.bios.app.ingest.PhoneSleepWorker

@Composable
internal fun EmptyStateCard(
    inferring: Boolean,
    onInferNow: () -> Unit,
    bufferedSamples: Int,
    lastSampleMs: Long?,
    longestQuietMs: Long,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "No sleep readings in the selected window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Connect a wearable, sync Health Connect, log a night manually, " +
                    "or run phone inference now against whatever samples already buffered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PhoneInferenceDiagnostics(bufferedSamples, lastSampleMs, longestQuietMs)
            FilledTonalButton(
                onClick = onInferNow,
                enabled = !inferring,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (inferring) "Running inference…" else "Run phone inference now")
            }
        }
    }
}

@Composable
private fun PhoneInferenceDiagnostics(
    bufferedSamples: Int,
    lastSampleMs: Long?,
    longestQuietMs: Long,
) {
    val minNeeded = PhoneSleepWorker.MIN_SAMPLES_FOR_INFERENCE
    val minWindowMs = PhoneSleepInference.MIN_SLEEP_WINDOW_MS
    val lastSampleText = lastSampleMs?.let { relativeTime(it) } ?: "never"
    val sampleColor = if (bufferedSamples >= minNeeded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val quietColor = if (longestQuietMs >= minWindowMs) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Phone inference buffer: $bufferedSamples / $minNeeded samples",
            style = MaterialTheme.typography.labelSmall,
            color = sampleColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Last sample collected: $lastSampleText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Longest quiet window: ${formatDurationMs(longestQuietMs)} / " +
                "need ${formatDurationMs(minWindowMs)}+",
            style = MaterialTheme.typography.labelSmall,
            color = quietColor,
            fontWeight = FontWeight.SemiBold,
        )
        if (bufferedSamples == 0) {
            Text(
                "The background worker hasn't recorded any sensor samples yet. " +
                    "On Android, periodic work can be delayed by battery savers or " +
                    "aggressive app-killing — check that Bios isn't restricted in " +
                    "battery/optimization settings, then wait ~15 min for the next run.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (longestQuietMs < minWindowMs && bufferedSamples >= minNeeded) {
            Text(
                "Inference needs a continuous screen-off stretch — single " +
                    "screen-on flickers under 20 min are tolerated, but " +
                    "longer wake periods split the stretch. Notifications, " +
                    "always-on-display, or lift-to-wake during sleep are the " +
                    "usual culprits.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InferenceResultCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val h = totalMinutes / 60L
    val m = totalMinutes % 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun relativeTime(timestampMs: Long): String {
    val deltaMs = System.currentTimeMillis() - timestampMs
    if (deltaMs < 0) return "in the future"
    val minutes = deltaMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
