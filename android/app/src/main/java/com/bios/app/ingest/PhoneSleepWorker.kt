package com.bios.app.ingest

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.bios.app.data.BiosDatabase
import com.bios.app.engine.PhoneSleepInference
import com.bios.app.model.DataSource
import com.bios.app.model.PhoneSleepSample
import com.bios.app.model.ReadingKind
import com.bios.app.model.SourceType
import com.bios.contracts.MetricType
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Phone-only sleep auto-derivation (issue #134).
 *
 * Every WorkManager firing:
 *
 *  1. Collects one [PhoneSleepInference.Sample] via [PhoneSleepAdapter]
 *     and appends it to `phone_sleep_samples`.
 *  2. Asks [PhoneSleepScheduler] whether we just crossed the morning
 *     trigger. If yes, reads the overnight buffer, hands it to
 *     [PhoneSleepInference.infer], writes the resulting
 *     `SLEEP_DURATION` (+ `SLEEP_STAGE`) rows under a registered
 *     `PHONE_SENSOR_DERIVED` data source, and prunes the buffer.
 *
 * Cadence is 15 minutes — WorkManager's minimum periodic interval and
 * good enough for the inference's 4 h minimum window + 5 min AWAKE
 * bout merging. Doze-mode delays will cost a sample or two but the
 * morning trigger still fires on the first post-wake-hour run.
 *
 * The worker self-installs idempotently via [enqueuePeriodicWork] so
 * adding it to AppViewModel's init path is enough to opt the user in.
 * No new permissions — accelerometer / light / charging / screen-state
 * are all unprivileged reads.
 */
class PhoneSleepWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val adapter = PhoneSleepAdapter(context)
        if (!adapter.isAvailable) {
            // No accelerometer → no inference possible. Succeed quietly so
            // WorkManager keeps the periodic schedule (cheaper than
            // re-enqueueing on every retry).
            Log.d(TAG, "Accelerometer unavailable; skipping sample")
            return Result.success()
        }

        val db = BiosDatabase.getInstance(context)
        val sampleDao = db.phoneSleepSampleDao()
        val readingDao = db.metricReadingDao()
        val sourceDao = db.dataSourceDao()

        return try {
            // 1. Sample + persist.
            val sample = adapter.sample()
            sampleDao.insert(
                PhoneSleepSample(
                    timestamp = sample.timestamp,
                    screenOff = sample.screenOff,
                    charging = sample.charging,
                    ambientLightLux = sample.ambientLightLux,
                    accelMagnitudeVar = sample.accelMagnitudeVar,
                )
            )

            // 2. Morning trigger? Bail early if not — keeps the common
            //    path (sample-only) cheap. Dedupe key is the most-recent
            //    phone-derived sleep midpoint; null on first run.
            val zone = ZoneId.systemDefault()
            val phoneSourceId = sourceDao.findByType(SourceType.PHONE_SENSOR_DERIVED.key)?.id
            val lastMidpoint = phoneSourceId?.let { sid ->
                readingDao.fetchLatest(MetricType.SLEEP_DURATION.key, limit = 5)
                    .firstOrNull { it.sourceId == sid }
                    ?.timestamp
            }
            val decision = PhoneSleepScheduler.decide(
                nowMs = System.currentTimeMillis(),
                zoneId = zone,
                lastInferredMidpointMs = lastMidpoint,
            )
            if (decision !is PhoneSleepScheduler.TriggerDecision.Fire) {
                return Result.success()
            }

            // 3. Inference window — read buffered samples, run inference,
            //    write results under a stable PHONE_SENSOR_DERIVED source.
            val samples = sampleDao
                .fetchInRange(decision.windowStartMs, decision.windowEndMs)
                .map {
                    PhoneSleepInference.Sample(
                        timestamp = it.timestamp,
                        screenOff = it.screenOff,
                        charging = it.charging,
                        ambientLightLux = it.ambientLightLux,
                        accelMagnitudeVar = it.accelMagnitudeVar,
                    )
                }
            if (samples.size < MIN_SAMPLES_FOR_INFERENCE) {
                Log.d(TAG, "Only ${samples.size} samples in window; skipping inference")
                return Result.success()
            }

            val sourceId = ensurePhoneSleepSource(sourceDao)
            val readings = adapter.infer(samples, sourceId)
            if (readings.isNotEmpty()) {
                readingDao.insertAll(readings)
                Log.i(TAG, "Inferred phone sleep: ${readings.size} readings written")
            }

            // 4. Prune old samples so the buffer can't grow unbounded.
            sampleDao.deleteOlderThan(
                PhoneSleepScheduler.pruneCutoffMs(System.currentTimeMillis())
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "PhoneSleepWorker failed: ${t.message}", t)
            Result.retry()
        }
    }

    private suspend fun ensurePhoneSleepSource(
        dao: com.bios.app.data.dao.DataSourceDao,
    ): String {
        val existing = dao.findByType(SourceType.PHONE_SENSOR_DERIVED.key)
        if (existing != null) return existing.id
        val source = DataSource(
            sourceType = SourceType.PHONE_SENSOR_DERIVED.key,
            sensorType = "phone_sleep",
            deviceName = "Phone sleep inference",
            readingKind = ReadingKind.DERIVED.name,
        )
        dao.insert(source)
        return source.id
    }

    companion object {
        private const val TAG = "PhoneSleepWorker"
        const val WORK_NAME = "bios_phone_sleep"
        /** Below this many samples the inference can't honestly fit
         *  a 4 h minimum window even at the 15-min cadence. */
        internal const val MIN_SAMPLES_FOR_INFERENCE = 8

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<PhoneSleepWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
