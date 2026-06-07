package com.bios.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App settings — device/app configuration, kept separate from the "Self"
 * tab so that owner-identity and privacy surfaces aren't buried under
 * plumbing. These cards are pure app config (notifications, language,
 * feedback, on-device feature toggles, diagnostics, about) and share no
 * state with [SettingsScreen]; the Self tab links here with a single row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit = {},
    onViewSeizureDetections: () -> Unit = {},
    onLongPressVersion: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsNotificationsCard()
            SettingsLanguageCard()
            SettingsFeedbackCard()
            SettingsPhoneSleepCard()
            SettingsSeizureDetectionCard(onViewDetections = onViewSeizureDetections)
            SettingsDiagnosticsCard()
            // About — long-press the Version row opens the metric_readings debug screen (#253).
            SettingsAboutCard(onLongPressVersion = onLongPressVersion)
        }
    }
}
