package com.bios.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bios.app.R
import com.bios.app.config.RegionConfigProvider
import com.bios.app.data.BiosDatabase
import com.bios.app.data.GoalsOfCareRepo
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.PipelineStage
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly
import com.bios.app.model.InterventionLevel
import com.bios.app.physiology.PhysiologyState
import com.bios.app.physiology.PhysiologyStateStore
import kotlinx.coroutines.runBlocking

/**
 * Manages alert generation, notification delivery, and alert lifecycle.
 */
class AlertManager(
    private val context: Context,
    private val db: BiosDatabase,
    private val latencyTracker: DetectionLatencyTracker? = null,
    private val physiologyStateStore: PhysiologyStateStore =
        PhysiologyStateStore(context),
    private val goalsOfCareRepo: GoalsOfCareRepo = GoalsOfCareRepo(db),
) {
    private val anomalyDao = db.anomalyDao()

    companion object {
        const val CHANNEL_NOTICE = "bios_notice"
        const val CHANNEL_ADVISORY = "bios_advisory"
        const val CHANNEL_URGENT = "bios_urgent"
        const val CHANNEL_FOLLOWUP = "bios_followup"
        const val CHANNEL_DIGEST = "bios_digest"

        /**
         * Non-pejorative note appended to in-app alert text when the
         * URGENT-escalation pathway has been silenced because of the
         * owner's declared goals of care (#184). The text deliberately
         * avoids second-person lifestyle judgments, evaluative
         * language, and guilt mechanics — it states the fact of the
         * configuration and the path back to default.
         */
        const val GOALS_OF_CARE_NOTE: String =
            "Per the goals-of-care preference on file, no automatic " +
                "escalation will occur. Open the alert in-app to act on it, " +
                "or change the preference in Settings to restore default " +
                "escalation."
    }

    init {
        createNotificationChannels()
    }

    suspend fun fetchUnacknowledged(): List<Anomaly> = anomalyDao.fetchUnacknowledged()

    suspend fun fetchRecent(limit: Int = 20): List<Anomaly> = anomalyDao.fetchRecent(limit)

    suspend fun acknowledge(id: String) = anomalyDao.acknowledge(id)

    fun sendNotification(anomaly: Anomaly) {
        val notificationStart = System.currentTimeMillis()
        val tier = AlertTier.fromLevel(anomaly.severity)
        if (tier < AlertTier.NOTICE) return  // only notify for Notice and above

        // Pull-side-only patterns (#208 geriatric trajectory advisories,
        // future trajectory surfaces) persist their anomalies but never
        // raise a system notification. The owner reads the trend when
        // they navigate into the trajectory surface; Bios stays silent
        // until then.
        val patternId = anomaly.patternId
        if (patternId != null) {
            val pattern = ConditionPatterns.all.firstOrNull { it.id == patternId }
            if (pattern?.pullSideOnly == true) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        // Goals-of-care gate (#184). When the owner has declared
        // hospice mode or COMFORT_ONLY intervention level, an URGENT
        // alert is silenced from the system notification channel and
        // does NOT trigger downstream emergency escalation (SMS-to-
        // emergency-contact / tap-to-call, added by #215). The alert
        // is still persisted to the DB so it appears in-app for the
        // owner / caregivers to read.
        val urgentEscalationSuppressed =
            tier == AlertTier.URGENT && isUrgentEscalationSuppressed()

        val (channelId, priority) = when {
            urgentEscalationSuppressed ->
                // Silence: emit on the low-importance NOTICE channel
                // with PRIORITY_LOW so no sound / vibration fires.
                CHANNEL_NOTICE to NotificationCompat.PRIORITY_LOW
            tier == AlertTier.URGENT -> CHANNEL_URGENT to NotificationCompat.PRIORITY_HIGH
            tier == AlertTier.ADVISORY -> CHANNEL_ADVISORY to NotificationCompat.PRIORITY_DEFAULT
            else -> CHANNEL_NOTICE to NotificationCompat.PRIORITY_LOW
        }

        // Append regional disclaimer to explanation. Disclaimer text is
        // resolved through the localization overlay when available
        // (issue #210) — falls back to the English source on the
        // RegionConfig when no resource is declared for the active locale.
        val regionConfig = RegionConfigProvider.forCurrentLocale()
        val disclaimerKey = regionConfig.regulatory.alertDisclaimerKey
        val localizedDisclaimer = disclaimerKey?.let { key ->
            val resId = context.resources.getIdentifier(key, "string", context.packageName)
            if (resId != 0) context.resources.getString(resId).takeIf { it.isNotBlank() } else null
        }
        val disclaimer = localizedDisclaimer ?: regionConfig.regulatory.alertDisclaimer
        val baseExplanation = if (urgentEscalationSuppressed) {
            "${anomaly.explanation}\n\n$GOALS_OF_CARE_NOTE"
        } else {
            anomaly.explanation
        }
        val fullExplanation = "$baseExplanation\n\n$disclaimer"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(anomaly.title)
            .setContentText(anomaly.explanation.take(150))
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullExplanation))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(anomaly.id.hashCode(), notification)

        // Track notification delivery latency
        latencyTracker?.record(
            PipelineStage.NOTIFICATION_DELIVERY,
            System.currentTimeMillis() - notificationStart,
            mapOf(
                "tier" to tier.label,
                "pattern" to (anomaly.patternId ?: "unknown"),
                "goalsOfCareSuppressed" to urgentEscalationSuppressed.toString(),
            )
        )

        // Schedule a follow-up reminder in 24h to prompt journal entry
        FollowUpWorker.schedule(context, anomaly.id, anomaly.title)
    }

    /**
     * True when URGENT-tier auto-escalation should be short-circuited
     * (#184). Delegates to [UrgentEscalationGate] (pure logic) using
     * the current physiology state and the owner's declared
     * intervention level.
     *
     * Public so callers that bypass [sendNotification] — e.g. the
     * future emergency-contact escalation worker added by #215 — can
     * gate on the same condition.
     */
    fun isUrgentEscalationSuppressed(): Boolean {
        val state = physiologyStateStore.current()
        val interventionLevel = runBlocking {
            goalsOfCareRepo.fetch()?.interventionLevel ?: InterventionLevel.UNSPECIFIED
        }
        return UrgentEscalationGate.shouldSuppress(
            tier = AlertTier.URGENT,
            state = state,
            interventionLevel = interventionLevel,
        )
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NOTICE, "Health Notices", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Sustained deviations worth monitoring" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ADVISORY, "Health Advisories", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Multi-signal patterns matching known conditions" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_URGENT, "Urgent Health Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Acute anomalies requiring immediate attention" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOLLOWUP, "Journal Reminders", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Reminders to record how you're feeling after an alert" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST, "Daily Digest", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Morning summary of your vitals and health status" }
        )
    }
}
