package com.bios.app.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bios.app.data.BiosDatabase
import com.bios.app.data.EmergencyContactRepo
import com.bios.app.model.AlertTier
import com.bios.app.model.EmergencyContact
import java.util.concurrent.TimeUnit

/**
 * Per-contact URGENT-acknowledgement timeout worker (#179, EM/CC audit
 * gap §2.1).
 *
 * Scheduled when [AlertManager] fires an URGENT alert and the owner has
 * configured at least one [EmergencyContact] with a non-null
 * [EmergencyContact.acknowledgementTimeoutMinutes] eligible for that
 * tier. On timeout *without* the owner having tapped "I'm OK" (which
 * cancels the worker by tag), this worker surfaces a notification whose
 * tap action opens the system SMS chooser pre-populated with a short
 * message to the contact.
 *
 * **Bios never sends a message.** The system SMS chooser hands the dial
 * decision back to the owner — the owner taps Send. This is the owner-
 * mediated path the manifesto requires: prospective consent (the owner
 * set the contact and the timeout while competent), final consent (the
 * owner — or a bystander who finds the phone — actively confirms the
 * send through Android's system UI).
 *
 * No cloud, no SMS gateway, no Bios server. Pure local
 * [Intent.ACTION_SENDTO] with an `smsto:` URI.
 */
class UrgentAckTimeoutWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val anomalyId = inputData.getString(KEY_ANOMALY_ID) ?: return Result.failure()
        val alertTitle = inputData.getString(KEY_ALERT_TITLE) ?: "Urgent health alert"
        val tierLevel = inputData.getInt(KEY_TIER_LEVEL, AlertTier.URGENT.level)

        val db = BiosDatabase.getInstance(applicationContext)

        // Skip if the owner already acknowledged the alert. AlertManager
        // sets acknowledgedAt when the "I'm OK" action fires; if that
        // happened before the worker ran (race window between
        // ExistingWorkPolicy.REPLACE and tag cancellation), bail.
        val anomaly = db.anomalyDao().fetchRecent(100).find { it.id == anomalyId }
        if (anomaly == null || anomaly.acknowledged) return Result.success()

        val repo = EmergencyContactRepo(db)
        val contacts = repo.eligibleForTier(tierLevel)
        if (contacts.isEmpty()) return Result.success()

        for (contact in contacts) {
            surfaceEscalationPrompt(contact, alertTitle, anomalyId)
        }
        return Result.success()
    }

    private fun surfaceEscalationPrompt(
        contact: EmergencyContact,
        alertTitle: String,
        anomalyId: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        // Factual SMS body — no diagnosis, no speculation. The owner set
        // this person as their contact while competent; the contact gets
        // the same factual statement Bios uses internally.
        val body = "Bios on this phone hasn't received an acknowledgement on an urgent " +
            "health alert (\"$alertTitle\"). The owner asked me to let you know."
        val smsUri = Uri.parse(
            "smsto:" + Uri.encode(contact.phoneNumber)
        )
        val composeIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            ("escalate_${contact.id}_$anomalyId").hashCode(),
            composeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(
            applicationContext, AlertManager.CHANNEL_URGENT
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("No acknowledgement on urgent alert")
            .setContentText("Tap to text ${contact.name} (${contact.relationship}).")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "An urgent alert from Bios has not been acknowledged. " +
                        "You designated ${contact.name} (${contact.relationship}) as a " +
                        "contact for this case. Tap to compose a text — your phone will " +
                        "ask you to confirm before sending."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(("escalate_${contact.id}_$anomalyId").hashCode(), notification)
    }

    companion object {
        const val KEY_ANOMALY_ID = "anomaly_id"
        const val KEY_ALERT_TITLE = "alert_title"
        const val KEY_TIER_LEVEL = "tier_level"

        /** Unique-work tag for a given anomaly. Lets [cancel] dismiss
         *  the pending escalation when the owner acknowledges the alert. */
        fun workName(anomalyId: String): String = "urgent_ack_$anomalyId"

        /**
         * Schedule a per-anomaly escalation timeout. Uses the *minimum*
         * timeout across all eligible contacts so the first contact's
         * window dictates the schedule — subsequent contacts are surfaced
         * in the same fire (the worker iterates eligible contacts).
         *
         * Caller is expected to query [EmergencyContactRepo.eligibleForTier]
         * first; this method is a no-op if [delayMinutes] is null or ≤ 0.
         */
        fun schedule(
            context: Context,
            anomalyId: String,
            alertTitle: String,
            tierLevel: Int,
            delayMinutes: Int?,
        ) {
            val delay = delayMinutes ?: return
            if (delay <= 0) return

            val data = workDataOf(
                KEY_ANOMALY_ID to anomalyId,
                KEY_ALERT_TITLE to alertTitle,
                KEY_TIER_LEVEL to tierLevel,
            )

            val request = OneTimeWorkRequestBuilder<UrgentAckTimeoutWorker>()
                .setInitialDelay(delay.toLong(), TimeUnit.MINUTES)
                .setInputData(data)
                .addTag(workName(anomalyId))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(anomalyId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /** Cancel a pending escalation — called when the owner taps
         *  "I'm OK" / acknowledges the alert. Safe to call when no work
         *  was scheduled. */
        fun cancel(context: Context, anomalyId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(anomalyId))
        }
    }
}
