package com.bios.app.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bios.app.model.SourceType
import com.bios.app.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sends and tracks Bios-state "your source X stopped syncing" pushes.
 *
 * Storage shape is intentionally small — per-source last-pushed-at and
 * a single owner-disable toggle, both kept in a dedicated SharedPreferences
 * file so a Room schema migration isn't required for what's essentially
 * per-source bookkeeping. The state isn't synced (it's local notification
 * state) and it's cleared by [com.bios.app.platform.DataDestroyer]'s
 * `clearPreferences` step on full wipe.
 *
 * Notification content follows the AlertContentPolicy rule for
 * category-3 pushes: factual statement about Bios's state, no "you
 * should" / "you need to" framing. Tap deep-links to Data Coverage.
 */
class DisconnectNotifier(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    init {
        ensureChannel()
    }

    /**
     * Whether the owner has the disconnect-push surface enabled. Default
     * is on — silent system failure is the manifesto-anti-pattern; opting
     * in is the trade-off the owner makes for fewer notifications.
     */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun lastPushedAt(sourceType: SourceType): Long =
        prefs.getLong(lastPushedAtKey(sourceType), 0L)

    fun notifyAndRecord(alert: DisconnectAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            // Owner muted Bios system-wide — don't try, don't crash.
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO_DATA_COVERAGE, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.sourceType.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val lastSyncDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(alert.lastSyncAt))
        val text = "${alert.displayName} hasn't synced since $lastSyncDate. Reconnect in Bios?"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${alert.displayName} stopped syncing")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_BASE + alert.sourceType.ordinal, notification)
            prefs.edit().putLong(lastPushedAtKey(alert.sourceType), System.currentTimeMillis()).apply()
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS race between manager.areNotificationsEnabled()
            // and notify(). Swallow — the next sync will retry.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // Low importance — Bios-state pushes should arrive in the
        // notification drawer without sound or heads-up. Owners who
        // want them louder can adjust per-channel in OS settings.
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, "Bios system", NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    "Bios reporting on its own state — broken adapters, expired sync tokens."
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "bios_system_state"
        const val EXTRA_NAVIGATE_TO_DATA_COVERAGE = "navigate_to_data_coverage"
        private const val PREF_FILE = "bios_disconnect_state"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_PUSHED_AT_PREFIX = "last_pushed_at:"
        private const val NOTIFICATION_ID_BASE = 7000

        private fun lastPushedAtKey(sourceType: SourceType): String =
            "$KEY_LAST_PUSHED_AT_PREFIX${sourceType.key}"
    }
}
