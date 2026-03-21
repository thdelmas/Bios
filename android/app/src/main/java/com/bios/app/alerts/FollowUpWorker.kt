package com.bios.app.alerts

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.bios.app.data.BiosDatabase
import java.util.concurrent.TimeUnit

/**
 * Sends a follow-up notification 24 hours after an alert, prompting the
 * user to record how they're feeling in their health journal — but only
 * if they haven't already provided feedback.
 */
class FollowUpWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val anomalyId = inputData.getString(KEY_ANOMALY_ID) ?: return Result.failure()
        val alertTitle = inputData.getString(KEY_ALERT_TITLE) ?: "Health Alert"

        val db = BiosDatabase.getInstance(applicationContext)
        val anomaly = db.anomalyDao().fetchRecent(100).find { it.id == anomalyId }

        // Skip if anomaly was deleted or user already gave feedback
        if (anomaly == null || anomaly.feedbackAt != null) return Result.success()

        sendFollowUp(anomalyId, alertTitle)
        return Result.success()
    }

    private fun sendFollowUp(anomalyId: String, alertTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notification = NotificationCompat.Builder(
            applicationContext, AlertManager.CHANNEL_FOLLOWUP
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("How are you feeling?")
            .setContentText("You had an alert: $alertTitle. Tap to update your health journal.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify("followup_$anomalyId".hashCode(), notification)
    }

    companion object {
        const val KEY_ANOMALY_ID = "anomaly_id"
        const val KEY_ALERT_TITLE = "alert_title"
        const val FOLLOW_UP_DELAY_HOURS = 24L

        fun schedule(context: Context, anomalyId: String, alertTitle: String) {
            val data = workDataOf(
                KEY_ANOMALY_ID to anomalyId,
                KEY_ALERT_TITLE to alertTitle
            )

            val request = OneTimeWorkRequestBuilder<FollowUpWorker>()
                .setInitialDelay(FOLLOW_UP_DELAY_HOURS, TimeUnit.HOURS)
                .setInputData(data)
                .addTag("followup_$anomalyId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "followup_$anomalyId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, anomalyId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("followup_$anomalyId")
        }
    }
}
