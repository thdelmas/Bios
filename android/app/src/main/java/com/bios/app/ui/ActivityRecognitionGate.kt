package com.bios.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Requests the runtime `ACTIVITY_RECOGNITION` permission once after the
 * owner has cleared Health Connect onboarding.
 *
 * Android 10+ silently delivers zero events to `TYPE_STEP_COUNTER`
 * without this permission, even when the sensor itself reports
 * `hasStepCounter = true`. Without the runtime grant the phone-side
 * step source stayed empty forever in production.
 *
 * The outcome surfaces naturally — if the owner declines, the next
 * sync's step reading stays null and the rest of the bus keeps
 * working. The launcher block is empty by design; this is a one-shot
 * passive opt-in, not a gate on app functionality.
 *
 * Extracted from MainActivity to keep that file under the 500-line cap
 * and so the runtime-perm pattern is reusable when more sensor perms
 * land (e.g. `BODY_SENSORS` if Bios ever supports phone PPG).
 */
@Composable
fun MaybeRequestActivityRecognition(hasPermissions: Boolean) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result surfaces via the next sync's step reading */ }
    LaunchedEffect(hasPermissions) {
        if (hasPermissions && ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
}
