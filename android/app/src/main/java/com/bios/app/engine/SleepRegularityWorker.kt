package com.bios.app.engine

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bios.app.data.BiosDatabase
import com.bios.app.model.DataSource
import com.bios.app.model.ReadingKind
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import java.util.concurrent.TimeUnit

/**
 * Nightly worker that persists [MetricType.SLEEP_REGULARITY] readings
 * derived by [SleepRegularityCalculator] (#135 deliverable 2 — the
 * gap between the calculator being ready and actual rows landing on
 * the bus).
 *
 * Before this worker, `derive()` was only called inline from
 * [com.bios.app.ui.sleep.SleepDashboardScreen] as an on-demand
 * preview — the result was never persisted, so no downstream
 * consumer (trending, alerts, future condition patterns crossing
 * sleep ↔ HRV / glucose / mood) could read SLEEP_REGULARITY from
 * `metric_readings`. The worker closes that loop.
 *
 * ## Cadence
 *
 * Daily. Sleep regularity is a chronic-pattern metric — a single
 * extra night doesn't move the rolling 7-day window in any
 * clinically interesting way, and a more frequent cadence would
 * burn battery without earning information. Mirrors
 * [com.bios.app.alerts.MohScreeningWorker] scheduling shape.
 *
 * ## No alert surface
 *
 * SLEEP_REGULARITY is a pull-side metric per #135 manifesto:
 * no notification, no nudge, no score-as-judgment surface. The worker's
 * single output is the `metric_readings` row + its sidecar payload.
 * The owner reads it from the dashboard (or a future trend / detail
 * surface); evaluation belongs to the owner.
 */
class SleepRegularityWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = BiosDatabase.getInstance(context)
        val readingDao = db.metricReadingDao()
        val payloadDao = db.eventPayloadFieldDao()
        val sourceDao = db.dataSourceDao()

        return try {
            val now = System.currentTimeMillis()
            // The calculator filters internally to the window; we fetch
            // a comfortably-wider span (2× the window) so a delayed
            // worker firing doesn't truncate.
            val fetchStart = now -
                (SleepRegularityCalculator.DEFAULT_WINDOW_DAYS * 2L * MS_PER_DAY)
            val sleepDurations = readingDao.fetch(
                MetricType.SLEEP_DURATION.key, fetchStart, now,
            )
            val sourceId = ensureSource(sourceDao)
            val result = SleepRegularityCalculator.derive(
                sleepDurations = sleepDurations,
                sourceId = sourceId,
                nowMs = now,
            )
            if (result == null) {
                Log.i(TAG, "insufficient nights for derivation; skipping")
                return Result.success()
            }
            readingDao.insert(result.reading)
            payloadDao.insertAll(result.payload)
            Log.i(
                TAG,
                "wrote SLEEP_REGULARITY reading id=${result.reading.id} " +
                    "score=${result.reading.value}",
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "SleepRegularityWorker failed: ${t.message}", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SleepRegularityWorker"
        const val WORK_NAME = "bios_sleep_regularity"
        private const val MS_PER_DAY: Long = 24L * 60L * 60L * 1000L

        /** Stable deviceName for the DataSource row that owns the
         *  derived SLEEP_REGULARITY rows. Distinct from the phone-
         *  sleep inference source so provenance reads cleanly. */
        const val SOURCE_DEVICE_NAME = "Sleep regularity"

        /** Daily cadence. Idempotent via WorkManager unique-work
         *  policy. The worker is a no-op when fewer than the minimum
         *  nights are available, so it's safe to enqueue
         *  unconditionally from app init. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SleepRegularityWorker>(
                24, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        internal suspend fun ensureSource(dao: com.bios.app.data.dao.DataSourceDao): String {
            val existing = dao.findByTypeAndDeviceName(
                SourceType.PHONE_SENSOR_DERIVED.key,
                SOURCE_DEVICE_NAME,
            )
            if (existing != null) return existing.id
            val source = DataSource(
                sourceType = SourceType.PHONE_SENSOR_DERIVED.key,
                sensorType = "sleep_regularity",
                deviceName = SOURCE_DEVICE_NAME,
                readingKind = ReadingKind.DERIVED.name,
            )
            dao.insert(source)
            return source.id
        }
    }
}
