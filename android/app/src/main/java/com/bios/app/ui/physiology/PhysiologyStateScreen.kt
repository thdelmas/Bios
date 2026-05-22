package com.bios.app.ui.physiology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.physiology.PaediatricBandCalculator
import com.bios.app.physiology.PaediatricBandWorker
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Settings → Physiology state. Closes audit gap §2.7 + #198.
 *
 * Pull-side picker for the owner's physiological context. Default is
 * STANDARD; the owner switches when their physiology genuinely differs
 * from a stable adult baseline (pregnancy, postpartum, athlete, frailty,
 * paediatric). The selected state filters which condition patterns the
 * anomaly detector runs — patterns with normative deviations in that
 * state are suppressed so a non-pathologic RHR rise in pregnancy doesn't
 * fire `cardiovascular_stress`.
 *
 * Paediatric extension (#198): rather than ask the owner to pick the
 * right band manually from six options, the screen accepts a birth date
 * (ISO `yyyy-MM-dd`) and derives the band via [PaediatricBandCalculator].
 * The band is then recomputed daily by [PaediatricBandWorker] so a child
 * who crosses an age boundary (e.g. 13th birthday → ADOLESCENT) gets
 * the right thresholds without manual intervention. Birth date is the
 * only owner-supplied input that drives automatic band selection —
 * Bios never infers age from biomarkers.
 *
 * Manifesto guard: Bios never infers state. The owner picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysiologyStateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PhysiologyStateStore(context) }
    var current by remember { mutableStateOf(store.current()) }
    var birthDateText by remember {
        mutableStateOf(store.birthDate()?.toString() ?: "")
    }
    var birthDateError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physiology state") },
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
            ManifestoCard()
            PaediatricBandCard(
                birthDateText = birthDateText,
                onBirthDateChange = {
                    birthDateText = it
                    birthDateError = null
                },
                error = birthDateError,
                onSave = {
                    val (parsed, error) = parseBirthDate(birthDateText)
                    if (error != null) {
                        birthDateError = error
                    } else {
                        store.setBirthDate(parsed)
                        val band = PaediatricBandWorker.runOnce(context)
                        current = band
                    }
                },
                onClear = {
                    store.setBirthDate(null)
                    birthDateText = ""
                    birthDateError = null
                    // Don't auto-flip the current state; the owner may
                    // have set a non-paediatric state intentionally.
                },
                derivedBand = parseBirthDate(birthDateText).first?.let {
                    PaediatricBandCalculator.bandFor(it)
                },
            )
            for (state in PhysiologyState.entries) {
                StateOption(
                    state = state,
                    selected = state == current,
                    onSelect = {
                        store.set(state)
                        current = state
                    },
                )
            }
        }
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios's anomaly patterns assume a stable adult baseline. Pregnancy " +
                    "raises resting heart rate 10–20 bpm by trimester 2 — that's " +
                    "normative, not pathology. Athletes have low resting heart rates by " +
                    "design. Paediatric and frailty bodies follow different rules. " +
                    "Known heart failure unlocks a HeartLogic-lite decompensation-" +
                    "prodrome pattern that only makes sense if a clinician has made " +
                    "that diagnosis. Pick the state that matches your context so Bios " +
                    "doesn't false-flag what's normal for you. Default is Standard " +
                    "adult; nothing is set automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaediatricBandCard(
    birthDateText: String,
    onBirthDateChange: (String) -> Unit,
    error: String?,
    onSave: () -> Unit,
    onClear: () -> Unit,
    derivedBand: PhysiologyState?,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Paediatric profile (under 18)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "If this profile belongs to a child, enter their birth date " +
                    "(YYYY-MM-DD). Bios derives the PALS / APLS age band from " +
                    "the date and recomputes it daily, so emergency vital-sign " +
                    "thresholds match the age — adult cutoffs are wrong for " +
                    "children. You can clear the date and pick a non-paediatric " +
                    "state at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = birthDateText,
                onValueChange = onBirthDateChange,
                label = { Text("Birth date (YYYY-MM-DD)") },
                isError = error != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            derivedBand?.let {
                Text(
                    "Computed band: ${it.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text("Save birth date")
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun StateOption(
    state: PhysiologyState,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    OutlinedButton(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else ButtonDefaults.outlinedButtonColors(),
    ) {
        Text(if (selected) "✓ ${state.displayName}" else state.displayName)
    }
}

/**
 * Parse a `YYYY-MM-DD` birth-date string. Empty input returns
 * `(null, null)` — clearing the field is a valid no-op. Returns the
 * parsed [LocalDate] and `null` error on success, or `(null, message)`
 * on a parse failure / future-date input.
 */
internal fun parseBirthDate(text: String): Pair<LocalDate?, String?> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null to null
    val parsed = try {
        LocalDate.parse(trimmed)
    } catch (_: DateTimeParseException) {
        return null to "Use YYYY-MM-DD (e.g. 2018-07-15)."
    }
    if (parsed.isAfter(LocalDate.now())) {
        return null to "Birth date can't be in the future."
    }
    return parsed to null
}

