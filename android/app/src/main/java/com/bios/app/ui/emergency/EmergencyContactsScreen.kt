package com.bios.app.ui.emergency

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bios.app.data.BiosDatabase
import com.bios.app.data.EmergencyContactRepo
import com.bios.app.model.EmergencyContact
import kotlinx.coroutines.launch

/**
 * Settings → Identity → Emergency contacts (#179, audit gap EM/CC §2.1).
 *
 * Owner-set, owner-revocable, off by default. No row = no escalation.
 *
 * Each contact carries a per-tier opt-in (ADVISORY-or-URGENT vs.
 * URGENT-only) and an optional acknowledgement timeout in minutes. When
 * the timeout is null, the row is informational only — Bios will not
 * surface this contact for an unacknowledged alert. When non-null, the
 * timeout is the window Bios waits before composing a local SMS draft;
 * the system SMS chooser then hands the send decision back to the owner.
 *
 * Sibling of the existing Identity surfaces (Current medications,
 * Immunisation record, Preventive care, Risk profile, Physiology state).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) {
        EmergencyContactRepo(BiosDatabase.getInstance(context))
    }
    val scope = rememberCoroutineScope()

    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmergencyContact?>(null) }

    suspend fun refresh() {
        contacts = repo.fetchAll()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency contacts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emergency contacts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "If you'd like Bios to surface a contact when an urgent alert goes " +
                            "unacknowledged, name them here. Bios never sends a message on its " +
                            "own — your phone's text app opens with a draft, and you (or " +
                            "someone with your phone) confirm before it sends.\n\n" +
                            "Off until you add a contact. Each contact opts in to a minimum " +
                            "alert tier and a wait time. Leaving the wait time blank keeps " +
                            "this contact on record without ever escalating.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add contact")
                    }
                }
            }

            if (contacts.isEmpty()) {
                Text(
                    "No emergency contacts on record. Tap \"Add contact\" to designate one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                contacts.forEach { c ->
                    ContactRow(
                        contact = c,
                        onEdit = { editing = c },
                        onDelete = {
                            scope.launch {
                                repo.remove(c.id)
                                refresh()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        ContactDialog(
            initial = null,
            title = "Add contact",
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                scope.launch {
                    repo.add(
                        name = draft.name,
                        relationship = draft.relationship,
                        phoneNumber = draft.phoneNumber,
                        minTierLevel = draft.minTierLevel,
                        acknowledgementTimeoutMinutes = draft.acknowledgementTimeoutMinutes,
                        note = draft.note,
                    )
                    refresh()
                }
                showAddDialog = false
            }
        )
    }

    editing?.let { current ->
        ContactDialog(
            initial = current,
            title = "Edit contact",
            onDismiss = { editing = null },
            onSave = { draft ->
                scope.launch {
                    repo.update(current.copy(
                        name = draft.name,
                        relationship = draft.relationship,
                        phoneNumber = draft.phoneNumber,
                        minTierLevel = draft.minTierLevel,
                        acknowledgementTimeoutMinutes = draft.acknowledgementTimeoutMinutes,
                        note = draft.note,
                    ))
                    refresh()
                }
                editing = null
            }
        )
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    contact.relationship,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(
                        if (contact.minTierLevel <= EmergencyContact.MIN_TIER_ADVISORY) {
                            "Advisory + Urgent"
                        } else {
                            "Urgent only"
                        }
                    )
                    val t = contact.acknowledgementTimeoutMinutes
                    if (t != null) append(" · escalates after ${t}m unacknowledged")
                    else append(" · never escalates")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            contact.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Edit") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

internal data class ContactDraft(
    val name: String,
    val relationship: String,
    val phoneNumber: String,
    val minTierLevel: Int,
    val acknowledgementTimeoutMinutes: Int?,
    val note: String?,
)

@Composable
private fun ContactDialog(
    initial: EmergencyContact?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (ContactDraft) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var relationship by remember { mutableStateOf(initial?.relationship.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phoneNumber.orEmpty()) }
    var advisoryOptIn by remember {
        mutableStateOf((initial?.minTierLevel ?: EmergencyContact.MIN_TIER_URGENT) <= EmergencyContact.MIN_TIER_ADVISORY)
    }
    var timeoutText by remember {
        mutableStateOf(initial?.acknowledgementTimeoutMinutes?.toString().orEmpty())
    }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it.take(40) },
                    label = { Text("Relationship") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(32) },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tier opt-in", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !advisoryOptIn,
                        onClick = { advisoryOptIn = false },
                        label = { Text("Urgent only") },
                    )
                    FilterChip(
                        selected = advisoryOptIn,
                        onClick = { advisoryOptIn = true },
                        label = { Text("Advisory + Urgent") },
                    )
                }
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { v ->
                        timeoutText = v.filter { it.isDigit() }.take(3)
                    },
                    label = { Text("Escalate after (minutes, blank = never)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && relationship.isNotBlank() && phone.isNotBlank(),
                onClick = {
                    onSave(
                        ContactDraft(
                            name = name.trim(),
                            relationship = relationship.trim(),
                            phoneNumber = phone.trim(),
                            minTierLevel = if (advisoryOptIn) {
                                EmergencyContact.MIN_TIER_ADVISORY
                            } else {
                                EmergencyContact.MIN_TIER_URGENT
                            },
                            acknowledgementTimeoutMinutes = timeoutText.toIntOrNull(),
                            note = note.trim().takeIf { it.isNotBlank() },
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
