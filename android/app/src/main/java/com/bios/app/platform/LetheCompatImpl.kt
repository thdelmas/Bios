package com.bios.app.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.bios.app.platform.DataDestroyer

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
        // TODO: Send status to LETHE agent via localhost:8080/health/status
        // Phase 2.1 — Local health API implementation
        Log.d(TAG, "Health status changed: ${status.overallState} (${status.activeAlertCount} alerts)")
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

        const val ACTION_BURNER_WIPE = "lethe.intent.action.BURNER_WIPE"
        const val ACTION_DMS_WIPE = "lethe.intent.action.DMS_WIPE"
        const val ACTION_PANIC_WIPE = "lethe.intent.action.PANIC_WIPE"
        const val ACTION_DURESS_WIPE = "lethe.intent.action.DURESS_WIPE"
    }
}
