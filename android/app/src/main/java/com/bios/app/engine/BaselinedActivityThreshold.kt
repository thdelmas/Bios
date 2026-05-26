package com.bios.app.engine

import android.content.Context

/**
 * Per-owner-learned activity threshold for
 * [PhoneSleepInference.isQuietSample] (#244 Cut 2).
 *
 * The prior hardcoded `ACTIVITY_THRESHOLD = 0.5f (m/s²)²` has no
 * published basis — the actigraphy literature explicitly warns that
 * thresholds are device- and population-dependent. Modern Android
 * phones span at least an order of magnitude in accelerometer noise
 * floor (sensor-hub class on flagships vs commodity sensors on
 * sub-$200 mid-tier), so a single global cutoff over-fires on quiet
 * devices and under-fires on noisy ones.
 *
 * The learned threshold is **3× the 25th percentile of
 * accelMagnitudeVar measured during the owner's nominal quiet hours
 * over a rolling 7-day window**. The 3× factor pulls the threshold
 * above the owner's own quiet-night noise floor; the P25 baseline is
 * robust to occasional outliers (a single restless night doesn't
 * shift the threshold).
 *
 * **Fallback.** Until at least [MIN_SAMPLES_FOR_BASELINE] quiet-hour
 * samples have accumulated, [currentThreshold] returns the
 * conservative pre-Cut-2 constant
 * [PhoneSleepInference.ACTIVITY_THRESHOLD]. Same shape as
 * [SeizureDetectionPrefs] and [PpgCalibrationLogger] — owner-facing
 * features fall back to a documented default when the learned signal
 * isn't ready.
 *
 * **Storage.** Rolling sample buffer as a comma-separated `Float`
 * string in [PREFS_NAME]; pruning happens lazily on the next
 * [recordSample] call. Sample count stays small (typically ~660
 * samples at the FG-service 60s cadence × 11 quiet hours × 7 days),
 * so the CSV serialization is well under 50 KB even in the worst
 * case.
 *
 * Mirrors the per-owner-calibration spirit of Bios's "instrument,
 * not coach" principle: Bios learns *this* owner's quiet baseline,
 * not a population average.
 */
object BaselinedActivityThreshold {

    /** Multiplier applied to the P25 baseline. Pulls the threshold
     *  comfortably above the owner's quiet-night noise floor while
     *  staying low enough to catch genuine movement. */
    const val P25_MULTIPLIER: Float = 3.0f

    /** Minimum quiet-hour samples before a learned threshold is
     *  trusted. Below this, [currentThreshold] returns the fallback.
     *  Picked so the buffer covers at least ~6 hours of one quiet
     *  night at the 60s FG-service cadence. */
    const val MIN_SAMPLES_FOR_BASELINE: Int = 360

    /** Rolling window the baseline reads from. 7 days per #244's
     *  spec — long enough that one anomalous night doesn't shift the
     *  threshold, short enough that the owner's actual current
     *  device / movement regime dominates. */
    private const val WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** SharedPreferences file + keys. */
    private const val PREFS_NAME = "bios_baselined_activity_threshold"
    private const val KEY_SAMPLES = "samples"
    private const val KEY_TIMESTAMPS = "timestamps"

    // -- pure compute (JVM-testable) --

    /**
     * Compute the learned threshold from a list of quiet-hour
     * variance samples. Returns null when there aren't enough
     * samples; callers should fall back to the documented default.
     *
     * The result is `[P25_MULTIPLIER] * P25(samples)` with a sanity
     * floor at 0.05 — a degenerate phone (perfectly still on a
     * laboratory bench) could produce a near-zero P25; multiplying
     * by 3 still leaves an unusably small threshold. The floor keeps
     * the threshold above plausible sensor-hub quantization noise.
     */
    fun compute(samples: List<Float>): Float? {
        if (samples.size < MIN_SAMPLES_FOR_BASELINE) return null
        val sorted = samples.sorted()
        val p25Index = (sorted.size * 25 / 100).coerceIn(0, sorted.size - 1)
        val p25 = sorted[p25Index]
        val raw = P25_MULTIPLIER * p25
        return raw.coerceAtLeast(MIN_LEARNED_THRESHOLD)
    }

    /** Sanity floor for the learned threshold. See [compute]. */
    private const val MIN_LEARNED_THRESHOLD: Float = 0.05f

    /**
     * Prune samples older than [WINDOW_MS] from the supplied parallel
     * sample / timestamp arrays. Returns the surviving subset (both
     * lists in lockstep). Pure logic for JVM testing.
     */
    fun pruneOldSamples(
        samples: List<Float>,
        timestamps: List<Long>,
        nowMs: Long,
    ): Pair<List<Float>, List<Long>> {
        require(samples.size == timestamps.size) {
            "samples (${samples.size}) and timestamps (${timestamps.size}) must be parallel"
        }
        val cutoff = nowMs - WINDOW_MS
        val keptSamples = mutableListOf<Float>()
        val keptTimestamps = mutableListOf<Long>()
        for (i in samples.indices) {
            if (timestamps[i] >= cutoff) {
                keptSamples += samples[i]
                keptTimestamps += timestamps[i]
            }
        }
        return keptSamples to keptTimestamps
    }

    // -- Android-bound storage --

    /**
     * Record a quiet-hour variance sample into the rolling buffer.
     * Callers are responsible for the quiet-hour gate (typically via
     * [com.bios.app.ingest.PhoneSleepQuietHours]); this method does
     * not check. Triggers a prune of >7-day-old samples.
     */
    fun recordSample(context: Context, variance: Float, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingSamples = parseFloatCsv(prefs.getString(KEY_SAMPLES, "").orEmpty())
        val existingTimestamps = parseLongCsv(prefs.getString(KEY_TIMESTAMPS, "").orEmpty())
        val (prunedSamples, prunedTimestamps) =
            pruneOldSamples(existingSamples, existingTimestamps, nowMs)
        val newSamples = prunedSamples + variance
        val newTimestamps = prunedTimestamps + nowMs
        prefs.edit()
            .putString(KEY_SAMPLES, newSamples.joinToString(",") { it.toString() })
            .putString(KEY_TIMESTAMPS, newTimestamps.joinToString(",") { it.toString() })
            .apply()
    }

    /**
     * Return the current threshold for [PhoneSleepInference.isQuietSample]
     * — either the learned value if the buffer has crossed
     * [MIN_SAMPLES_FOR_BASELINE], or the documented pre-Cut-2 fallback
     * otherwise. Safe to call from any thread.
     */
    fun currentThreshold(context: Context): Float {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val samples = parseFloatCsv(prefs.getString(KEY_SAMPLES, "").orEmpty())
        return compute(samples) ?: PhoneSleepInference.ACTIVITY_THRESHOLD
    }

    /** Test-only reset hook. */
    internal fun clearForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun parseFloatCsv(csv: String): List<Float> {
        if (csv.isBlank()) return emptyList()
        return csv.split(",").mapNotNull { it.toFloatOrNull() }
    }

    private fun parseLongCsv(csv: String): List<Long> {
        if (csv.isBlank()) return emptyList()
        return csv.split(",").mapNotNull { it.toLongOrNull() }
    }
}
