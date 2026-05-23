package com.bios.app.ui.physiology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bios.app.physiology.CancerTherapyDrugClass
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore

/**
 * Settings → Physiology state. Closes audit gap §2.7.
 *
 * Pull-side picker for the owner's physiological context. Default is
 * STANDARD; the owner switches when their physiology genuinely differs
 * from a stable adult baseline (pregnancy, postpartum, athlete, frailty,
 * paediatric). The selected state filters which condition patterns the
 * anomaly detector runs — patterns with normative deviations in that
 * state are suppressed so a non-pathologic RHR rise in pregnancy doesn't
 * fire `cardiovascular_stress`.
 *
 * Manifesto guard: Bios never infers state. The owner picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysiologyStateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PhysiologyStateStore(context) }
    var current by remember { mutableStateOf(store.current()) }
    var currentDrugClass by remember { mutableStateOf(store.drugClass()) }

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
            for (state in PhysiologyState.entries) {
                StateOption(
                    state = state,
                    selected = state == current,
                    onSelect = {
                        store.set(state)
                        current = state
                        // Setting a non-cancer state clears the drug class
                        // (PhysiologyStateStore.set handles this), so reflect
                        // that in the UI state too.
                        if (state !in PhysiologyState.CANCER_TREATMENT) {
                            currentDrugClass = CancerTherapyDrugClass.NONE
                        }
                    },
                )
            }
            // Cancer-therapy drug-class picker (#201). Shown only when the
            // owner has selected a cancer-treatment state; most cardio-
            // oncology patterns gate on the state AND the drug class.
            if (current in PhysiologyState.CANCER_TREATMENT) {
                DrugClassPickerCard()
                for (drugClass in CancerTherapyDrugClass.entries) {
                    DrugClassOption(
                        drugClass = drugClass,
                        selected = drugClass == currentDrugClass,
                        onSelect = {
                            store.setDrugClass(drugClass)
                            currentDrugClass = drugClass
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrugClassPickerCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cancer-therapy drug class", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Pick the drug class that matches your regimen. Cardio-oncology " +
                    "surveillance patterns (anthracycline / trastuzumab cardiotoxicity, " +
                    "ICI pneumonitis / colitis / thyroiditis) are class-specific — Bios " +
                    "only runs the patterns that match what you're on. Default is " +
                    "Not specified; nothing fires until you pick.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DrugClassOption(
    drugClass: CancerTherapyDrugClass,
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
        Text(if (selected) "✓ ${drugClass.displayName}" else drugClass.displayName)
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
