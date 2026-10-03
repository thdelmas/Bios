package com.bios.app.alerts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.bios.app.ui.MainActivity

/**
 * Tap target for alert notifications: opens MainActivity, which routes to the
 * alert detail screen through HandleDeepLinks. Without this, a tap on an alert
 * did nothing (contentIntent was null; docs/specs/alert-detail.md).
 */
object AlertNotificationIntents {
    const val EXTRA_OPEN_ALERT_ID = "com.bios.app.extra.OPEN_ALERT_ID"

    fun openAlert(context: Context, anomalyId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ALERT_ID, anomalyId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
