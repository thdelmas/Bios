package com.bios.app.alerts

import android.content.Context

/**
 * Owner-set flags that gate the geriatric trajectory advisories
 * (#208, GERIATRICS_PALLIATIVE_POV §2.3 + §2.4 + §2.5).
 *
 * The audit converges on three trajectory surfaces — fall risk, delirium
 * risk, and cognitive trajectory — each anchored on an owner-declared
 * context flag rather than on Bios *inferring* the population. Manifesto
 * guard: Bios never auto-classifies an owner as frail, recently
 * discharged, or "fall-risk concerned". The owner picks.
 *
 * Why a separate store rather than an extension of [com.bios.app.physiology.PhysiologyStateStore]?
 *  - PhysiologyState is a *single-selection* enum; these are three
 *    independent boolean toggles, plus a transient timestamp (the
 *    discharge window auto-expires after 30 days per the
 *    Emergency Medicine post-discharge delirium audit).
 *  - Future fields belong here, not in the enum.
 *
 * v1 ships:
 *  - [fallRiskConcern] — owner opts in to receive fall-risk trajectory
 *    advisories. Default false.
 *  - [recentDischargeAtMillis] — owner records a hospital / ED
 *    discharge event. Auto-considered active for [DISCHARGE_WINDOW_DAYS]
 *    days, then ignored. Owner can clear it instantly.
 *  - [ageOver75] — owner-declared. Bios never infers age.
 *  - [cognitiveTrackingEnabled] — opt-in for typing-speed and
 *    related cognitive trajectory signals. Default false (data
 *    collection off until the owner actively enables).
 */
class GeriatricTrajectoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE,
    )

    var fallRiskConcern: Boolean
        get() = prefs.getBoolean(KEY_FALL_RISK_CONCERN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FALL_RISK_CONCERN, value).apply()
        }

    var ageOver75: Boolean
        get() = prefs.getBoolean(KEY_AGE_OVER_75, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AGE_OVER_75, value).apply()
        }

    var cognitiveTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_COGNITIVE_TRACKING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COGNITIVE_TRACKING, value).apply()
        }

    /**
     * Epoch-millis of the most recently recorded hospital / ED discharge,
     * or 0 if none / cleared. The [isRecentlyDischarged] helper applies
     * the [DISCHARGE_WINDOW_DAYS] window.
     */
    var recentDischargeAtMillis: Long
        get() = prefs.getLong(KEY_DISCHARGE_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_DISCHARGE_AT, value).apply()
        }

    /**
     * True when the owner-recorded discharge timestamp falls within the
     * 30-day post-discharge window. Outside that window (or when no
     * discharge has been recorded), returns false.
     */
    fun isRecentlyDischarged(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val at = recentDischargeAtMillis
        if (at <= 0L) return false
        val ageMillis = nowMillis - at
        return ageMillis in 0L..DISCHARGE_WINDOW_MS
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_FALL_RISK_CONCERN)
            .remove(KEY_AGE_OVER_75)
            .remove(KEY_COGNITIVE_TRACKING)
            .remove(KEY_DISCHARGE_AT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "bios_geriatric_trajectory"
        private const val KEY_FALL_RISK_CONCERN = "fall_risk_concern"
        private const val KEY_AGE_OVER_75 = "age_over_75"
        private const val KEY_COGNITIVE_TRACKING = "cognitive_tracking_enabled"
        private const val KEY_DISCHARGE_AT = "recent_discharge_at_millis"

        /**
         * Post-discharge delirium window. Inouye 1990 / CAM-S delirium
         * literature anchors highest incidence in the first 30 days
         * post-discharge. The Emergency Medicine audit's "POD_0_30"
         * label refers to this window.
         */
        const val DISCHARGE_WINDOW_DAYS: Int = 30
        const val DISCHARGE_WINDOW_MS: Long = DISCHARGE_WINDOW_DAYS.toLong() * 24L * 3600L * 1000L
    }
}
