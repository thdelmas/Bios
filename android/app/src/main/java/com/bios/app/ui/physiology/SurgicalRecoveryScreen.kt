package com.bios.app.ui.physiology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bios.app.physiology.PerioperativeBaselineFreezer
import com.bios.app.physiology.PerioperativeStateStore
import com.bios.app.physiology.PerioperativeStateTransitionWorker
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Identity → Surgical recovery (#183, SURGICAL_POV §2.1).
 *
 * Pull-side surface for the owner to declare their peri-operative
 * context — an upcoming or recent surgery. Setting a date drives:
 *
 *   1. A frozen [com.bios.app.physiology.PerioperativeBaseline] snapshot
 *      taken from the current rolling baselines.
 *   2. A [PhysiologyState] transition: future date → PREHAB_WINDOW,
 *      past date → POD_0_30 / POD_30_90 / POD_90_PLUS based on
 *      days-since-procedure.
 *   3. Activation of the post-op pattern library
 *      (ssi_surveillance, anastomotic_leak_screen, post_op_vte_screen)
 *      and suppression of the baseline-relative patterns that would
 *      false-fire during normal post-op physiology (infection_onset,
 *      cardiovascular_stress, recovery_deficit, etc.).
 *
 * Matches the FRAIL / Physiology state UI patterns: small explainer
 * card, owner picks date, optional free-text tag, save and clear
 * controls.
 *
 * Manifesto guard: Bios never *infers* this context — the owner sets it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurgicalRecoveryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val perioStore = remember(context) { PerioperativeStateStore(context) }
    val stateStore = remember(context) { PhysiologyStateStore(context) }
    val freezer = remember(context) { PerioperativeBaselineFreezer(BiosDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()

    var procedureDate by remember { mutableStateOf<Long?>(null) }
    var procedureTag by remember { mutableStateOf("") }
    var currentState by remember { mutableStateOf(stateStore.current()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        procedureDate = perioStore.procedureDate()
        procedureTag = perioStore.procedureTag().orEmpty()
        currentState = stateStore.current()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Surgical recovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ManifestoCard()

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Procedure date", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        procedureDate?.let { formatDate(it) }
                            ?: "Not set — Bios is treating you as a stable adult.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (procedureDate == null) "Set procedure date" else "Change date")
                    }
                    if (procedureDate != null) {
                        Text(
                            "Future date → Pre-op prehabilitation window. Past date → Post-op recovery window.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Procedure tag (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Free text for your own recall — e.g. \"sigmoidectomy\", \"total knee arthroplasty\". " +
                            "Bios does not branch its detection logic on the tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = procedureTag,
                        onValueChange = { procedureTag = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current physiology state", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(currentState.displayName, style = MaterialTheme.typography.bodyMedium)
                    statusMessage?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            val date = procedureDate
            Button(
                onClick = {
                    val pickedDate = date ?: return@Button
                    scope.launch {
                        saving = true
                        perioStore.setProcedure(pickedDate, procedureTag.trim().ifBlank { null })
                        freezer.freeze(pickedDate, procedureTag.trim().ifBlank { null })
                        val newState = perioStore.expectedState() ?: PhysiologyState.STANDARD
                        stateStore.set(newState)
                        PerioperativeStateTransitionWorker.schedule(context)
                        currentState = newState
                        statusMessage = "Pre-op snapshot frozen. Bios will follow the post-op trajectory."
                        saving = false
                    }
                },
                enabled = !saving && date != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save procedure date and freeze baseline")
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        perioStore.clear()
                        freezer.clear()
                        PerioperativeStateTransitionWorker.cancel(context)
                        if (currentState in PhysiologyState.PERIOPERATIVE) {
                            stateStore.set(PhysiologyState.STANDARD)
                            currentState = PhysiologyState.STANDARD
                        }
                        procedureDate = null
                        procedureTag = ""
                        statusMessage = "Surgical recovery context cleared."
                    }
                },
                enabled = procedureDate != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear surgical recovery")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = procedureDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    procedureDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Surgery is event-driven. Your resting heart rate runs 10-20 bpm above " +
                    "pre-op for 2-4 weeks of normal recovery, your sleep is fragmented, your " +
                    "skin temperature is mildly elevated through POD3-5. Bios's standard " +
                    "patterns assume a stable adult baseline — applied to a post-op owner, " +
                    "they would either false-fire constantly or miss the genuine surgical " +
                    "site infection at POD7-14.\n\n" +
                    "Set your procedure date and Bios will: (1) freeze your current baseline " +
                    "as a pre-op reference, (2) suppress patterns whose deviations are " +
                    "normative post-op, (3) activate the NHSN 30-day SSI surveillance pattern, " +
                    "the POD3-7 anastomotic-leak screen, and the post-op VTE/PE pattern. " +
                    "Bios never infers a procedure — only you can set this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date(millis))
}
