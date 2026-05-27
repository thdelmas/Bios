package com.bios.app.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bios.app.alerts.DailyDigestWorker
import com.bios.app.push.PushRegistrationManager

/**
 * Settings → Notifications card. Three toggles: daily digest, disconnect
 * alerts, and UnifiedPush registration. Extracted from [SettingsScreen]
 * so the host file stays under the 500-line cap as more data-source rows
 * (WHOOP, future Garmin) land in the data-sources block above.
 */
@Composable
internal fun SettingsNotificationsCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            NotificationsPermissionBanner()

            var digestEnabled by remember { mutableStateOf(DailyDigestWorker.isEnabled(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Digest", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Morning summary of your vitals at 8 AM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = digestEnabled,
                    onCheckedChange = {
                        digestEnabled = it
                        DailyDigestWorker.setEnabled(context, it)
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DisconnectAlertToggle()
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            var pushEnabled by remember { mutableStateOf(PushRegistrationManager.isEnabled(context)) }
            var pushDistributor by remember { mutableStateOf(PushRegistrationManager.getDistributorName(context)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Push Notifications", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (pushEnabled && pushDistributor != null)
                            "Via $pushDistributor — no Google required"
                        else
                            "Receive population health signals without polling. Requires a UnifiedPush distributor (e.g. ntfy).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pushEnabled,
                    onCheckedChange = { enabled ->
                        pushEnabled = enabled
                        if (enabled) {
                            PushRegistrationManager.setEnabled(context, true)
                            PushRegistrationManager.register(context)
                        } else {
                            PushRegistrationManager.unregister(context)
                        }
                        pushDistributor = PushRegistrationManager.getDistributorName(context)
                    }
                )
            }
        }
    }
}

/**
 * Banner shown at the top of the Notifications card when
 * `POST_NOTIFICATIONS` is missing — without it the alert pipeline silently
 * no-ops on API 33+ and the toggles below appear active while delivering
 * nothing. Surfaced here (rather than left as the OS-managed pref) so the
 * owner discovers the gap inside Bios when they come looking for alert
 * controls.
 *
 * Recomposes when the host lifecycle resumes, so a permission granted via
 * the system settings deep-link clears the banner without restarting the
 * app. No-op on API < 33 where the permission is install-time granted.
 */
@Composable
private fun NotificationsPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    if (granted) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Notifications are off — alerts won't reach you outside Bios.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                // Direct OS prompt first; if Android remembered a "Don't ask
                // again" the launcher returns immediately with false and we
                // bounce to the system settings page where the owner can
                // still flip the switch.
                if (NotificationPermissionLastResult.shouldDeepLinkInstead(context)) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) {
                Text("Turn on notifications")
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    LaunchedEffect(granted) {
        NotificationPermissionLastResult.markAttempt(context, granted)
    }
}

/** Tracks whether the last in-app POST_NOTIFICATIONS request was denied so
 *  the banner button knows to deep-link to system settings rather than
 *  launching a contract that the OS will silently no-op. */
private object NotificationPermissionLastResult {
    private const val PREFS = "bios_notification_perm_attempt"
    private const val KEY_LAST_DENIED = "last_denied_v1"

    fun markAttempt(context: android.content.Context, granted: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LAST_DENIED, !granted).apply()
    }

    fun shouldDeepLinkInstead(context: android.content.Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_LAST_DENIED, false)
}
