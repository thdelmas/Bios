package com.bios.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Identity card — owner-annotated medical and physiological context;
 * not configuration. Extracted from [SettingsScreen] to give "Self"
 * surfaces a dedicated landing zone. Adding a new identity entry means
 * adding a nav callback param + a row in the matching [IdentitySection]
 * group below, in one place.
 *
 * Entries are organised into collapsible groups so the card reads as a
 * handful of scannable headers rather than a wall of buttons. All groups
 * are collapsed by default; the owner expands the area they're looking for.
 *
 * Each `onNavigateTo*` callback is a no-op by default so new entries
 * can be wired progressively without breaking the existing call sites.
 */
@Composable
internal fun IdentityCard(
    onNavigateToMedications: () -> Unit = {},
    onNavigateToImmunisations: () -> Unit = {},
    onNavigateToPreventiveCare: () -> Unit = {},
    onNavigateToRiskProfile: () -> Unit = {},
    onNavigateToPhysiologyState: () -> Unit = {},
    onNavigateToFrailAssessment: () -> Unit = {},
    onNavigateToGoalsOfCare: () -> Unit = {},
    onNavigateToHeadacheDiary: () -> Unit = {},
    onNavigateToFastStroke: () -> Unit = {},
    onNavigateToEsasCapture: () -> Unit = {},
    onNavigateToTraditionalMedicine: () -> Unit = {},
    onNavigateToEnvironmentalContext: () -> Unit = {},
    onNavigateToEmergencyContacts: () -> Unit = {},
    onNavigateToEcgStrips: () -> Unit = {},
    onNavigateToSurgicalRecovery: () -> Unit = {},
    onNavigateToInterventionEvents: () -> Unit = {},
    onNavigateToTreatmentCourses: () -> Unit = {},
    onNavigateToAnthropometry: () -> Unit = {},
    onNavigateToGeriatricTrajectory: () -> Unit = {},
    onNavigateToTremorTrend: () -> Unit = {},
    onNavigateToReproductive: (route: String) -> Unit = {},
) {
    val sections = listOf(
        "Body & baseline" to listOf(
            "Anthropometry" to onNavigateToAnthropometry,
            "Physiology state" to onNavigateToPhysiologyState,
            "Environmental context" to onNavigateToEnvironmentalContext,
        ),
        "Medical record" to listOf(
            "Current medications" to onNavigateToMedications,
            "Immunisation record" to onNavigateToImmunisations,
            "Treatment courses" to onNavigateToTreatmentCourses,
            "Intervention events" to onNavigateToInterventionEvents,
            "Surgical recovery" to onNavigateToSurgicalRecovery,
            "ECG strips" to onNavigateToEcgStrips,
        ),
        "Prevention & risk" to listOf(
            "Preventive care" to onNavigateToPreventiveCare,
            "Risk profile" to onNavigateToRiskProfile,
            "FRAIL assessment" to onNavigateToFrailAssessment,
            "Geriatric trajectory" to onNavigateToGeriatricTrajectory,
        ),
        "Symptoms & assessments" to listOf(
            "Headache & migraine diary" to onNavigateToHeadacheDiary,
            "Tremor / movement trends" to onNavigateToTremorTrend,
            "FAST stroke check" to onNavigateToFastStroke,
            "Symptom report (ESAS-r)" to onNavigateToEsasCapture,
        ),
        "Reproductive" to listOf(
            "Contraception" to { onNavigateToReproductive("contraception") },
            "Menopause / perimenopause" to { onNavigateToReproductive("menopause_stage") },
            "Gender-affirming care" to { onNavigateToReproductive("gender_affirming_care") },
            "PCOS / Endometriosis context" to { onNavigateToReproductive("pcos_endometriosis") },
        ),
        "Care & contacts" to listOf(
            "Goals of care" to onNavigateToGoalsOfCare,
            "Emergency contacts" to onNavigateToEmergencyContacts,
            "Traditional medicine" to onNavigateToTraditionalMedicine,
        ),
    )

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Identity", style = MaterialTheme.typography.titleSmall)
            sections.forEach { (title, entries) ->
                Spacer(Modifier.height(8.dp))
                IdentitySection(title = title, entries = entries)
            }
        }
    }
}

/**
 * A collapsible group of identity entries. Collapsed by default; the
 * header toggles visibility of the contained rows.
 */
@Composable
private fun IdentitySection(
    title: String,
    entries: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse $title" else "Expand $title",
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column {
            entries.forEachIndexed { idx, (label, action) ->
                if (idx > 0) Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}
