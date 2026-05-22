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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.physiology.FrailAssessmentStore
import com.bios.app.physiology.FrailCategory
import com.bios.app.physiology.FrailResponses
import com.bios.app.physiology.FrailScore

/**
 * Settings → Identity → FRAIL assessment.
 *
 * Pull-side, owner-administered FRAIL questionnaire (Morley 2012, IANA).
 * Five Yes/No-ish items; a score of ≥3 activates
 * [com.bios.app.physiology.PhysiologyState.FRAILTY_FLAG] in
 * [com.bios.app.physiology.PhysiologyStateStore] so the four
 * baseline-deviation patterns (sleep_disruption, cardiovascular_stress,
 * cardiorespiratory_deconditioning, recovery_deficit) stop false-firing
 * on a frail >75 baseline that drifts on the deconditioning trajectory
 * by definition (Fried 2001).
 *
 * Closes the FRAILTY_FLAG-is-declared-but-unused half of audit gap §2.1
 * (issue #186; GERIATRICS_PALLIATIVE_POV.md §2.2).
 *
 * Manifesto guards:
 *  - The owner answers; Bios never infers frailty from biomarkers.
 *  - No push notification fires on result; the score is shown here only.
 *  - The owner can retake the assessment any time, or clear it (which
 *    reverts a previously-set FRAILTY_FLAG back to STANDARD only if
 *    the store is still on FRAILTY_FLAG — never clobbers another state).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrailAssessmentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { FrailAssessmentStore(context) }
    val initial = remember { store.latest() ?: FrailResponses() }

    var fatigue by remember { mutableStateOf(initial.fatigueMostOfTime) }
    var resistance by remember { mutableStateOf(initial.cannotClimbTenSteps) }
    var ambulation by remember { mutableStateOf(initial.cannotWalkSeveralHundredYards) }
    var illnessText by remember { mutableStateOf(initial.illnessCount.toString()) }
    var weightLoss by remember { mutableStateOf(initial.unintentionalWeightLossOver5Percent) }
    var recorded by remember { mutableStateOf<FrailCategory?>(null) }
    var illnessError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FRAIL assessment") },
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
            IntroCard()

            QuestionRow(
                title = "Fatigue",
                detail = "Are you fatigued all or most of the time in the past 4 weeks?",
                checked = fatigue,
                onChange = { fatigue = it },
            )
            QuestionRow(
                title = "Resistance",
                detail = "Do you have difficulty climbing 10 steps alone, without resting, without aids?",
                checked = resistance,
                onChange = { resistance = it },
            )
            QuestionRow(
                title = "Ambulation",
                detail = "Do you have difficulty walking several hundred yards alone, without aids?",
                checked = ambulation,
                onChange = { ambulation = it },
            )

            IllnessesField(
                value = illnessText,
                error = illnessError,
                onChange = {
                    illnessText = it
                    illnessError = null
                },
            )

            QuestionRow(
                title = "Loss of weight",
                detail = "Have you lost more than 5% of your body weight in the past year, without intending to?",
                checked = weightLoss,
                onChange = { weightLoss = it },
            )

            Button(
                onClick = {
                    val count = illnessText.toIntOrNull()
                    if (count == null || count !in 0..11) {
                        illnessError = "Enter a number from 0 to 11."
                        return@Button
                    }
                    val responses = FrailResponses(
                        fatigueMostOfTime = fatigue,
                        cannotClimbTenSteps = resistance,
                        cannotWalkSeveralHundredYards = ambulation,
                        illnessCount = count,
                        unintentionalWeightLossOver5Percent = weightLoss,
                    )
                    recorded = store.record(responses)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Record assessment")
            }

            recorded?.let { category ->
                ResultCard(
                    category = category,
                    score = FrailScore.score(
                        FrailResponses(
                            fatigueMostOfTime = fatigue,
                            cannotClimbTenSteps = resistance,
                            cannotWalkSeveralHundredYards = ambulation,
                            illnessCount = illnessText.toIntOrNull()?.coerceIn(0, 11) ?: 0,
                            unintentionalWeightLossOver5Percent = weightLoss,
                        )
                    ),
                )
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "The FRAIL questionnaire (Morley 2012) is a five-item, owner-administered " +
                    "screen for frailty. A score of 3 or more sets your physiology state to " +
                    "frailty, which suppresses four trend patterns calibrated to a stable-adult " +
                    "baseline that false-fire when the baseline itself is drifting on the " +
                    "trajectory of frailty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "You administer this yourself. Bios never infers frailty from biomarkers, " +
                    "and there is no notification — only the result on this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuestionRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun IllnessesField(
    value: String,
    error: String?,
    onChange: (String) -> Unit,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Illnesses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Of these 11 conditions, how many has a physician told you you have? " +
                    "Hypertension, diabetes, cancer, chronic lung disease, heart attack, " +
                    "heart failure, angina, asthma, arthritis, stroke, kidney disease. " +
                    "5 or more scores 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text("Count (0–11)") },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ResultCard(category: FrailCategory, score: Int) {
    val color = when (category) {
        FrailCategory.ROBUST -> MaterialTheme.colorScheme.primaryContainer
        FrailCategory.PRE_FRAIL -> MaterialTheme.colorScheme.tertiaryContainer
        FrailCategory.FRAIL -> MaterialTheme.colorScheme.errorContainer
    }
    val explanation = when (category) {
        FrailCategory.ROBUST ->
            "Robust (score $score of 5). No FRAIL items active. Your physiology state was " +
                "not changed."
        FrailCategory.PRE_FRAIL ->
            "Pre-frail (score $score of 5). One or two FRAIL items active. Your physiology " +
                "state was not changed. Pre-frailty is a heads-up surface; pattern gating " +
                "activates at frail (3+) per Morley 2012."
        FrailCategory.FRAIL ->
            "Frail (score $score of 5). Three or more FRAIL items active. Your physiology " +
                "state is now Frailty / age >75, which suppresses four baseline-trend " +
                "patterns (sleep_disruption, cardiovascular_stress, " +
                "cardiorespiratory_deconditioning, recovery_deficit) calibrated to a " +
                "stable-adult baseline."
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(category.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(explanation, style = MaterialTheme.typography.bodySmall)
        }
    }
}
