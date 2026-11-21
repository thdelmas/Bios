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
 * The owner's records, by route. Adding an entry means adding a
 * `label to "route"` row in the matching group below; every entry
 * navigates through [onNavigate].
 *
 * Entries are organised into collapsible groups, all collapsed by
 * default; the owner expands the area they're looking for.
 */
@Composable
internal fun IdentityCard(onNavigate: (route: String) -> Unit = {}) {
    val sections = listOf(
        "Body measurements" to listOf(
            "Height, weight & composition" to "anthropometry",
            "ECG strips" to "ecg_strips",
        ),
        "Records" to listOf(
            "Medications" to "medications",
            "Immunisation record" to "immunisations",
            "Treatment courses" to "treatment_courses",
            "Intervention events" to "intervention_events",
            "Traditional practices" to "traditional_medicine_context",
        ),
        "Diaries" to listOf(
            "Headache diary" to "headache_diary",
        ),
        "Cycle" to listOf(
            "Cycle dashboard" to "cycle_dashboard",
            "Contraception" to "contraception",
        ),
    )

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your records", style = MaterialTheme.typography.titleSmall)
            sections.forEach { (title, entries) ->
                Spacer(Modifier.height(8.dp))
                IdentitySection(title = title, entries = entries, onNavigate = onNavigate)
            }
        }
    }
}

/**
 * A collapsible group of record entries. Collapsed by default; the
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
                OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }
        }
    }
}
