package com.bios.app.ui.screening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.screening.AnatomyProfile
import java.util.Calendar

/**
 * Demographics + anatomy editor for [PreventiveCareScreen]. Split into its
 * own file so the screen stays under the 500-line cap. Same package, so
 * these stay `internal` rather than `public`.
 *
 * The anatomy-direct flow (#209) asks per-organ rather than a binary
 * sex/gender question — each screening pins to an organ, so the owner
 * answers what applies and leaves the rest unset.
 */
@Composable
internal fun DemographicsDialog(
    initialBirthYear: Int?,
    initialAnatomy: AnatomyProfile,
    onDismiss: () -> Unit,
    onSave: (Int?, AnatomyProfile) -> Unit,
) {
    var yearText by remember { mutableStateOf(initialBirthYear?.toString() ?: "") }
    var anatomy by remember { mutableStateOf(initialAnatomy) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demographics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { v -> yearText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Birth year (e.g. 1985)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Anatomy", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text(
                    "Bios asks about anatomy, not gender. Each screening " +
                        "recommendation pins to an organ: mammograms to breast " +
                        "tissue, cervical screening to a cervix, PSA discussion " +
                        "to a prostate. Answer what applies; leave the rest " +
                        "unset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnatomyQuestion(
                    label = "Do you have breast tissue?",
                    answer = anatomy.hasBreastTissue,
                    onAnswer = { anatomy = anatomy.copy(hasBreastTissue = it) },
                )
                AnatomyQuestion(
                    label = "Do you have a cervix?",
                    answer = anatomy.hasCervix,
                    onAnswer = { anatomy = anatomy.copy(hasCervix = it) },
                )
                AnatomyQuestion(
                    label = "Do you have a uterus?",
                    answer = anatomy.hasUterus,
                    onAnswer = { anatomy = anatomy.copy(hasUterus = it) },
                )
                AnatomyQuestion(
                    label = "Do you have a prostate?",
                    answer = anatomy.hasProstate,
                    onAnswer = { anatomy = anatomy.copy(hasProstate = it) },
                )
                AnatomyQuestion(
                    label = "Have you had bottom or top surgery?",
                    answer = anatomy.hadGenderAffirmingSurgery,
                    onAnswer = { anatomy = anatomy.copy(hadGenderAffirmingSurgery = it) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    yearText.toIntOrNull()?.takeIf {
                        it in 1900..Calendar.getInstance().get(Calendar.YEAR)
                    },
                    anatomy,
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AnatomyQuestion(
    label: String,
    answer: Boolean?,
    onAnswer: (Boolean?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnatomyChip("Yes", answer == true) { onAnswer(true) }
            AnatomyChip("No", answer == false) { onAnswer(false) }
            AnatomyChip("Unset", answer == null) { onAnswer(null) }
        }
    }
}

@Composable
private fun AnatomyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else ButtonDefaults.outlinedButtonColors(),
    ) { Text(label) }
}
