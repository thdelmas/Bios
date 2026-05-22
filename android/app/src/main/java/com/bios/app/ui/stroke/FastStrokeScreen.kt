package com.bios.app.ui.stroke

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.FastStrokeEventRepo
import kotlinx.coroutines.launch

/**
 * Settings → Identity → "FAST stroke check" entry surface (issue #207,
 * neurology audit §2.15). The AHA / ASA FAST mnemonic —
 * **F**acial drooping, **A**rm weakness, **S**peech difficulty, **T**ime
 * to call — is the public-health stroke-recognition tool the National
 * Stroke Association, the WHO, and the American Heart Association all
 * teach for lay-bystander screening.
 *
 * Owner-input only. The neurology audit §3.3 and the Bios manifesto
 * explicitly prohibit automated voice / face / handwriting stroke
 * detection — every field on this screen is set by the owner (or by
 * someone tapping through the FAST screen on the owner's behalf when
 * the owner can't operate the phone).
 *
 * When **any** of the three observable items is on, the screen surfaces
 * a prominent URGENT-tier card with a one-tap "Call emergency services"
 * action. The call action fires an `Intent.ACTION_DIAL` — the system
 * dialer opens with the number pre-filled and the owner taps to dial.
 * Bios does not place the call itself; the manifesto's "owner is final"
 * principle holds even when seconds count.
 *
 * Region emergency numbers are intentionally not hard-coded here — the
 * generic `tel:112` (the GSM-standard emergency number recognised on
 * every cellular network worldwide, which then routes to 911 in the US,
 * 999 in the UK, etc.) is the safest default until the per-region
 * emergency-number infrastructure ships.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastStrokeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { FastStrokeEventRepo(BiosDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()

    var facialDrooping by remember { mutableStateOf(false) }
    var armWeakness by remember { mutableStateOf(false) }
    var speechDifficulty by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var recordedAt by remember { mutableStateOf<Long?>(null) }

    val isPositive = facialDrooping || armWeakness || speechDifficulty

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAST stroke check") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { IntroCard() }

            item {
                FastItemCard(
                    letter = "F",
                    title = "Face — sudden facial drooping?",
                    description = "Ask the person to smile. Does one side of the face droop?",
                    checked = facialDrooping,
                    onCheckedChange = { facialDrooping = it },
                )
            }
            item {
                FastItemCard(
                    letter = "A",
                    title = "Arms — arm weakness?",
                    description = "Ask the person to raise both arms. Does one arm drift downward?",
                    checked = armWeakness,
                    onCheckedChange = { armWeakness = it },
                )
            }
            item {
                FastItemCard(
                    letter = "S",
                    title = "Speech — speech difficulty?",
                    description = "Ask the person to repeat a simple sentence. Is the speech slurred or strange?",
                    checked = speechDifficulty,
                    onCheckedChange = { speechDifficulty = it },
                )
            }

            if (isPositive) {
                item { TimeUrgentCard(onCallEmergency = { context.dialEmergency() }) }
            }

            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (which side, additional symptoms)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            repo.add(
                                timestamp = now,
                                facialDrooping = facialDrooping,
                                armWeakness = armWeakness,
                                speechDifficulty = speechDifficulty,
                                notes = notes.trim().takeIf { it.isNotBlank() },
                            )
                            recordedAt = now
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Record this check") }
            }

            recordedAt?.let {
                item {
                    Text(
                        "Recorded. The check is now in the diary; the entry is timestamped so a clinician can read the deficit-onset window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                "What is FAST?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "FAST is the AHA / ASA / WHO stroke-recognition mnemonic for lay bystanders: Face drooping, Arm weakness, Speech difficulty, Time to call emergency services. If any one of the three observable items is positive, the recommended action is immediate emergency-services contact — even a single deficit warrants the call. Stroke care is time-critical: every minute of large-vessel-occlusion ischaemia destroys roughly 1.9 million neurons (Saver 2006, Stroke).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "This screen is owner-input only. Bios does not infer stroke from voice, face, or handwriting — that capability is deliberately out of scope per the neurology audit and the Bios manifesto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FastItemCard(
    letter: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "$letter — $title",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(description, style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    if (checked) "Yes — positive" else "No",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }
}

@Composable
private fun TimeUrgentCard(onCallEmergency: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "T — Time to call emergency services",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
            )
            Text(
                "One or more FAST items is positive. AHA / ASA guidance: even a single positive FAST item warrants an immediate emergency-services call. Note the time you noticed the deficit — the assessing clinician needs the deficit-onset window to determine treatment options.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onError,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onCallEmergency,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onError,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Phone, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("Call emergency services", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onCallEmergency,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Or dial 112 / 911 manually", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

/**
 * Fires `Intent.ACTION_DIAL` with `tel:112` so the system dialer opens
 * pre-filled with the GSM-standard worldwide emergency code. Bios does
 * not place the call itself — the owner / bystander taps the dialer to
 * dial. Manifesto: the owner is final.
 *
 * `tel:112` is recognised on every GSM / 3G / 4G / 5G cellular network
 * worldwide and routes to the local emergency dispatcher (911 in the
 * US, 999 in the UK, 000 in AU, 110 / 119 in JP, etc.). A future
 * per-region override should land when the region-emergency-number
 * config ships (the audit's PR #228 work).
 */
private fun android.content.Context.dialEmergency(emergencyNumber: String = "112") {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
