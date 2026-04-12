package com.bios.app.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bios.app.alerts.AlertManager
import org.json.JSONObject

/**
 * Parses and dispatches incoming UnifiedPush messages.
 *
 * Message types:
 * - population_signal: regional health advisory -> local notification
 * - model_update: new anomaly model available -> trigger background download
 * - ping: connectivity test -> log and discard
 *
 * All messages are treated as untrusted: validated, size-bounded, and never stored raw.
 */
class PushMessageHandler(private val context: Context) {

    companion object {
        private const val TAG = "BiosPushMsg"
        private const val MAX_MESSAGE_BYTES = 4096
        private const val NOTIFICATION_ID_BASE = 20_000
    }

    fun handle(message: ByteArray) {
        if (message.size > MAX_MESSAGE_BYTES) {
            Log.w(TAG, "Discarding oversized push message: ${message.size} bytes")
            return
        }

        val json = try {
            JSONObject(String(message, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Discarding malformed push message", e)
            return
        }

        when (json.optString("type")) {
            "population_signal" -> handlePopulationSignal(json)
            "model_update" -> handleModelUpdate(json)
            "ping" -> Log.d(TAG, "Push ping received")
            else -> Log.w(TAG, "Unknown push message type: ${json.optString("type")}")
        }
    }

    private fun handlePopulationSignal(json: JSONObject) {
        val payload = json.optJSONObject("payload") ?: return
        val title = payload.optString("title", "").take(200)
        val explanation = payload.optString("explanation", "").take(500)
        val severity = payload.optInt("severity", 1).coerceIn(0, 3)

        if (title.isBlank()) {
            Log.w(TAG, "Population signal missing title, discarding")
            return
        }

        Log.d(TAG, "Population signal: $title (severity=$severity)")
        sendPopulationNotification(title, explanation, severity)
    }

    private fun handleModelUpdate(json: JSONObject) {
        val payload = json.optJSONObject("payload") ?: return
        val version = payload.optString("version", "")
        Log.d(TAG, "Model update available: v$version")
        // TODO: Enqueue model download WorkManager job once OTA update flow is wired
    }

    private fun sendPopulationNotification(
        title: String,
        explanation: String,
        severity: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val channelId = when {
            severity >= 3 -> AlertManager.CHANNEL_URGENT
            severity >= 2 -> AlertManager.CHANNEL_ADVISORY
            else -> AlertManager.CHANNEL_NOTICE
        }

        val priority = when {
            severity >= 3 -> NotificationCompat.PRIORITY_HIGH
            severity >= 2 -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(explanation.take(150))
            .setStyle(NotificationCompat.BigTextStyle().bigText(explanation))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        val notificationId = NOTIFICATION_ID_BASE + title.hashCode().and(0xFFFF)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
