package com.bios.app.engine

import android.content.Context

/**
 * Opt-in toggle for the wearable convulsive-seizure detector (#269
 * Cut 3a). When the owner enables this from `Settings → Seizure
 * detection`, the continuous accelerometer foreground service that
 * feeds [SeizureDetector] is allowed to run; when disabled (the
 * default), the service does not start and no automated detection
 * happens.
 *
 * The toggle is the privacy gate, not the safety gate. The safety
 * gate (#269 Cut 2, shipped via #287) is in
 * [com.bios.app.alerts.NeurologyUrgentPatterns]: wearable-inferred
 * SEIZURE_EVENT rows are excluded from the URGENT and ADVISORY
 * escalation paths regardless of this toggle. Owner-logged events
 * always fire those patterns; detector candidates land in the
 * timeline only and require owner confirmation before any push.
 *
 * Defaults to **off**: continuous accelerometer streaming with a
 * persistent wake-lock has a real (though small) battery cost, and
 * the manifesto's "owner is final" principle says we don't start
 * a privacy-relevant background capture without explicit consent.
 *
 * Mirrors the [PpgCalibrationLogger.isEnabled] / `setEnabled` shape
 * so the Settings card pattern is uniform across opt-in detectors.
 */
object SeizureDetectionPrefs {

    private const val PREFS_NAME = "bios_seizure_detection"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
