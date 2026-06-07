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
 * adding a `label to "route"` row in the matching group below, in one
 * place — every entry navigates by route through [onNavigate].
 *
 * Entries are organised into collapsible groups so the card reads as a
 * handful of scannable headers rather than a wall of buttons. All groups
 * are collapsed by default; the owner expands the area they're looking for.
 */
@Composable
internal fun IdentityCard(
    onNavigate: (route: String) -> Unit = {},
) {
    val sections = listOf(
        "Body & baseline" to listOf(
            "Anthropometry" to "anthropometry",
            "Physiology state" to "physiology_state",
            "Environmental context" to "environmental_context",
        ),
        "Medical record" to listOf(
            "Current medications" to "medications",
            "Immunisation record" to "immunisations",
            "Treatment courses" to "treatment_courses",
            "Intervention events" to "intervention_events",
            "Surgical recovery" to "surgical_recovery",
            "ECG strips" to "ecg_strips",
        ),
        "Prevention & risk" to listOf(
            "Preventive care" to "preventive_care",
            "Risk profile" to "risk_profile",
            "FRAIL assessment" to "frail_assessment",
            "Geriatric trajectory" to "geriatric_trajectory",
        ),
        "Symptoms & assessments" to listOf(
            "Headache & migraine diary" to "headache_diary",
            "Tremor / movement trends" to "tremor_trend",
            "FAST stroke check" to "fast_stroke",
            "Symptom report (ESAS-r)" to "esas_capture",
        ),
        "Reproductive" to listOf(
            "Contraception" to "contraception",
            "Menopause / perimenopause" to "menopause_stage",
            "Gender-affirming care" to "gender_affirming_care",
            "PCOS / Endometriosis context" to "pcos_endometriosis",
        ),
        "Care & contacts" to listOf(
            "Goals of care" to "goals_of_care",
            "Emergency contacts" to "emergency_contacts",
            "Traditional medicine" to "traditional_medicine_context",
        ),
    )

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Identity", style = MaterialTheme.typography.titleSmall)
            sections.forEach { (title, entries) ->
                Spacer(Modifier.height(8.dp))
                IdentitySection(title = title, entries = entries, onNavigate = onNavigate)
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
    entries: List<Pair<String, String>>,
    onNavigate: (route: String) -> Unit,
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
            entries.forEachIndexed { idx, (label, route) ->
                if (idx > 0) Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}
