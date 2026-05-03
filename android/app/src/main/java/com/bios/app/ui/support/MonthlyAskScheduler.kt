package com.bios.app.ui.support

import android.content.Context

/**
 * Schedules the monthly community ask. Persists exactly one piece of state:
 * the timestamp of the last shown/dismissed popup. No donor flag, no
 * supporter status, no per-branch tracking. The popup re-appears on cadence
 * regardless of which action the user picked last time.
 *
 * See /home/mia/autonomo/android-apps-contribution-popup-guide.md for the
 * portfolio-wide policy this implements.
 */
class MonthlyAskScheduler(private val context: Context) {

    /**
     * Returns true when the cadence has elapsed since the last popup.
     * On first call (no stored timestamp), seeds the timestamp to `now` and
     * returns false — the first popup appears one cadence after install.
     */
    fun shouldShow(now: Long = System.currentTimeMillis()): Boolean {
        val stored = prefs().getLong(KEY_LAST_SHOWN, 0L)
        if (stored == 0L) {
            prefs().edit().putLong(KEY_LAST_SHOWN, now).apply()
            return false
        }
        return shouldShowPure(now, stored, CADENCE_MILLIS)
    }

    fun markShown(now: Long = System.currentTimeMillis()) {
        prefs().edit().putLong(KEY_LAST_SHOWN, now).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val CADENCE_DAYS = 30L
        const val CADENCE_MILLIS = CADENCE_DAYS * 24L * 3600L * 1000L
        const val KEY_LAST_SHOWN = "monthly_ask_last_shown"
        private const val PREFS_NAME = "bios_settings"

        fun shouldShowPure(
            nowMillis: Long,
            lastShownMillis: Long,
            cadenceMillis: Long = CADENCE_MILLIS
        ): Boolean {
            if (lastShownMillis <= 0L) return false
            return (nowMillis - lastShownMillis) >= cadenceMillis
        }
    }
}
