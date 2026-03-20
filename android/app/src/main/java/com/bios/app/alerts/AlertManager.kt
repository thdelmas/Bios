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
import com.bios.app.data.BiosDatabase
import com.bios.app.model.AlertTier
import com.bios.app.model.Anomaly

/**
 * Manages alert generation, notification delivery, and alert lifecycle.
 */
class AlertManager(
    private val context: Context,
    private val db: BiosDatabase
) {
    private val anomalyDao = db.anomalyDao()

    companion object {
        const val CHANNEL_NOTICE = "bios_notice"
        const val CHANNEL_ADVISORY = "bios_advisory"
        const val CHANNEL_URGENT = "bios_urgent"
    }

    init {
        createNotificationChannels()
    }

    suspend fun fetchUnacknowledged(): List<Anomaly> = anomalyDao.fetchUnacknowledged()

    suspend fun fetchRecent(limit: Int = 20): List<Anomaly> = anomalyDao.fetchRecent(limit)

    suspend fun acknowledge(id: String) = anomalyDao.acknowledge(id)

    fun sendNotification(anomaly: Anomaly) {
        val tier = AlertTier.fromLevel(anomaly.severity)
        if (tier < AlertTier.NOTICE) return  // only notify for Notice and above

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val (channelId, priority) = when (tier) {
            AlertTier.URGENT -> CHANNEL_URGENT to NotificationCompat.PRIORITY_HIGH
            AlertTier.ADVISORY -> CHANNEL_ADVISORY to NotificationCompat.PRIORITY_DEFAULT
            else -> CHANNEL_NOTICE to NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(anomaly.title)
            .setContentText(anomaly.explanation.take(150))
            .setStyle(NotificationCompat.BigTextStyle().bigText(anomaly.explanation))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(anomaly.id.hashCode(), notification)
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
    }
}
