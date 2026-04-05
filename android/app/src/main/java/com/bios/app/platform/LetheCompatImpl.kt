package com.bios.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.bios.app.platform.DataDestroyer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real LETHE integration for the embedded flavor.
 *
 * Handles:
 * - Wipe signal receivers (burner mode, dead man's switch, panic, duress)
 * - Agent IPC via localhost:8080
 * - OTA coordination queries
 * - Burner mode / dead man's switch status
 */
class LetheCompatImpl(private val appContext: Context) : LetheCompat {

    private var wipeReceiver: BroadcastReceiver? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    override fun registerWipeReceivers(context: Context) {
        if (wipeReceiver != null) return

        wipeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.w(TAG, "Received LETHE wipe signal: ${intent.action}")
                DataDestroyer.destroyAll(ctx)
            }
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_BURNER_WIPE)
            addAction(ACTION_DMS_WIPE)
            addAction(ACTION_PANIC_WIPE)
            addAction(ACTION_DURESS_WIPE)
        }

        // LETHE wipe signals are internal system broadcasts — not exported
        context.registerReceiver(wipeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        Log.i(TAG, "LETHE wipe receivers registered")
    }

    override fun unregisterWipeReceivers(context: Context) {
        wipeReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
            wipeReceiver = null
        }
    }

    override suspend fun notifyHealthStatusChanged(status: HealthStatus) {
        Log.d(TAG, "Health status changed: ${status.overallState} (${status.activeAlertCount} alerts)")
        // Notify the LETHE agent via its IPC endpoint so it can refresh launcher cards
        try {
            val body = JSONObject().apply {
                put("state", status.overallState.name.lowercase())
                put("active_alerts", status.activeAlertCount)
                put("summary", status.summaryText)
            }.toString()
            val request = Request.Builder()
                .url("$AGENT_BASE_URL/bios/status")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (e: Exception) {
            // Agent may not be running — this is expected on first boot or during OTA
            Log.d(TAG, "Could not notify LETHE agent: ${e.message}")
        }
    }

    override fun isRebootSafe(): Boolean {
        // Synchronous check for broadcast receiver context.
        // Full async check via OtaCoordinator is exposed through the health API.
        return try {
            val hour = java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).hour
            val isSleepWindow = hour in 22..23 || hour in 0..6
            !isSleepWindow // Conservative: delay during sleep hours
        } catch (_: Exception) {
            true
        }
    }

    override fun isBurnerModeActive(): Boolean {
        return try {
            val value = System.getProperty("persist.lethe.burner.enabled")
            value == "true" || value == "1"
        } catch (_: Exception) {
            false
        }
    }

    override fun isDeadManSwitchArmed(): Boolean {
        return try {
            val value = System.getProperty("persist.lethe.dms.armed")
            value == "true" || value == "1"
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "BiosLetheCompat"
        private const val AGENT_BASE_URL = "http://127.0.0.1:8081"

        const val ACTION_BURNER_WIPE = "lethe.intent.action.BURNER_WIPE"
        const val ACTION_DMS_WIPE = "lethe.intent.action.DMS_WIPE"
        const val ACTION_PANIC_WIPE = "lethe.intent.action.PANIC_WIPE"
        const val ACTION_DURESS_WIPE = "lethe.intent.action.DURESS_WIPE"
    }
}
