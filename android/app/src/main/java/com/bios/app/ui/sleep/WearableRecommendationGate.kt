package com.bios.app.ui.sleep

import com.bios.app.model.ConfidenceTier
import com.bios.app.model.MetricReading

/**
 * Pure predicate that decides whether the wearable-recommendation
 * banner (#245) should appear on the sleep dashboard.
 *
 * Even with the lifecycle fix (#241), the sensor-surface expansion
 * (#243), Cole-Kripke (#244), and per-owner baselining, the phone-only
 * ceiling is structurally low — Sleep As Android's decade of phone-
 * only ML caps at ~1/3 awake-period recall, and Gadgetbridge's
 * 10+-year FOSS effort explicitly chose **not** to attempt phone-side
 * sleep inference. When Bios's phone-only inference has been
 * producing only LOW-confidence rows (or nothing) for a sustained
 * window, the honest escape valve is to surface the option of a
 * wearable.
 *
 * Manifesto framing: this is a **suggestion**, not a nag. The
 * predicate fires once and the surface is dismissable; a 30-day
 * cooldown re-evaluates only if the condition still holds.
 *
 * ## When the banner shows
 *
 * 1. The last [REQUIRED_NIGHTS] calendar nights in the inference
 *    window each carry either:
 *    - no SLEEP_DURATION reading at all, **or**
 *    - only readings at [ConfidenceTier.LOW] (or VENDOR_DERIVED) from
 *      any source.
 * 2. AND the owner has not dismissed the banner inside the past
 *    [REDISPLAY_AFTER_MS].
 *
 * Pure-function — takes the night-list and the dismissed-at timestamp
 * (or null) and returns a Boolean. The Compose-side caller persists
 * the dismissed timestamp via SharedPreferences; the predicate is
 * Android-free for JVM unit testing.
 */
object WearableRecommendationGate {

    /**
     * Consecutive-night threshold. 7 nights is the smallest window
     * that's clearly "a pattern, not a fluke" — a single bad night
     * (travel, late dinner, sick) won't trip it.
     */
    const val REQUIRED_NIGHTS: Int = 7

    /**
     * Re-display cooldown after the owner dismisses the banner.
     * 30 days = roughly "ask once a month if the condition still
     * holds." Anything tighter starts to feel like a nag.
     */
    const val REDISPLAY_AFTER_MS: Long = 30L * 24L * 60L * 60L * 1000L

    /**
     * One night's worth of SLEEP_DURATION rows. The dashboard already
     * sorts and bins by calendar night; the gate takes the binned list.
     */
    data class Night(val readings: List<MetricReading>) {
        /**
         * True when this night carries either no reading at all or
         * only LOW-class confidence rows. VENDOR_DERIVED counts as
         * LOW for this gate — it's the "vendor said so, no
         * Bios-side validation" tier and doesn't clear the
         * unreliable-inference bar.
         */
        val isLowOrMissing: Boolean
            get() = readings.isEmpty() || readings.all {
                it.confidence <= ConfidenceTier.LOW.level
            }
    }

    /**
     * Evaluate the gate.
     *
     * @param recentNights last N calendar nights, newest first. Must
     *   contain at least [REQUIRED_NIGHTS] entries for the gate to
     *   fire — fewer entries means the dashboard hasn't had enough
     *   time to observe the pattern.
     * @param dismissedAtMs SharedPreferences-persisted timestamp of
     *   the most recent dismissal (or null if never dismissed).
     * @param nowMs current wall-clock time. Injected so tests can
     *   pin the cooldown evaluation.
     */
    fun shouldShow(
        recentNights: List<Night>,
        dismissedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (recentNights.size < REQUIRED_NIGHTS) return false
        val lastN = recentNights.take(REQUIRED_NIGHTS)
        if (!lastN.all { it.isLowOrMissing }) return false
        if (dismissedAtMs == null) return true
        return (nowMs - dismissedAtMs) >= REDISPLAY_AFTER_MS
    }

    /**
     * Bin a flat MetricReading list (newest first, as the dashboard
     * already produces) into per-calendar-night [Night] buckets. The
     * caller passes the result to [shouldShow]. The dashboard uses a
     * local-time day key; injecting `localDayKey` keeps this pure.
     */
    fun binByNight(
        readingsNewestFirst: List<MetricReading>,
        localDayKey: (Long) -> Long,
        nightsBack: Int = REQUIRED_NIGHTS,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Night> {
        val nowDay = localDayKey(nowMs)
        val buckets = LinkedHashMap<Long, MutableList<MetricReading>>()
        // Pre-seed the most-recent N days so empty nights still register
        // as a Night with no readings — that's the missing-row case the
        // gate needs to see.
        for (offset in 0 until nightsBack) {
            buckets[nowDay - offset] = mutableListOf()
        }
        for (r in readingsNewestFirst) {
            val day = localDayKey(r.timestamp)
            buckets[day]?.add(r)
        }
        return buckets.values.map { Night(it.toList()) }
    }
}
