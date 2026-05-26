package com.bios.app.ui.risk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.RiskProfileRepo
import com.bios.app.model.RiskProfile
import kotlinx.coroutines.launch

/**
 * Settings → Risk profile. Closes audit gap §2.6.
 *
 * Pull-side surface for family-history + personal-history risk factors.
 * Manifesto guard: nothing pushed; owner edits when they choose. The
 * screening-cadence engine (#155) and pattern-explanation builder will
 * read this in follow-up wire-up PRs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { RiskProfileRepo(BiosDatabase.getInstance(context)) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var profile by remember { mutableStateOf(RiskProfile()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profile = repo.fetchOrEmpty()
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risk profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ManifestoCard()

            FamilyHistoryCard(
                profile = profile,
                onChange = { profile = it },
            )

            HereditarySyndromeCard(
                profile = profile,
                onChange = { profile = it },
            )

            RegionalRiskCard(
                profile = profile,
                onChange = { profile = it },
            )

            TobaccoCard(
                profile = profile,
                onChange = { profile = it },
            )

            PersonalHistoryCard(
                profile = profile,
                onChange = { profile = it },
            )

            Button(
                onClick = {
                    scope.launch {
                        repo.save(profile)
                        snackbar.showSnackbar("Risk profile saved")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How Bios uses this", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Calibrated primary-care risk scores (ASCVD, FRAX, Gail) and the " +
                    "USPSTF screening schedule both take family history as input. " +
                    "Recording these factors lets Bios surface earlier preventive-care " +
                    "cadences when they apply. Nothing here triggers a notification — " +
                    "the screen waits until you ask.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FamilyHistoryCard(profile: RiskProfile, onChange: (RiskProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Family history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CheckRow(
                label = "First-degree relative with early heart disease (♂ <55 / ♀ <65)",
                checked = profile.firstDegreeCadEarly,
                onChange = { onChange(profile.copy(firstDegreeCadEarly = it)) },
            )
            CheckRow(
                label = "First-degree breast or ovarian cancer",
                checked = profile.firstDegreeBreastOvarianCancer,
                onChange = { onChange(profile.copy(firstDegreeBreastOvarianCancer = it)) },
            )
            CheckRow(
                label = "First-degree colorectal cancer",
                checked = profile.firstDegreeColorectalCancer,
                onChange = { onChange(profile.copy(firstDegreeColorectalCancer = it)) },
            )
            CheckRow(
                label = "First-degree type 2 diabetes",
                checked = profile.firstDegreeDiabetes,
                onChange = { onChange(profile.copy(firstDegreeDiabetes = it)) },
            )
            CheckRow(
                label = "First-degree osteoporosis or hip fracture",
                checked = profile.firstDegreeOsteoporosisHipFracture,
                onChange = { onChange(profile.copy(firstDegreeOsteoporosisHipFracture = it)) },
            )
            CheckRow(
                label = "First-degree melanoma",
                checked = profile.firstDegreeMelanoma,
                onChange = { onChange(profile.copy(firstDegreeMelanoma = it)) },
            )
        }
    }
}

@Composable
private fun HereditarySyndromeCard(profile: RiskProfile, onChange: (RiskProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hereditary syndromes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Tick what genetics testing or a clinician has confirmed. " +
                    "These flags unlock NCCN surveillance cadences in Preventive Care.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckRow(
                label = "Lynch syndrome (HNPCC)",
                checked = profile.lynchSyndrome,
                onChange = { onChange(profile.copy(lynchSyndrome = it)) },
            )
            CheckRow(
                label = "Li-Fraumeni syndrome",
                checked = profile.liFraumeni,
                onChange = { onChange(profile.copy(liFraumeni = it)) },
            )
            CheckRow(
                label = "Familial adenomatous polyposis (FAP)",
                checked = profile.fapFamilialAdenomatousPolyposis,
                onChange = { onChange(profile.copy(fapFamilialAdenomatousPolyposis = it)) },
            )
            CheckRow(
                label = "Cowden syndrome (PTEN)",
                checked = profile.cowdenSyndrome,
                onChange = { onChange(profile.copy(cowdenSyndrome = it)) },
            )
            CheckRow(
                label = "Peutz-Jeghers syndrome",
                checked = profile.peutzJeghersSyndrome,
                onChange = { onChange(profile.copy(peutzJeghersSyndrome = it)) },
            )
            CheckRow(
                label = "von Hippel-Lindau",
                checked = profile.vhlVonHippelLindau,
                onChange = { onChange(profile.copy(vhlVonHippelLindau = it)) },
            )
            CheckRow(
                label = "MEN1",
                checked = profile.men1,
                onChange = { onChange(profile.copy(men1 = it)) },
            )
            CheckRow(
                label = "MEN2A",
                checked = profile.men2A,
                onChange = { onChange(profile.copy(men2A = it)) },
            )
            CheckRow(
                label = "MEN2B",
                checked = profile.men2B,
                onChange = { onChange(profile.copy(men2B = it)) },
            )
            CheckRow(
                label = "Hereditary diffuse gastric cancer (CDH1+)",
                checked = profile.hdgcHereditaryDiffuseGastric,
                onChange = { onChange(profile.copy(hdgcHereditaryDiffuseGastric = it)) },
            )
        }
    }
}

@Composable
private fun RegionalRiskCard(profile: RiskProfile, onChange: (RiskProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Regional context", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Bios never infers ethnicity. Self-declared here so the cadence " +
                    "engine can surface IHS Diabetes Standards (HbA1c every 6 mo) " +
                    "when it applies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CheckRow(
                label = "Diabetic, American Indian / Alaska Native (IHS Standards)",
                checked = profile.ihsDiabeticAianStatus,
                onChange = { onChange(profile.copy(ihsDiabeticAianStatus = it)) },
            )
        }
    }
}

@Composable
private fun TobaccoCard(profile: RiskProfile, onChange: (RiskProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tobacco history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = profile.personalTobaccoYears?.toString() ?: "",
                onValueChange = { v -> onChange(profile.copy(personalTobaccoYears = v.filter { it.isDigit() }.toIntOrNull())) },
                label = { Text("Years smoked (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = profile.personalTobaccoPackYears?.toString() ?: "",
                onValueChange = { v -> onChange(profile.copy(personalTobaccoPackYears = v.filter { it.isDigit() }.toIntOrNull())) },
                label = { Text("Pack-years (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Pack-years input drives the USPSTF lung-cancer-screening cadence " +
                    "(annual low-dose CT 50–80 with ≥20 pack-years) once the cadence " +
                    "engine wires it up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PersonalHistoryCard(profile: RiskProfile, onChange: (RiskProfile) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Personal history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = profile.personalCancerHistory.orEmpty(),
                onValueChange = { onChange(profile.copy(personalCancerHistory = it.takeIf { s -> s.isNotBlank() })) },
                label = { Text("Cancer history (free text)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = profile.personalCardiacEventHistory.orEmpty(),
                onValueChange = { onChange(profile.copy(personalCardiacEventHistory = it.takeIf { s -> s.isNotBlank() })) },
                label = { Text("Cardiac event history (free text)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = profile.note.orEmpty(),
                onValueChange = { onChange(profile.copy(note = it.takeIf { s -> s.isNotBlank() })) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
