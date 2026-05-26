package com.bios.app.ui.reproductive

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
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore

/**
 * Settings → PCOS / Endometriosis context (#209, OBGYN §2.13-§2.14).
 *
 * Owner-recorded flags. The owner toggles whichever applies; Bios uses
 * the flag as a [PhysiologyState] gate. PCOS flips the engine into
 * heightened glucose/HbA1c sensitivity (Moran et al. 2010, Hum Reprod
 * Update — PCOS roughly doubles lifetime T2DM risk). Endometriosis
 * surfaces pelvic-pain symptom tracking on the ESAS-r framework.
 *
 * Manifesto guard: Bios never infers either condition. Owner-set,
 * owner-readable, owner-erasable. The flags only modulate engine
 * behaviour; the diagnosis is the owner's, not the app's.
 *
 * PhysiologyState only carries one value at a time — owners who have
 * both PCOS and endometriosis can pick whichever they want to gate on;
 * follow-up issues can shift to a multi-flag model when more conditions
 * accumulate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcosEndometriosisScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PhysiologyStateStore(context) }
    var current by remember { mutableStateOf(store.current()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PCOS / Endometriosis context") },
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
            FlagButton(
                label = "Known PCOS",
                description = "Heightens glucose / HbA1c sensitivity (Moran et al. 2010 — PCOS roughly doubles lifetime T2DM risk).",
                target = PhysiologyState.KNOWN_PCOS,
                current = current,
                onSelect = {
                    store.set(it)
                    current = it
                },
            )
            FlagButton(
                label = "Known endometriosis",
                description = "Surfaces pelvic-pain symptom tracking on the ESAS-r framework.",
                target = PhysiologyState.KNOWN_ENDOMETRIOSIS,
                current = current,
                onSelect = {
                    store.set(it)
                    current = it
                },
            )
            FlagButton(
                label = "Clear (Standard adult)",
                description = "Remove the PCOS / endometriosis flag.",
                target = PhysiologyState.STANDARD,
                current = current,
                onSelect = {
                    store.set(it)
                    current = it
                },
            )
        }
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Owner-recorded context", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios uses these flags to modulate which patterns evaluate and " +
                    "how. The diagnosis is yours, not the app's — Bios never " +
                    "infers PCOS from cycle irregularity or endometriosis from " +
                    "pelvic pain. PhysiologyState only carries one value at a " +
                    "time; if both apply, pick whichever you want the engine " +
                    "to gate on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlagButton(
    label: String,
    description: String,
    target: PhysiologyState,
    current: PhysiologyState,
    onSelect: (PhysiologyState) -> Unit,
) {
    val selected = current == target
    OutlinedButton(
        onClick = { onSelect(target) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else ButtonDefaults.outlinedButtonColors(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(if (selected) "✓ $label" else label, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
