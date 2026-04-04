package com.bios.app.platform

import android.content.Context

/**
 * No-op implementation of [LetheCompat] for stock Android.
 * All methods are safe to call and do nothing.
 */
class LetheCompatNoop : LetheCompat {

    override fun registerWipeReceivers(context: Context) {
        // No LETHE wipe signals on stock Android
    }

    override fun unregisterWipeReceivers(context: Context) {
        // Nothing to unregister
    }

    override suspend fun notifyHealthStatusChanged(status: HealthStatus) {
        // No LETHE agent to notify
    }

    override fun isRebootSafe(): Boolean {
        // Always safe — no LETHE OTA coordination
        return true
    }

    override fun isBurnerModeActive(): Boolean = false

    override fun isDeadManSwitchArmed(): Boolean = false
}
