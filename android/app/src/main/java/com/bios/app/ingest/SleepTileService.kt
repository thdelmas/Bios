package com.bios.app.ingest

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.bios.app.data.BiosDatabase
import com.bios.app.data.SleepEntryRepo
import com.bios.app.ui.sleep.ManualSleepEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owner-asserted bedtime ↔ wake-time tile (#242 / #134).
 *
 * The most-starred FOSS sleep app on F-Droid is purely manual: owners
 * prefer a reliable manual log over an unreliable inference. This tile
 * is the same idea anchored to a one-tap surface — the owner taps once
 * at bedtime (tile flips to "Sleeping"), taps again at wake (tile
 * flips back to "Going to bed" and a `SLEEP_DURATION`
 * `MetricReading` lands with `ConfidenceTier.HIGH` via
 * [SleepEntryRepo]).
 *
 * "Owner is final" applies: the tile-written rows ride the same
 * SELF_REPORTED source as the existing dashboard duration entry, so
 * the baseline engine ignores them while they still surface in the
 * sleep dashboard, regularity calculator, and FHIR export.
 *
 * ## State
 *
 * The bedtime timestamp is persisted to SharedPreferences ("bios_sleep_tile")
 * so the tile survives system restarts of the host process. A
 * non-positive stored value means "not started"; on first tap we store
 * `System.currentTimeMillis()`, on second tap we read it, validate via
 * [ManualSleepEntry.validate], and clear it.
 *
 * Invalid sessions (e.g. accidental double-tap, wake-before-bedtime
 * clock skew) are dropped with a Log.i and a tile subtitle update —
 * never written, never poison the baseline.
 */
class SleepTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        renderTile()
    }

    override fun onClick() {
        super.onClick()
        val now = System.currentTimeMillis()
        val storedBedtime = readBedtime(applicationContext)
        if (storedBedtime <= 0L) {
            writeBedtime(applicationContext, now)
            Log.i(TAG, "tile started at $now")
            renderTile(activeBedtime = now)
            return
        }
        when (val result = ManualSleepEntry.validate(storedBedtime, now)) {
            is ManualSleepEntry.Result.Valid -> {
                clearBedtime(applicationContext)
                writeReading(result.range)
                Log.i(
                    TAG,
                    "tile stopped at $now; logged ${result.range.durationSeconds}s",
                )
                renderTile(subtitle = "Logged ${formatHours(result.range.durationSeconds)}")
            }
            is ManualSleepEntry.Result.Invalid -> {
                clearBedtime(applicationContext)
                Log.i(TAG, "tile stopped at $now; dropped: ${result.reason}")
                renderTile(subtitle = result.reason)
            }
            ManualSleepEntry.Result.NotStarted -> {
                // Unreachable given the early-return above, but the
                // sealed exhaustiveness keeps the contract honest.
                renderTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun writeReading(range: ManualSleepEntry.Range) {
        val context = applicationContext
        scope.launch {
            try {
                val repo = SleepEntryRepo(BiosDatabase.getInstance(context))
                repo.add(
                    durationSeconds = range.durationSeconds,
                    timestamp = range.wakeMs,
                )
            } catch (t: Throwable) {
                Log.w(TAG, "failed to persist owner-asserted sleep: ${t.message}", t)
            }
        }
    }

    private fun renderTile(
        activeBedtime: Long = readBedtime(applicationContext),
        subtitle: String? = null,
    ) {
        val tile = qsTile ?: return
        val active = activeBedtime > 0L
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Sleeping" else "Going to bed"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle ?: if (active) "Tap on wake" else "Tap to start"
        }
        tile.contentDescription =
            if (active) "Stop the manual sleep log" else "Start a manual sleep log"
        tile.updateTile()
    }

    private fun formatHours(seconds: Long): String {
        val h = seconds / 3600L
        val m = (seconds % 3600L) / 60L
        return when {
            h > 0L -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    companion object {
        private const val TAG = "SleepTileService"
        private const val PREFS_NAME = "bios_sleep_tile"
        private const val KEY_BEDTIME_MS = "bedtime_ms"

        internal fun readBedtime(context: Context): Long =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_BEDTIME_MS, 0L)

        internal fun writeBedtime(context: Context, bedtimeMs: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BEDTIME_MS, bedtimeMs)
                .apply()
        }

        internal fun clearBedtime(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_BEDTIME_MS)
                .apply()
        }
    }
}
