package com.bios.app.physiology

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic worker (#183, SURGICAL_POV §2.1) that advances the
 * [PhysiologyStateStore] from [PhysiologyState.POD_0_30] →
 * [PhysiologyState.POD_30_90] → [PhysiologyState.POD_90_PLUS] based
 * on days-since-procedure.
 *
 * Reads the procedureDate from [PerioperativeStateStore], computes the
 * [PerioperativeStateStore.expectedState] for now, and writes it to
 * [PhysiologyStateStore] when it differs from the currently-set state.
 *
 * Only acts when the current state is one of the peri-op states — if
 * the owner has manually moved off (e.g. cleared their peri-op context),
 * the worker leaves them alone. Manifesto-clean: the worker never
 * decides to *enter* a peri-op state on the owner's behalf, only to
 * advance through the configured trajectory.
 *
 * Scheduled hourly (the resolution is days, but a 24-hour period would
 * mean a state transition could be up to 24h late at boundaries; 1h is
 * cheap and keeps the transition tight).
 */
class PerioperativeStateTransitionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val perioStore = PerioperativeStateStore(ctx)
        val expected = perioStore.expectedState() ?: return Result.success()

        val stateStore = PhysiologyStateStore(ctx)
        val current = stateStore.current()

        // Only advance through the peri-op envelope. If the owner has
        // moved themselves to STANDARD or any non-peri-op state, leave
        // them alone — they have opted out.
        if (current !in PhysiologyState.PERIOPERATIVE) return Result.success()

        if (current != expected) {
            stateStore.set(expected)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "bios_perioperative_state_transition"

        /** Enqueue the periodic worker. Idempotent — KEEP existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PerioperativeStateTransitionWorker>(
                1, TimeUnit.HOURS,
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
