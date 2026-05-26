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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.bios.app.data.MenopauseStageEntryRepo
import com.bios.app.model.MenopauseStage
import com.bios.app.model.MenopauseStageEntry
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import kotlinx.coroutines.launch

/**
 * Settings → Menopause / perimenopause (#209). STRAW+10 staging (Harlow
 * SD et al. 2012, J Clin Endocrinol Metab 97:1159-1168).
 *
 * Owner picks their stage. The screen also offers to mirror the choice
 * into [PhysiologyState] so the cycle-anomaly pattern is suppressed for
 * peri- and post-menopausal owners — the audit-explicit normative-misread
 * fix.
 *
 * Manifesto guard: STRAW+10 is the medical reference, but the *owner*
 * stages themselves. Bios does not infer perimenopause from cycle gaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenopauseStageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { MenopauseStageEntryRepo(context) }
    val physStore = remember(context) { PhysiologyStateStore(context) }
    val scope = rememberCoroutineScope()

    var latest by remember { mutableStateOf<MenopauseStageEntry?>(null) }
    var notes by remember { mutableStateOf("") }
    var currentPhys by remember { mutableStateOf(physStore.current()) }

    LaunchedEffect(Unit) {
        latest = repo.fetchLatest()
    }

    val latestStage = latest?.stage?.let {
        runCatching { MenopauseStage.valueOf(it) }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Menopause / perimenopause") },
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
            Text(
                "Current stage: ${latestStage?.displayName ?: "not set"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            for (stage in MenopauseStage.entries) {
                StageOption(
                    stage = stage,
                    selected = stage == latestStage,
                    onSelect = {
                        scope.launch {
                            repo.add(stage, System.currentTimeMillis(), notes.trim().takeIf { it.isNotBlank() })
                            latest = repo.fetchLatest()
                            // Mirror into PhysiologyState so the
                            // menstrual-cycle-anomaly pattern is
                            // suppressed for peri-/postmenopausal owners.
                            val mapped = mapToPhysiology(stage)
                            if (mapped != null) {
                                physStore.set(mapped)
                                currentPhys = mapped
                            }
                        }
                    },
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (saved with next selection)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (latest != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Physiology state mirroring", fontWeight = FontWeight.Bold)
                        Text(
                            "Bios's anomaly engine reads PhysiologyState = $currentPhys. " +
                                "Perimenopause / postmenopause states suppress the " +
                                "menstrual-cycle-anomaly pattern because variable " +
                                "cycle length IS the perimenopausal physiology — " +
                                "STRAW+10 names it as the defining marker of the " +
                                "transition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun mapToPhysiology(stage: MenopauseStage): PhysiologyState? = when (stage) {
    MenopauseStage.PREMENOPAUSE -> PhysiologyState.STANDARD
    MenopauseStage.PERIMENOPAUSE_EARLY,
    MenopauseStage.PERIMENOPAUSE_LATE -> PhysiologyState.PERIMENOPAUSE
    MenopauseStage.POSTMENOPAUSE -> PhysiologyState.POSTMENOPAUSE
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("STRAW+10 staging", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Stages of Reproductive Aging Workshop + 10 (Harlow et al. 2012) " +
                    "is the international reference for menopause staging. You stage " +
                    "yourself; Bios doesn't infer. Picking perimenopause or " +
                    "postmenopause tells Bios's cycle-anomaly pattern to stand " +
                    "down — variable cycles in this phase are physiology, not " +
                    "pathology. Stored in the separately-encrypted reproductive DB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StageOption(
    stage: MenopauseStage,
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
        Text(if (selected) "✓ ${stage.displayName}" else stage.displayName)
    }
}
