package com.bios.app.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.bios.app.data.BiosDatabase
import com.bios.app.model.MetricType
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Sends a daily morning notification summarizing yesterday's vitals
 * so the user stays connected to their health data even when
 * everything is normal.
 */
class DailyDigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!isEnabled(applicationContext)) return Result.success()

        val db = BiosDatabase.getInstance(applicationContext)
        val summary = buildSummary(db)
        sendNotification(summary)
        return Result.success()
    }

    internal suspend fun buildSummary(db: BiosDatabase): DigestSummary {
        val now = System.currentTimeMillis()
        val yesterday = now - 24 * 3600 * 1000L

        val unacknowledged = db.anomalyDao().fetchUnacknowledged().size

        val rhr = db.metricReadingDao()
            .fetchLatest(MetricType.HEART_RATE.key).firstOrNull()
        val hrv = db.metricReadingDao()
            .fetchLatest(MetricType.HEART_RATE_VARIABILITY.key).firstOrNull()
        val steps = db.metricReadingDao()
            .fetchValues(MetricType.STEPS.key, yesterday, now)
            .sum().toLong()

        return DigestSummary(
            rhr = rhr?.value?.toInt(),
            hrv = hrv?.value?.toInt(),
            steps = steps,
            pendingAlerts = unacknowledged
        )
    }

    internal fun formatMessage(summary: DigestSummary): Pair<String, String> {
        val title = if (summary.pendingAlerts > 0)
            "Daily Check-in \u2022 ${summary.pendingAlerts} alert${if (summary.pendingAlerts == 1) "" else "s"}"
        else
            "Daily Check-in \u2022 All clear"

        val parts = mutableListOf<String>()
        summary.rhr?.let { parts += "RHR ${it}bpm" }
        summary.hrv?.let { parts += "HRV ${it}ms" }
        if (summary.steps > 0) parts += "${summary.steps} steps"

        val body = if (parts.isEmpty())
            "Open Bios to sync your latest health data."
        else
            parts.joinToString(" \u2022 ") + ". Tap to see details."

        return title to body
    }

    private fun sendNotification(summary: DigestSummary) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val (title, body) = formatMessage(summary)

        val notification = NotificationCompat.Builder(
            applicationContext, AlertManager.CHANNEL_DIGEST
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID, notification)
    }

    data class DigestSummary(
        val rhr: Int?,
        val hrv: Int?,
        val steps: Long,
        val pendingAlerts: Int
    )

    companion object {
        const val WORK_NAME = "bios_daily_digest"
        const val PREF_KEY = "daily_digest_enabled"
        private const val NOTIFICATION_ID = 9001
        private const val TARGET_HOUR = 8 // 8 AM local time

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            val initialDelay = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY, true)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("bios_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY, enabled)
                .apply()
            if (enabled) schedule(context) else cancel(context)
        }
    }
}