package com.bios.app.ui

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * One-shot `POST_NOTIFICATIONS` opt-in shown immediately after the owner
 * clears Health Connect onboarding.
 *
 * Without this permission every alert path on API 33+ silently no-ops
 * (`AlertManager`, `FollowUpWorker`, `DailyDigestWorker`,
 * `UrgentAckTimeoutWorker`, `PushMessageHandler`, `DisconnectNotifier`
 * all check the permission and `return` if it is missing). The previous
 * code path let Android's first-post auto-prompt show with no context, and
 * an owner who declined had no way back — alerts would simply never
 * arrive. This gate shows a manifesto-aligned rationale first ("Bios
 * speaks when something matters") so the OS prompt that follows is
 * contextualised.
 *
 * The rationale dialog is shown at most once per install (tracked via
 * SharedPreferences). If the owner declines, [SettingsNotificationsCard]
 * surfaces a persistent banner with a re-request action so the decision
 * can be revisited from the Self tab.
 *
 * No-op on API < 33 (the permission is install-time granted on older
 * Android versions).
 */
@Composable
fun MaybeRequestPostNotifications(hasPermissions: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Decline path surfaces in SettingsNotificationsCard as a banner. */ }

    LaunchedEffect(hasPermissions) {
        if (!hasPermissions) return@LaunchedEffect
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) return@LaunchedEffect
        if (NotificationPermissionPrefs.rationaleShown(context)) return@LaunchedEffect
        showRationale = true
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                NotificationPermissionPrefs.markRationaleShown(context)
            },
            title = { Text("Let Bios speak when it matters") },
            text = {
                Text(
                    "Bios stays silent until something deviates from your baseline " +
                        "or a sensor drops out. Notifications are how it reaches you " +
                        "when the app isn't open.\n\n" +
                        "Without this, alerts only appear inside the app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    NotificationPermissionPrefs.markRationaleShown(context)
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    NotificationPermissionPrefs.markRationaleShown(context)
                }) { Text("Not now") }
            },
        )
    }
}

/** Backing store for the "rationale already shown" flag. Lives in a dedicated
 *  SharedPreferences file so it doesn't collide with feature-toggle prefs. */
internal object NotificationPermissionPrefs {
    private const val PREFS_NAME = "bios_notification_perm"
    private const val KEY_RATIONALE_SHOWN = "rationale_shown_v1"

    fun rationaleShown(context: Context): Boolean = prefs(context).getBoolean(KEY_RATIONALE_SHOWN, false)

    fun markRationaleShown(context: Context) {
        prefs(context).edit().putBoolean(KEY_RATIONALE_SHOWN, true).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
