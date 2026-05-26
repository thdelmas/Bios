package com.bios.app.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Charge edge-trigger for [PhoneSleepCollectionService] (#241 Cut 1,
 * receiver-launch path repaired in #315).
 *
 * The receiver listens for the OS-broadcast power events:
 *
 * - `ACTION_POWER_CONNECTED` — schedule an exact alarm (via
 *   [PhoneSleepAlarmReceiver]) iff [PhoneSleepQuietHours.isInQuietHoursNow]
 *   is true. Daytime charging (commute, desk, café power bank) is not
 *   sleep and never triggers the service.
 * - `ACTION_POWER_DISCONNECTED` — cancel any pending start alarm and
 *   stop the collection service unconditionally. The morning unplug is
 *   the canonical end-of-night signal for phone-only inference.
 *
 * ## Why not start the FGS directly?
 *
 * Since Android 12, a background `BroadcastReceiver` cannot call
 * `startForegroundService` for non-allowlisted broadcasts.
 * `ACTION_POWER_CONNECTED` is not on the allowlist, so the direct call
 * threw `ForegroundServiceStartNotAllowedException` every night (#315).
 * Alarms scheduled via `setExactAndAllowWhileIdle` *are* on the FGS
 * launch-exemption list — so we schedule a short-deferred alarm and let
 * [PhoneSleepAlarmReceiver] perform the legal launch.
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
                    Log.i(TAG, "power connected outside quiet-hours; not scheduling start")
                    return
                }
                Log.i(TAG, "power connected during quiet-hours; scheduling alarm-relay start")
                val scheduled = PhoneSleepAlarmReceiver.scheduleStart(context)
                if (!scheduled) {
                    // Permission-revoked fallback: the 15-min PhoneSleepWorker
                    // remains the safety net. We deliberately do NOT call
                    // startForegroundService here — it throws from a background
                    // receiver context (#315). Honest logging is the contract;
                    // a dashboard surface is a follow-up.
                    Log.w(
                        TAG,
                        "alarm-relay unavailable; relying on PhoneSleepWorker for the night",
                    )
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.i(TAG, "power disconnected; cancelling pending alarm and stopping service")
                PhoneSleepAlarmReceiver.cancelStart(context)
                context.stopService(serviceIntent)
            }
        }
    }

    companion object {
        private const val TAG = "PowerConnectionReceiver"
    }
}
