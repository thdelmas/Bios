package com.bios.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bios.app.data.BiosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the acknowledge action of an alert notification:
 * [ACTION_ACKNOWLEDGE] marks the alert acknowledged and dismisses the
 * notification.
 *
 * Declared in AndroidManifest with android:exported="false" — only Bios
 * itself fires these intents.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val anomalyId = intent.getStringExtra(EXTRA_ANOMALY_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, anomalyId.hashCode())

        when (intent.action) {
            ACTION_ACKNOWLEDGE -> handleAcknowledge(context, anomalyId, notificationId)
        }
    }

    private fun handleAcknowledge(context: Context, anomalyId: String, notificationId: Int) {
        // Dismiss the notification immediately for owner feedback.
        NotificationManagerCompat.from(context).cancel(notificationId)

        // Persist the acknowledgement asynchronously. Using a detached
        // scope is acceptable here: the broadcast is short-lived and the
        // DAO call is idempotent.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val db = BiosDatabase.getInstance(context.applicationContext)
                db.anomalyDao().acknowledge(anomalyId)
            } catch (_: Exception) {
                // Best-effort persistence; the alert stays unacknowledged in-app.
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "com.bios.app.alerts.ACTION_ACKNOWLEDGE"
        const val EXTRA_ANOMALY_ID = "anomaly_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
