package com.bios.app.ingest

import android.content.Context

/**
 * Owner-configurable knobs for [PhoneSleepWorker] / [PhoneSleepScheduler].
 *
 * Currently exposes the morning wake-hour: the local hour-of-day at or
 * after which the worker considers the previous night "complete" and
 * fires the inference. Default 9, matching the legacy hardcoded value;
 * owners who wake earlier can lower it from Settings so the inference
 * fires on the first post-wake worker tick instead of waiting until 9.
 *
 * Stored in plain SharedPreferences — no secrets, owner-tunable, and
 * the read happens on the worker thread once per firing so a synchronous
 * pref read is fine. Mirrors [com.bios.app.engine.SeizureDetectionPrefs]
 * so the settings-card pattern is uniform.
 */
object PhoneSleepPrefs {

    private const val PREFS_NAME = "bios_phone_sleep"
    private const val KEY_WAKE_HOUR = "wake_hour"

    /** Inclusive bounds for the wake-hour setting. 4 AM covers extreme
     *  early risers; noon covers extreme late sleepers. Outside this
     *  range the noon-to-noon overnight window stops being a sane
     *  partition. */
    const val MIN_WAKE_HOUR: Int = 4
    const val MAX_WAKE_HOUR: Int = 12

    fun wakeHour(context: Context): Int {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_WAKE_HOUR, PhoneSleepScheduler.DEFAULT_WAKE_HOUR)
        return stored.coerceIn(MIN_WAKE_HOUR, MAX_WAKE_HOUR)
    }

    fun setWakeHour(context: Context, hour: Int) {
        val clamped = hour.coerceIn(MIN_WAKE_HOUR, MAX_WAKE_HOUR)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_WAKE_HOUR, clamped).apply()
    }
}
