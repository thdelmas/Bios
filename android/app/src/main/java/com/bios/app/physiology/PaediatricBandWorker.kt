package com.bios.app.physiology

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily worker that recomputes the paediatric [PhysiologyState] band
 * from the stored birth date (#198). Idempotent: if the owner has not
 * supplied a birth date, the worker no-ops; if they have, the worker
 * sets the band that matches the current calendar date. The owner can
 * override the band manually at any time — the next worker run will
 * reset it to match the date, which is the intended behaviour (the
 * birth date is the source of truth when present).
 *
 * Mirrors the scheduling shape of [com.bios.app.alerts.DailyDigestWorker]:
 * unique periodic work, KEEP policy so the existing scheduled run wins
 * on re-launch, no constraints (the computation is pure-local + does
 * not need network).
 */
class PaediatricBandWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runOnce(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "bios_paediatric_band"

        /**
         * Recompute the band immediately and persist it if it differs
         * from what's already stored. Returns the resulting state for
         * callers that want to react in-process (e.g. the settings UI
         * after the owner taps "Save birth date"). When no birth date
         * is on file, returns the existing state untouched.
         */
        fun runOnce(
            context: Context,
            today: LocalDate = LocalDate.now(),
        ): PhysiologyState {
            val store = PhysiologyStateStore(context)
            val birthDate = store.birthDate() ?: return store.current()
            val band = PaediatricBandCalculator.bandFor(birthDate, today)
            if (band != store.current()) store.set(band)
            return band
        }

        /**
         * Schedule the daily recompute. Safe to call repeatedly — the
         * KEEP policy preserves the original schedule on subsequent
         * launches. Cancelled automatically by
         * [com.bios.app.platform.DataDestroyer] alongside everything
         * else when the owner taps "Delete all data".
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PaediatricBandWorker>(
                24, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
