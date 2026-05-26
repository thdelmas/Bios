package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bios.app.ingest.PhoneSleepPrefs
import com.bios.app.ingest.PhoneSleepWorker
import kotlinx.coroutines.launch

/**
 * Settings → Sleep inference card.
 *
 * Two owner controls for the phone-sensor sleep auto-derivation:
 *
 *  1. **Wake hour** — the local hour-of-day at or after which the
 *     periodic worker considers last night "complete" and fires the
 *     inference. Lowering it means an early riser sees the sleep card
 *     populated sooner; raising it means a late sleeper avoids a
 *     partial pre-wake inference that the scheduler's dedupe would
 *     then permanently freeze.
 *
 *  2. **Infer last night now** — on-demand escape hatch that ignores
 *     the wake-hour gate and the midpoint dedupe, so a misconfigured
 *     hour or a weird-schedule night never leaves the owner waiting
 *     on the clock. Calls [PhoneSleepWorker.runImmediateInference].
 *
 * Lives in its own file because [SettingsScreen] is near the 500-line
 * limit; new cards land here per the file-organization convention.
 */
@Composable
internal fun SettingsPhoneSleepCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wakeHour by remember { mutableStateOf(PhoneSleepPrefs.wakeHour(context)) }
    var inferring by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sleep inference", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            Text(
                "Bios infers last night's sleep from phone sensors after you wake. " +
                    "Adjust the wake hour if you rise before 9 AM, or tap " +
                    "\"Infer now\" to skip the wait.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wake hour", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatHour(wakeHour) + " local",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = (wakeHour - 1).coerceAtLeast(PhoneSleepPrefs.MIN_WAKE_HOUR)
                            if (next != wakeHour) {
                                wakeHour = next
                                PhoneSleepPrefs.setWakeHour(context, next)
                            }
                        },
                        enabled = wakeHour > PhoneSleepPrefs.MIN_WAKE_HOUR,
                    ) { Text("–", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        wakeHour.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(28.dp),
                    )
                    IconButton(
                        onClick = {
                            val next = (wakeHour + 1).coerceAtMost(PhoneSleepPrefs.MAX_WAKE_HOUR)
                            if (next != wakeHour) {
                                wakeHour = next
                                PhoneSleepPrefs.setWakeHour(context, next)
                            }
                        },
                        enabled = wakeHour < PhoneSleepPrefs.MAX_WAKE_HOUR,
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (inferring) return@OutlinedButton
                    inferring = true
                    lastResult = null
                    scope.launch {
                        val outcome = runCatching {
                            PhoneSleepWorker.runImmediateInference(context)
                        }
                        inferring = false
                        lastResult = outcome.fold(
                            onSuccess = ::describeResult,
                            onFailure = { "Inference failed: ${it.message ?: it.javaClass.simpleName}" },
                        )
                    }
                },
                enabled = !inferring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (inferring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Inferring…")
                } else {
                    Text("Infer last night now")
                }
            }

            lastResult?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

private fun describeResult(result: PhoneSleepWorker.ImmediateResult): String = when (result) {
    is PhoneSleepWorker.ImmediateResult.Written ->
        "Wrote ${result.count} reading${if (result.count == 1) "" else "s"}."
    PhoneSleepWorker.ImmediateResult.NoSensor ->
        "No accelerometer on this device — phone-sensor inference unavailable."
    PhoneSleepWorker.ImmediateResult.NotEnoughSamples ->
        "Not enough phone-sensor samples buffered yet. The worker needs to run a few times first."
    PhoneSleepWorker.ImmediateResult.NoSleepWindow ->
        "No viable sleep window found in the recent buffer."
}
