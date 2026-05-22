package com.bios.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.bios.app.alerts.DisconnectNotifier
import com.bios.app.provider.CompanionAccessNotifier

/**
 * Cross-process deep links delivered via the launching Intent:
 *  - CompanionAccessNotifier → Settings → Companion Apps
 *  - DisconnectNotifier → Data Coverage (so the owner can reconnect a
 *    failing source)
 *  - `bios://capture/ppg` → PPG capture screen. popUpTo home/inclusive
 *    empties the in-app back stack so pressing back from capture
 *    finishes the activity and returns to the calling companion (e.g.
 *    W2F "take a snapshot" CTA), not Bios Home.
 *
 * Each extra is consumed so it doesn't refire on configuration change.
 */
@Composable
fun HandleDeepLinks(navController: NavController) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val intent = activity.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(
                CompanionAccessNotifier.EXTRA_NAVIGATE_TO_COMPANIONS,
                false
            )
        ) {
            intent.removeExtra(CompanionAccessNotifier.EXTRA_NAVIGATE_TO_COMPANIONS)
            navController.navigate("companions")
        }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val intent = activity.intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(
                DisconnectNotifier.EXTRA_NAVIGATE_TO_DATA_COVERAGE,
                false
            )
        ) {
            intent.removeExtra(DisconnectNotifier.EXTRA_NAVIGATE_TO_DATA_COVERAGE)
            navController.navigate("data_coverage")
        }
    }

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val data = activity.intent?.data ?: return@LaunchedEffect
        if (data.scheme == "bios" && data.host == "capture" && data.path == "/ppg") {
            activity.intent.data = null
            navController.navigate("ppg_capture") {
                popUpTo("home") { inclusive = true }
            }
        }
    }
}
