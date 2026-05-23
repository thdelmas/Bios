package com.bios.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Charge edge-trigger for [PhoneSleepCollectionService] (#241 Cut 1).
 *
 * The receiver listens for the OS-broadcast power events:
 *
 * - `ACTION_POWER_CONNECTED` — start collection iff
 *   [PhoneSleepQuietHours.isInQuietHoursNow] is true. Daytime charging
 *   (commute, desk, café power bank) is not sleep and never triggers
 *   the service.
 * - `ACTION_POWER_DISCONNECTED` — stop collection unconditionally. The
 *   morning unplug is the canonical end-of-night signal for phone-only
 *   inference.
 *
 * Both power actions are protected broadcasts: the receiver cannot be
 * targeted by other apps and the system delivers them with at-least-once
 * semantics. The receiver is declared `android:exported="false"` —
 * implicit broadcasts from the OS still reach it, but no app can
 * `am broadcast` it.
 *
 * No quiet-hours UI gate yet — the defaults (21:00–08:00 local) are
 * baked in; a Settings override is a follow-up cut alongside the
 * battery-optimisation onboarding ask.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val serviceIntent = Intent(context, PhoneSleepCollectionService::class.java)
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (!PhoneSleepQuietHours.isInQuietHoursNow()) {
                    Log.i(TAG, "power connected outside quiet-hours; not starting service")
                    return
                }
                Log.i(TAG, "power connected during quiet-hours; starting collection service")
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i(TAG, "power disconnected; stopping collection service")
                context.stopService(serviceIntent)
            }
        }
    }

    companion object {
        private const val TAG = "PowerConnectionReceiver"
    }
}
