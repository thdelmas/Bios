package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Identity card — owner-annotated medical and physiological context;
 * not configuration. Extracted from [SettingsScreen] to give "Self"
 * surfaces a dedicated landing zone. Adding a new identity entry means
 * adding a nav callback param + a row here, in one place.
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
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Identity", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Anthropometry" to onNavigateToAnthropometry,
                "Current medications" to onNavigateToMedications,
                "Immunisation record" to onNavigateToImmunisations,
                "Preventive care" to onNavigateToPreventiveCare,
                "Risk profile" to onNavigateToRiskProfile,
                "Physiology state" to onNavigateToPhysiologyState,
                "Environmental context" to onNavigateToEnvironmentalContext,
                "FRAIL assessment" to onNavigateToFrailAssessment,
                "Goals of care" to onNavigateToGoalsOfCare,
                "Headache & migraine diary" to onNavigateToHeadacheDiary,
                "FAST stroke check" to onNavigateToFastStroke,
                "Symptom report (ESAS-r)" to onNavigateToEsasCapture,
                "Traditional medicine" to onNavigateToTraditionalMedicine,
                "Emergency contacts" to onNavigateToEmergencyContacts,
                "ECG strips" to onNavigateToEcgStrips,
                "Surgical recovery" to onNavigateToSurgicalRecovery,
                "Intervention events" to onNavigateToInterventionEvents,
                "Treatment courses" to onNavigateToTreatmentCourses,
            ).forEachIndexed { idx, (label, action) ->
                if (idx > 0) Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}
