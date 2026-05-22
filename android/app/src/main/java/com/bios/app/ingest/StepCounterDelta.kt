package com.bios.app.ingest

import android.content.Context
import android.os.SystemClock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Converts cumulative TYPE_STEP_COUNTER readings (steps since device boot)
 * into per-sample deltas, persisting the last-seen value per source so that
 * repeat reads don't double-count.
 *
 * The hardware counter resets on every reboot, so we also persist boot time
 * and detect resets by either a counter decrease or a boot-time shift.
 *
 * On the first reading for a source (or right after a detected reboot) we
 * emit the full cumulative as today's delta only if boot happened on or
 * after today's local midnight — in that case the cumulative count *is*
 * today's steps so far. Otherwise we suppress and wait for the next
 * reading so a genuine delta can be measured.
 */
class StepCounterDelta(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /**
     * Records [cumulative] as the new baseline for [sourceId] and returns
     * the number of new steps to emit (or null when none should be).
     */
    fun computeDelta(cumulative: Long, sourceId: String): Long? {
        val bootMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val todayStartMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val lastCumulative = prefs.getLong(cumKey(sourceId), -1L)
        val lastBootMs = prefs.getLong(bootKey(sourceId), -1L)

        val delta = computePure(
            cumulative, bootMs, lastCumulative, lastBootMs, todayStartMs
        )

        prefs.edit()
            .putLong(cumKey(sourceId), cumulative)
            .putLong(bootKey(sourceId), bootMs)
            .apply()

        return delta
    }

    private fun cumKey(sourceId: String) = "cum_$sourceId"
    private fun bootKey(sourceId: String) = "boot_$sourceId"

    companion object {
        private const val PREFS_FILE = "bios_step_counter_delta"

        // Boot-time jitter below this is clock noise, not a real reboot.
        private const val BOOT_JITTER_MS = 5_000L

        /**
         * Pure delta logic, exposed for unit tests.
         * @return new steps to emit, or null to emit nothing.
         */
        internal fun computePure(
            cumulative: Long,
            bootMs: Long,
            lastCumulative: Long,
            lastBootMs: Long,
            todayStartMs: Long
        ): Long? {
            if (cumulative <= 0L) return null
            val firstReading = lastCumulative < 0L
            val rebooted = !firstReading && (
                cumulative < lastCumulative ||
                    kotlin.math.abs(bootMs - lastBootMs) > BOOT_JITTER_MS
                )
            return when {
                firstReading || rebooted ->
                    if (bootMs >= todayStartMs) cumulative else null
                else -> (cumulative - lastCumulative).takeIf { it > 0L }
            }
        }
    }
}
