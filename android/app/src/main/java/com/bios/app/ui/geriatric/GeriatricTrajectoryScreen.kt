package com.bios.app.ui.geriatric

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.bios.app.alerts.GeriatricTrajectoryStore
import com.bios.app.data.BiosDatabase
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import com.bios.contracts.MetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pull-side geriatric trajectory view (#208, GERIATRICS_PALLIATIVE_POV
 * §2.3 + §2.4 + §2.5).
 *
 * The owner navigates here from Settings → Geriatric trajectory. The
 * surface is strictly pull-side — Bios never pushes a notification to
 * bring the owner here. The screen surfaces three trajectory components:
 *
 *  1. Gait-variability CoV trend (the [MetricType.GAIT_VARIABILITY_COV]
 *     time series, anchored on the Hausdorff 2007 / Verghese 2009
 *     framework).
 *  2. Typing-speed trend ([MetricType.TYPING_SPEED_CHAR_PER_MIN]) — only
 *     populated when the owner has opted into cognitive tracking.
 *  3. Circadian fragmentation (CIRCADIAN_PHASE_SHIFT) and sleep
 *     regularity (SLEEP_REGULARITY) as supporting context.
 *
 * The owner-set toggles live here too:
 *  - Fall-risk concern (opt-in)
 *  - Cognitive tracking (opt-in for typing-speed capture)
 *  - Age-≥75 declaration (used alongside FRAILTY_FLAG)
 *  - Recent hospital / ED discharge (timestamp; 30-day window auto-expires)
 *
 * Manifesto guard: no classification, no scores, no "fall risk" label,
 * no "MCI risk" label. The page surfaces *the owner's data trajectories*
 * with their published-framework references. Evaluation belongs to the
 * owner and their clinician.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeriatricTrajectoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember(context) { BiosDatabase.getInstance(context) }
    val store = remember(context) { GeriatricTrajectoryStore(context) }
    val physStore = remember(context) { PhysiologyStateStore(context) }

    var fallRiskConcern by remember { mutableStateOf(store.fallRiskConcern) }
    var ageOver75 by remember { mutableStateOf(store.ageOver75) }
    var cognitiveTracking by remember { mutableStateOf(store.cognitiveTrackingEnabled) }
    var dischargeAt by remember { mutableStateOf(store.recentDischargeAtMillis) }

    var gaitCovValues by remember { mutableStateOf<List<Double>>(emptyList()) }
    var typingValues by remember { mutableStateOf<List<Double>>(emptyList()) }
    var sleepRegValues by remember { mutableStateOf<List<Double>>(emptyList()) }

    val now = System.currentTimeMillis()
    val windowMs = 30L * 24L * 3600L * 1000L

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val readingDao = db.metricReadingDao()
            gaitCovValues = readingDao.fetchValues(
                MetricType.GAIT_VARIABILITY_COV.key,
                now - windowMs,
                now,
                null,
            )
            typingValues = readingDao.fetchValues(
                MetricType.TYPING_SPEED_CHAR_PER_MIN.key,
                now - windowMs,
                now,
                null,
            )
            sleepRegValues = readingDao.fetchValues(
                MetricType.SLEEP_REGULARITY.key,
                now - windowMs,
                now,
                null,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geriatric trajectory") },
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
            ManifestoNoteCard()

            // Opt-in toggles — the owner is the gate.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Owner-declared context",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "These flags gate the trajectory advisories — Bios never infers them. Frailty / age-≥75 is set in Settings → Physiology state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = physStore.current() == PhysiologyState.FRAILTY_FLAG,
                            onCheckedChange = null,
                            enabled = false,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Frailty / age-≥75 context (Settings → Physiology state)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = fallRiskConcern,
                            onCheckedChange = {
                                fallRiskConcern = it
                                store.fallRiskConcern = it
                            },
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Fall-risk concern (surfaces the fall-trajectory advisory in this view)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = ageOver75,
                            onCheckedChange = {
                                ageOver75 = it
                                store.ageOver75 = it
                            },
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Age ≥ 75 (owner-declared)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cognitiveTracking,
                            onCheckedChange = {
                                cognitiveTracking = it
                                store.cognitiveTrackingEnabled = it
                            },
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Cognitive tracking (opt in to typing-speed capture)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Recent hospital / ED discharge",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT) }
                    val dischargeText = if (dischargeAt > 0L) {
                        "Recorded: ${sdf.format(Date(dischargeAt))} (window: ${GeriatricTrajectoryStore.DISCHARGE_WINDOW_DAYS} days)"
                    } else {
                        "Not recorded"
                    }
                    Text(
                        dischargeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                val nowMs = System.currentTimeMillis()
                                store.recentDischargeAtMillis = nowMs
                                dischargeAt = nowMs
                            },
                        ) {
                            Text("Record discharge today")
                        }
                        Spacer(Modifier.size(8.dp))
                        TextButton(
                            onClick = {
                                store.recentDischargeAtMillis = 0L
                                dischargeAt = 0L
                            },
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Trajectories.
            TrajectoryCard(
                title = "Gait-variability (stride-time CoV)",
                values = gaitCovValues,
                unitLabel = "%",
                blurb = "Stride-time coefficient of variation, derived from phone-sensor step intervals. Hausdorff (2007) and Verghese (2009) anchor the trajectory framing.",
            )

            TrajectoryCard(
                title = "Typing speed (chars/min)",
                values = typingValues,
                unitLabel = "chars/min",
                blurb = if (cognitiveTracking) {
                    "Owner-permission-gated cadence aggregate. Cogito study / Apple Cognition Score framework."
                } else {
                    "Off. Enable cognitive tracking above to record this signal."
                },
            )

            TrajectoryCard(
                title = "Sleep regularity",
                values = sleepRegValues,
                unitLabel = "score",
                blurb = "Bedtime/wake regularity score (Lim 2013 — sleep regularity and incident cognitive decline).",
            )
        }
    }
}

@Composable
private fun ManifestoNoteCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What this view is",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pull-side trajectories the owner reviews. Bios never auto-classifies fall risk, delirium, or cognitive change — those are clinical assessments. This page shows data trends with their published-framework references; clinical evaluation is what attaches meaning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrajectoryCard(
    title: String,
    values: List<Double>,
    unitLabel: String,
    blurb: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            if (values.isEmpty()) {
                Text(
                    "No readings in the last 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val recent = values.takeLast(7)
                val mean = if (recent.isNotEmpty()) recent.average() else 0.0
                val overall = values.average()
                val delta = mean - overall
                Text(
                    "Last 7 readings mean: %.2f %s".format(mean, unitLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "30-day mean: %.2f %s".format(overall, unitLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Δ vs 30-day mean: %+.2f %s".format(delta, unitLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Readings: ${values.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
