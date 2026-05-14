package com.bios.app.provider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bios.app.ui.MainActivity

/**
 * Fires a system notification the first time a new package is recorded as
 * PENDING by [CompanionGate]. Without this, the consent UI is invisible to the
 * owner — companion apps would silently sit denied forever.
 *
 * Tapping the notification deep-links into Settings → Companion Apps via an
 * extra read by MainActivity.
 */
class CompanionAccessNotifier(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "bios_companion_consent"
        const val EXTRA_NAVIGATE_TO_COMPANIONS = "navigate_to_companions"
        private const val NOTIFICATION_ID_BASE = 0x7C00
    }

    init {
        ensureChannel()
    }

    fun notifyPending(packageName: String) {
        val manager = NotificationManagerCompat.from(context)
        // On API 33+ POST_NOTIFICATIONS is a runtime perm. If the owner declined,
        // stay silent — the consent UI is still reachable from Settings.
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO_COMPANIONS, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New companion app requesting access")
            .setContentText("$packageName is asking to read or write Bios data.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$packageName is asking to read or write Bios data. " +
                            "Nothing is shared until you approve it. Tap to review."
                )
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_BASE + (packageName.hashCode() and 0xFF), notification)
        } catch (_: SecurityException) {
            // Race: perm revoked between the check above and notify(). Swallow.
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Companion approvals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Lets you know when another app first asks to read or write Bios data."
            }
        )
    }
}
