package com.bios.app.ingest

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Alarm relay that legally launches [PhoneSleepCollectionService] from
 * the background (#315 — fixes the original #241 Cut 1 receiver).
 *
 * ## Why this exists
 *
 * `PowerConnectionReceiver.onReceive` runs as a background broadcast.
 * Since Android 12, a background receiver cannot call
 * `startForegroundService` for `ACTION_POWER_CONNECTED` — the system
 * throws `ForegroundServiceStartNotAllowedException` and the service
 * never starts. Real-world consequence: no overnight sleep collection.
 *
 * Alarms scheduled via `AlarmManager.setExactAndAllowWhileIdle` *are*
 * on the FGS-from-background exemption list. So the flow is:
 *
 *   ACTION_POWER_CONNECTED → schedule exact alarm (~3 s out)
 *     → alarm fires → this receiver → startForegroundService (legal)
 *
 * The short delay is essentially free — quiet-hours collection at
 * per-minute cadence; a 3-second deferral on the start makes no
 * difference to inference quality.
 *
 * ## Permission shape
 *
 * `setExactAndAllowWhileIdle` requires `SCHEDULE_EXACT_ALARM` on
 * API 31+. Auto-granted on API 31–32; user-revocable on 33+. Callers
 * must check [canScheduleExactAlarms] and fall back honestly (log,
 * let the periodic [PhoneSleepWorker] handle the night) when the
 * permission has been revoked.
 */
class PhoneSleepAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_COLLECTION) {
            Log.w(TAG, "ignoring unexpected action: ${intent.action}")
            return
        }
        // Defensive re-check: the alarm could fire seconds after the
        // owner unplugged the phone (race) or after quiet-hours ended
        // (clock jump). The service does the same check itself, but
        // surfacing the decision here keeps the log honest.
        if (!PhoneSleepQuietHours.isInQuietHoursNow()) {
            Log.i(TAG, "alarm fired but no longer in quiet hours; skipping FGS start")
            return
        }
        val serviceIntent = Intent(context, PhoneSleepCollectionService::class.java)
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.i(TAG, "FGS launch issued from alarm context")
        } catch (e: Exception) {
            // Should not happen: alarm-triggered receivers carry the
            // FGS-launch exemption. If it does, the periodic worker
            // remains the safety net.
            Log.w(TAG, "FGS launch from alarm failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PhoneSleepAlarmReceiver"

        /** Explicit action so the receiver only handles our own intents. */
        const val ACTION_START_COLLECTION =
            "com.bios.app.ingest.action.START_SLEEP_COLLECTION"

        /**
         * Stable request code for the PendingIntent. Stable means a
         * re-schedule replaces the prior alarm rather than stacking.
         */
        private const val PI_REQUEST_CODE = 0x515A

        /**
         * Short deferral between scheduling and firing. Long enough for
         * the alarm subsystem to register the request as a fresh event
         * (and thus grant the FGS-launch exemption), short enough that
         * the owner perceives the service as starting "immediately on
         * plug-in".
         */
        const val SCHEDULE_DELAY_MS: Long = 3_000L

        /**
         * Schedule a one-shot alarm that will start the collection
         * service [SCHEDULE_DELAY_MS] from now. Returns true iff the
         * alarm was scheduled — false means the exact-alarm permission
         * has been revoked and the caller should log + fall back to the
         * periodic worker.
         */
        fun scheduleStart(context: Context): Boolean {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: run {
                Log.w(TAG, "AlarmManager unavailable; cannot schedule start")
                return false
            }
            if (!canScheduleExactAlarms(alarmManager)) {
                Log.w(
                    TAG,
                    "exact-alarm permission revoked; falling back to periodic worker",
                )
                return false
            }
            val pi = pendingIntent(context)
            val triggerAt = System.currentTimeMillis() + SCHEDULE_DELAY_MS
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pi,
                )
                Log.i(TAG, "exact alarm scheduled for +${SCHEDULE_DELAY_MS}ms")
                return true
            } catch (e: SecurityException) {
                // Race: permission revoked between canScheduleExactAlarms()
                // and the schedule call. Treat as fallback.
                Log.w(TAG, "exact-alarm scheduling threw: ${e.message}", e)
                return false
            }
        }

        /** Cancel any pending start alarm. Idempotent. */
        fun cancelStart(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PhoneSleepAlarmReceiver::class.java)
                .setAction(ACTION_START_COLLECTION)
            // FLAG_IMMUTABLE is required on API 31+; FLAG_UPDATE_CURRENT
            // ensures a re-schedule replaces the prior alarm.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(
                context,
                PI_REQUEST_CODE,
                intent,
                flags,
            )
        }

        private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
            // canScheduleExactAlarms() ships on API 31. On older releases
            // SCHEDULE_EXACT_ALARM is implicit and always granted.
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        }
    }
}
