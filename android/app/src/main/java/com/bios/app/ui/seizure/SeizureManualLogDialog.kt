package com.bios.app.ui.seizure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Owner-logging entry dialog for SEIZURE_EVENT (#269 follow-up).
 *
 * Bare first cut on purpose: (start time, duration, optional note).
 * The richer NEUROLOGY_POV §2.12 field set (character, aura,
 * post-ictal state, triggers) is a UX design decision and a
 * follow-up — the minimum-viable surface is what unblocks the URGENT
 * `statusEpilepticusConvulsive` pattern from owner-logged input.
 *
 * Defaults are chosen so the most common case (logging a seizure
 * that just ended) is a one-or-two-tap submit: start time defaults
 * to "X minutes ago" where X is the entered duration, duration
 * defaults blank so the owner must type the meaningful value.
 *
 * Validation runs through [SeizureEntryValidator]; invalid input
 * blocks submission with a visible reason.
 */
@Composable
internal fun SeizureManualLogDialog(
    onDismiss: () -> Unit,
    onSubmit: (timestampMs: Long, durationSec: Int, notes: String?) -> Unit,
    nowMs: Long = System.currentTimeMillis(),
) {
    var durationMinutesText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a seizure event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Records an owner-asserted SEIZURE_EVENT at high confidence. " +
                        "Used by the status-epilepticus and cluster patterns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = durationMinutesText,
                    onValueChange = {
                        durationMinutesText = it.filter { ch -> ch.isDigit() || ch == '.' }
                        error = null
                    },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseDurationSec(durationMinutesText)
                if (parsed == null) {
                    error = "Enter a duration in minutes."
                    return@TextButton
                }
                // The owner is almost always logging an event that
                // just ended; default the start time to (now - duration)
                // so the timestamp aligns with the seizure onset.
                val startMs = nowMs - parsed * 1_000L
                val result = SeizureEntryValidator.validate(
                    timestampMs = startMs,
                    durationSec = parsed,
                    nowMs = nowMs,
                )
                when (result) {
                    is SeizureEntryValidator.Result.Invalid -> error = result.reason
                    is SeizureEntryValidator.Result.Valid ->
                        onSubmit(result.timestampMs, result.durationSec, notes.ifBlank { null })
                }
            }) { Text("Log event") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * Parse the duration text field. Accepts integer or decimal minutes
 * (e.g. "5" or "2.5"). Returns the value in seconds, or null when
 * the input doesn't parse to a positive number.
 */
internal fun parseDurationSec(text: String): Int? {
    val mins = text.toDoubleOrNull() ?: return null
    if (mins <= 0.0) return null
    return (mins * 60).toInt()
}
