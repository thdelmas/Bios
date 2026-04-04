package com.bios.app.platform

import android.content.Context

/**
 * Interface for LETHE-specific functionality.
 *
 * On stock Android, [LetheCompatNoop] is used — all methods are safe no-ops.
 * On LETHE, [LetheCompatImpl] provides real integration with the LETHE agent,
 * launcher, wipe signals, and OTA coordination.
 */
interface LetheCompat {

    /** Register broadcast receivers for LETHE wipe signals. */
    fun registerWipeReceivers(context: Context)

    /** Unregister wipe receivers (cleanup on app destroy). */
    fun unregisterWipeReceivers(context: Context)

    /** Notify LETHE agent that health status has changed. */
    suspend fun notifyHealthStatusChanged(status: HealthStatus)

    /** Respond to LETHE OTA query: is it safe to reboot? */
    fun isRebootSafe(): Boolean

    /** Query whether burner mode is active on this device. */
    fun isBurnerModeActive(): Boolean

    /** Query whether dead man's switch is armed. */
    fun isDeadManSwitchArmed(): Boolean

    companion object {
        fun create(context: Context): LetheCompat {
            return if (PlatformDetector.isLethe(context)) {
                LetheCompatImpl(context)
            } else {
                LetheCompatNoop()
            }
        }
    }
}

/**
 * Health status summary exposed to the LETHE agent.
 * Contains no raw readings — only computed state.
 */
data class HealthStatus(
    val overallState: HealthState,
    val activeAlertCount: Int,
    val summaryText: String
)

enum class HealthState {
    NORMAL,
    OBSERVATION,
    NOTICE,
    ADVISORY,
    URGENT
}
