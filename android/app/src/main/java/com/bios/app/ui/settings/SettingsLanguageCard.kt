package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bios.app.R
import com.bios.app.i18n.LocalePreference

/**
 * Settings → Preferences → Language card (issue #210).
 *
 * Lets the owner override the system locale for Bios text without
 * changing the device-wide language. Respects the system default when
 * the owner has not made a selection — the override is opt-in. Persists
 * to SharedPreferences via [LocalePreference] so it survives relaunch.
 *
 * The card explains that a restart is required for the override to
 * apply across already-loaded screens — Compose doesn't re-resolve
 * Configuration mid-process. Future enhancement: wrap the activity's
 * `attachBaseContext` with [LocalePreference.wrap] so the override
 * applies on the next resume without an explicit restart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsLanguageCard() {
    val context = LocalContext.current
    var currentTag by remember { mutableStateOf(LocalePreference.currentTag(context)) }
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = LocalePreference.supported
        .firstOrNull { it.tag == currentTag }
        ?.displayName
        ?: stringResource(R.string.settings_language_system_default)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_language_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currentLabel, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { expanded = true }) {
                    Text(stringResource(R.string.settings_language_apply))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                LocalePreference.supported.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            LocalePreference.setTag(context, option.tag)
                            currentTag = option.tag
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
