package com.bios.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.model.HealthEventType
import com.bios.app.ui.AppViewModel
import com.bios.app.ui.home.HomeEntryCard

/**
 * Single home for owner-as-source entry surfaces. Bios hosts entry only
 * for keys with no other producer available (per
 * docs/SELF_REPORTED_DATA_HOME.md decision 6); the surfaces here meet
 * that gate.
 */
@Composable
fun LogScreen(
    viewModel: AppViewModel,
    onNavigateToPpgCapture: () -> Unit,
    onNavigateToBiomarkerEntry: () -> Unit,
    onNavigateToClinicalEntry: () -> Unit,
    onNavigateToSleepEntry: () -> Unit,
    onNavigateToBbtEntry: () -> Unit,
    onNavigateToPeriodEntry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Log",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            "How are you feeling?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QuickSymptomCard(onLogSymptom = { title ->
            viewModel.createHealthEvent(
                type = HealthEventType.SYMPTOM,
                title = title,
                description = null,
            )
        })

        Spacer(Modifier.height(4.dp))
        Text(
            "Take a reading",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HomeEntryCard(
            icon = Icons.Default.CameraAlt,
            title = "HRV Snapshot",
            subtitle = "Fingertip reading via camera — 60 seconds",
            onClick = onNavigateToPpgCapture,
        )
        HomeEntryCard(
            icon = Icons.Default.MedicalServices,
            title = "Clinical reading",
            subtitle = "Blood pressure, SpO2, temperature",
            onClick = onNavigateToClinicalEntry,
        )
        HomeEntryCard(
            icon = Icons.Default.Science,
            title = "Lab values",
            subtitle = "Biomarker panels from blood work",
            onClick = onNavigateToBiomarkerEntry,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "Track over time",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HomeEntryCard(
            icon = Icons.Default.Bedtime,
            title = "Sleep",
            subtitle = "Manual sleep duration when no wearable is present",
            onClick = onNavigateToSleepEntry,
        )
        HomeEntryCard(
            icon = Icons.Default.Thermostat,
            title = "Basal body temperature",
            subtitle = "First-thing-in-the-morning reading",
            onClick = onNavigateToBbtEntry,
        )
        HomeEntryCard(
            icon = Icons.Default.CalendarMonth,
            title = "Period",
            subtitle = "Cycle start",
            onClick = onNavigateToPeriodEntry,
        )
    }
}

@Composable
fun QuickSymptomCard(onLogSymptom: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "How are you feeling?",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            if (submitted) {
                Text(
                    "Logged! Check your Journal for details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text("e.g., metallic taste, headache...")
                        },
                    )
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onLogSymptom(text.trim())
                                text = ""
                                submitted = true
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Log symptom",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
