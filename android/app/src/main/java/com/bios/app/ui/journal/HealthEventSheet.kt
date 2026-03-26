package com.bios.app.ui.journal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.HealthEventType

data class HealthEventInput(
    val type: HealthEventType,
    val title: String,
    val description: String?,
    val parentEventId: String? = null,
    val initialActionItems: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthEventSheet(
    onDismiss: () -> Unit,
    onSave: (HealthEventInput) -> Unit,
    parentEventId: String? = null,
    defaultType: HealthEventType? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        HealthEventForm(
            onSave = onSave,
            parentEventId = parentEventId,
            defaultType = defaultType
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthEventForm(
    onSave: (HealthEventInput) -> Unit,
    parentEventId: String?,
    defaultType: HealthEventType?
) {
    var selectedType by remember { mutableStateOf(defaultType ?: HealthEventType.SYMPTOM) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var actionItemTexts by remember { mutableStateOf(listOf<String>()) }
    var newActionText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (parentEventId != null) "Add Follow-up" else "Log Health Event",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Type selector
        Text(
            "Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HealthEventType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.label) }
                )
            }
        }

        // Title
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(titleHint(selectedType)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Details (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        // Action items section (for Treatment type or any type)
        if (selectedType == HealthEventType.TREATMENT || actionItemTexts.isNotEmpty()) {
            Text(
                "Action Items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionItemTexts.forEachIndexed { index, text ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { newText ->
                            actionItemTexts = actionItemTexts.toMutableList().also { it[index] = newText }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        actionItemTexts = actionItemTexts.toMutableList().also { it.removeAt(index) }
                    }) {
                        Text("Remove")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newActionText,
                    onValueChange = { newActionText = it },
                    placeholder = { Text("e.g., Buy mouthwash, Dentist on Friday...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        if (newActionText.isNotBlank()) {
                            actionItemTexts = actionItemTexts + newActionText.trim()
                            newActionText = ""
                        }
                    },
                    enabled = newActionText.isNotBlank()
                ) {
                    Text("Add")
                }
            }
        }

        // Save button
        Button(
            onClick = {
                onSave(
                    HealthEventInput(
                        type = selectedType,
                        title = title.trim(),
                        description = description.ifBlank { null },
                        parentEventId = parentEventId,
                        initialActionItems = actionItemTexts.filter { it.isNotBlank() }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = title.isNotBlank()
        ) {
            Text("Save")
        }
    }
}

private fun titleHint(type: HealthEventType): String {
    return when (type) {
        HealthEventType.SYMPTOM -> "What are you experiencing?"
        HealthEventType.HYPOTHESIS -> "What do you think it might be?"
        HealthEventType.DOCTOR_VISIT -> "Doctor / clinic visited"
        HealthEventType.DIAGNOSIS -> "What was diagnosed?"
        HealthEventType.TREATMENT -> "Treatment plan"
        HealthEventType.NOTE -> "Note title"
    }
}
