package com.bios.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bios.app.data.ClinicalDirectiveRepo
import com.bios.app.data.GoalsOfCareRepo
import com.bios.app.model.ClinicalDirective
import com.bios.app.model.CprPreference
import com.bios.app.model.GoalsOfCare
import com.bios.app.model.HospitalizationPreference
import com.bios.app.model.InterventionLevel
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import kotlinx.coroutines.launch

/**
 * Settings → Identity → Goals of care (#184). Owner-declared CPR /
 * hospitalisation / intervention-level preferences + advance-directive
 * metadata + hospice-mode toggle.
 *
 * Manifesto guard: pull-side only. Nothing on this screen pushes a
 * notification or modifies what Bios *detects*. It only modifies what
 * Bios *escalates* — when the owner has set comfort-focused goals or
 * hospice mode, URGENT-tier alerts stop auto-escalating to emergency
 * contacts. Owner-revocable instantly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsOfCareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val goalsRepo = remember(context) { GoalsOfCareRepo(BiosDatabase.getInstance(context)) }
    val directiveRepo = remember(context) { ClinicalDirectiveRepo(BiosDatabase.getInstance(context)) }
    val stateStore = remember(context) { PhysiologyStateStore(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var goals by remember { mutableStateOf(GoalsOfCare()) }
    var directive by remember { mutableStateOf(ClinicalDirective()) }
    var hospiceActive by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var showHospiceConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        goals = goalsRepo.fetchOrEmpty()
        directive = directiveRepo.fetchOrEmpty()
        hospiceActive = stateStore.current() == PhysiologyState.HOSPICE_MODE
        loaded = true
    }

    fun save() {
        scope.launch {
            goalsRepo.save(goals)
            directiveRepo.save(directive)
            snackbar.showSnackbar("Saved")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals of care") },
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

            HospiceModeCard(
                active = hospiceActive,
                onRequestEnable = { showHospiceConfirm = true },
                onDisable = {
                    stateStore.set(PhysiologyState.STANDARD)
                    hospiceActive = false
                    scope.launch { snackbar.showSnackbar("Hospice mode disabled") }
                },
            )

            GoalsOfCareCard(
                goals = goals,
                onChange = { goals = it },
            )

            ClinicalDirectiveCard(
                directive = directive,
                onChange = { directive = it },
            )

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }

        if (showHospiceConfirm) {
            AlertDialog(
                onDismissRequest = { showHospiceConfirm = false },
                title = { Text("Enable hospice mode?") },
                text = {
                    Text(
                        "Bios will become quieter and stop auto-escalating " +
                            "emergencies. URGENT alerts still appear in-app, " +
                            "but no automatic call, no SMS to emergency " +
                            "contacts, and no notification sound. You can " +
                            "disable hospice mode at any time."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            stateStore.set(PhysiologyState.HOSPICE_MODE)
                            hospiceActive = true
                            showHospiceConfirm = false
                            scope.launch { snackbar.showSnackbar("Hospice mode enabled") }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        Text("Enable hospice mode")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHospiceConfirm = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun ManifestoCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Goals of care are the owner's standing preferences about " +
                    "resuscitation, hospitalisation, and the overall level " +
                    "of medical intervention. When you've declared " +
                    "comfort-focused care or activated hospice mode, Bios " +
                    "honors that declaration: URGENT alerts still appear " +
                    "in the app, but no automatic escalation fires. Nothing " +
                    "here is inferred — Bios only ever knows what you've " +
                    "told it. You can revise or revoke any choice at any " +
                    "time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HospiceModeCard(
    active: Boolean,
    onRequestEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hospice mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (active) {
                            "Active. URGENT-tier alerts will not auto-escalate."
                        } else {
                            "Inactive. Default escalation behaviour applies."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = active,
                    onCheckedChange = { wantOn ->
                        if (wantOn) onRequestEnable() else onDisable()
                    },
                )
            }
        }
    }
}

@Composable
private fun GoalsOfCareCard(
    goals: GoalsOfCare,
    onChange: (GoalsOfCare) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Goals of care", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            EnumDropdown(
                label = "CPR preference",
                options = CprPreference.entries,
                selected = goals.cprPreference,
                displayName = { it.displayName },
                onSelect = { onChange(goals.copy(cprPreference = it)) },
            )

            EnumDropdown(
                label = "Hospitalisation",
                options = HospitalizationPreference.entries,
                selected = goals.hospitalizationPreference,
                displayName = { it.displayName },
                onSelect = { onChange(goals.copy(hospitalizationPreference = it)) },
            )

            EnumDropdown(
                label = "Intervention level",
                options = InterventionLevel.entries,
                selected = goals.interventionLevel,
                displayName = { it.displayName },
                onSelect = { onChange(goals.copy(interventionLevel = it)) },
            )

            OutlinedTextField(
                value = goals.documentLocation.orEmpty(),
                onValueChange = { onChange(goals.copy(documentLocation = it.ifBlank { null })) },
                label = { Text("Document location (optional)") },
                placeholder = { Text("e.g. POLST at clinic X") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = goals.notes.orEmpty(),
                onValueChange = { onChange(goals.copy(notes = it.ifBlank { null })) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ClinicalDirectiveCard(
    directive: ClinicalDirective,
    onChange: (ClinicalDirective) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Advance directives", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Metadata only. Bios does not store the documents themselves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CheckRow(
                label = "I have an advance directive / living will",
                checked = directive.hasAdvanceDirective,
                onChange = { onChange(directive.copy(hasAdvanceDirective = it)) },
            )
            CheckRow(
                label = "I have a POLST / MOLST or equivalent portable order",
                checked = directive.hasPolst,
                onChange = { onChange(directive.copy(hasPolst = it)) },
            )
            CheckRow(
                label = "I have a designated healthcare proxy",
                checked = directive.hasHealthcareProxy,
                onChange = { onChange(directive.copy(hasHealthcareProxy = it)) },
            )

            OutlinedTextField(
                value = directive.proxyContactName.orEmpty(),
                onValueChange = { onChange(directive.copy(proxyContactName = it.ifBlank { null })) },
                label = { Text("Proxy name (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = directive.proxyContactPhone.orEmpty(),
                onValueChange = { onChange(directive.copy(proxyContactPhone = it.ifBlank { null })) },
                label = { Text("Proxy phone (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.height(0.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(displayName(selected), modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(displayName(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
