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
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.DetectionLatencyTracker
import com.bios.app.engine.PipelineStage
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly

/**
 * Delivers alerts as notifications and tracks their acknowledgement. An alert
 * says which metrics are outside the owner's usual range and by how much;
 * the notification carries those measured lines and nothing else.
 */
class AlertManager(
    private val context: Context,
    db: BiosDatabase,
    private val latencyTracker: DetectionLatencyTracker? = null,
) {
    private val anomalyDao = db.anomalyDao()

    companion object {
        const val CHANNEL_NOTICE = "bios_notice"
        const val CHANNEL_ADVISORY = "bios_advisory"
        const val CHANNEL_WARNING = "bios_warning"
        const val CHANNEL_DIGEST = "bios_digest"

        /** Channels of earlier versions, deleted from existing installs on start. */
        internal val REMOVED_CHANNELS = listOf("bios_urgent", "bios_followup", "bios_seizure_detection")

        /** The same sentence in every region, under every alert. */
        const val DISCLAIMER = "Bios is not a medical device. It shows measurements and does not interpret them."

        /**
         * [sendNotification] raises a system notification only for NOTICE and
         * above; OBSERVATION-tier alerts are stored but stay silent.
         */
        internal fun shouldNotify(tier: AlertTier): Boolean = tier >= AlertTier.NOTICE

        /**
         * Channel + notification priority for a tier. Nothing emits URGENT
         * any more; a stored row carrying it is treated as WARNING.
         */
        internal fun resolveChannel(tier: AlertTier): Pair<String, Int> = when {
            tier >= AlertTier.WARNING -> CHANNEL_WARNING to NotificationCompat.PRIORITY_HIGH
            tier == AlertTier.ADVISORY -> CHANNEL_ADVISORY to NotificationCompat.PRIORITY_DEFAULT
            else -> CHANNEL_NOTICE to NotificationCompat.PRIORITY_LOW
        }
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
        if (!shouldNotify(tier)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val (channelId, priority) = resolveChannel(tier)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(anomaly.title)
            .setContentText(anomaly.explanation.take(PREVIEW_CHARS))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${anomaly.explanation}\n\n$DISCLAIMER"))
            .setPriority(priority)
            .setContentIntent(
                AlertNotificationIntents.openAlert(context, anomaly.id, anomaly.id.hashCode())
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(anomaly.id.hashCode(), notification)

        latencyTracker?.record(
            PipelineStage.NOTIFICATION_DELIVERY,
            System.currentTimeMillis() - notificationStart,
            mapOf("tier" to tier.label),
        )
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        REMOVED_CHANNELS.forEach { manager.deleteNotificationChannel(it) }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NOTICE, "Notices", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "A few metrics outside your usual range" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ADVISORY, "Advisories", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Several metrics clearly outside your usual range" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNING, "Warnings", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "A wide deviation from your usual range" }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST, "Daily Digest", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Morning summary of your measurements" }
        )
    }
}

private const val PREVIEW_CHARS = 150
