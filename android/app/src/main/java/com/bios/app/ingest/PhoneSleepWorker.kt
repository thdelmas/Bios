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

    /** Outcome of [Companion.runImmediateInference]. Surfaced to the
     *  dashboard so the owner gets a concrete reason when no reading
     *  lands from a force-now request. */
    sealed interface ImmediateResult {
        object NoSensor : ImmediateResult
        object NotEnoughSamples : ImmediateResult
        object NoSleepWindow : ImmediateResult
        data class Written(val count: Int) : ImmediateResult
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val adapter = PhoneSleepAdapter(context)
        if (!adapter.isAvailable) {
            Log.i(TAG, "diag: no accelerometer; skipping firing")
            return Result.success()
        }

        val db = BiosDatabase.getInstance(context)
        val sampleDao = db.phoneSleepSampleDao()
        val readingDao = db.metricReadingDao()
        val sourceDao = db.dataSourceDao()

        return try {
            val sample = adapter.sample()
            sampleDao.insert(
                PhoneSleepSample(
                    timestamp = sample.timestamp,
                    screenOff = sample.screenOff,
                    charging = sample.charging,
                    ambientLightLux = sample.ambientLightLux,
                    accelMagnitudeVar = sample.accelMagnitudeVar,
                    stepDelta = sample.stepDelta,
                    dndEnabled = sample.dndEnabled,
                    pairedBluetoothConnected = sample.pairedBluetoothConnected,
                )
            )
            // One-line per-firing diagnostic so the failure mode is
            // observable from `adb logcat -s PhoneSleepWorker` without
            // having to decrypt SQLCipher rows. See #240.
            val quiet = PhoneSleepInference.isQuietSample(sample)
            Log.i(
                TAG,
                "diag: sample screenOff=${sample.screenOff} " +
                    "charging=${sample.charging} " +
                    "lux=${sample.ambientLightLux} " +
                    "accelVar=${sample.accelMagnitudeVar} " +
                    "quiet=$quiet"
            )

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
                Log.i(TAG, "diag: decision=Skip (preWakeHour or alreadyInferred)")
                return Result.success()
            }

            val samples = sampleDao
                .fetchInRange(decision.windowStartMs, decision.windowEndMs)
                .map {
                    PhoneSleepInference.Sample(
                        timestamp = it.timestamp,
                        screenOff = it.screenOff,
                        charging = it.charging,
                        ambientLightLux = it.ambientLightLux,
                        accelMagnitudeVar = it.accelMagnitudeVar,
                        stepDelta = it.stepDelta,
                        dndEnabled = it.dndEnabled,
                        pairedBluetoothConnected = it.pairedBluetoothConnected,
                    )
                }
            val breakdown = PhoneSleepInference.signalBreakdown(samples)
            val longestQuietMin = PhoneSleepInference.longestScreenOffMs(samples) / 60_000L
            Log.i(
                TAG,
                "diag: decision=Fire window=${decision.windowStartMs}..${decision.windowEndMs} " +
                    "samples=${samples.size} breakdown=$breakdown " +
                    "longestQuietMin=$longestQuietMin"
            )
            if (samples.size < MIN_SAMPLES_FOR_INFERENCE) {
                Log.i(TAG, "diag: ${samples.size} samples < $MIN_SAMPLES_FOR_INFERENCE; skip infer")
                return Result.success()
            }

            val sourceId = ensureSource(sourceDao)
            val readings = adapter.infer(samples, sourceId)
            if (readings.isNotEmpty()) {
                readingDao.insertAll(readings)
                Log.i(TAG, "diag: inferred readings=${readings.size}")
            } else {
                Log.i(TAG, "diag: inferred readings=0 (no viable window in buffer)")
            }

            sampleDao.deleteOlderThan(
                PhoneSleepScheduler.pruneCutoffMs(System.currentTimeMillis())
            )
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "PhoneSleepWorker failed: ${t.message}", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "PhoneSleepWorker"
        const val WORK_NAME = "bios_phone_sleep"
        /** Below this many samples the inference can't honestly fit
         *  a 4 h minimum window even at the 15-min cadence. */
        internal const val MIN_SAMPLES_FOR_INFERENCE = 8

        /** Wider window for the on-demand "Infer now" path: covers up
         *  to ~1.5 days back so an owner who slept late, just installed,
         *  or had a DB wipe has a chance of producing a reading from a
         *  partial buffer instead of waiting until tomorrow morning. */
        internal const val FORCE_WINDOW_MS: Long = 36L * 60L * 60L * 1000L

        /**
         * On-demand inference path for owners who don't want to wait
         * until the periodic morning trigger fires at the next wake hour.
         * Takes one fresh sample, then runs inference over the last
         * [FORCE_WINDOW_MS] using whatever's in the buffer. Skips both
         * the morning-trigger gate and the lastMidpoint dedupe — the
         * owner asked for an inference, so produce one if the math
         * allows. Writes results under the same PHONE_SENSOR_DERIVED
         * source the periodic worker uses.
         */
        suspend fun runImmediateInference(context: Context): ImmediateResult {
            val adapter = PhoneSleepAdapter(context)
            if (!adapter.isAvailable) return ImmediateResult.NoSensor

            val db = BiosDatabase.getInstance(context)
            val sampleDao = db.phoneSleepSampleDao()
            val readingDao = db.metricReadingDao()
            val sourceDao = db.dataSourceDao()

            val fresh = adapter.sample()
            sampleDao.insert(
                PhoneSleepSample(
                    timestamp = fresh.timestamp,
                    screenOff = fresh.screenOff,
                    charging = fresh.charging,
                    ambientLightLux = fresh.ambientLightLux,
                    accelMagnitudeVar = fresh.accelMagnitudeVar,
                    stepDelta = fresh.stepDelta,
                    dndEnabled = fresh.dndEnabled,
                    pairedBluetoothConnected = fresh.pairedBluetoothConnected,
                )
            )

            val end = System.currentTimeMillis()
            val start = end - FORCE_WINDOW_MS
            val samples = sampleDao.fetchInRange(start, end).map {
                PhoneSleepInference.Sample(
                    timestamp = it.timestamp,
                    screenOff = it.screenOff,
                    charging = it.charging,
                    ambientLightLux = it.ambientLightLux,
                    accelMagnitudeVar = it.accelMagnitudeVar,
                    stepDelta = it.stepDelta,
                    dndEnabled = it.dndEnabled,
                    pairedBluetoothConnected = it.pairedBluetoothConnected,
                )
            }
            if (samples.size < MIN_SAMPLES_FOR_INFERENCE) {
                return ImmediateResult.NotEnoughSamples
            }

            val sourceId = ensureSource(sourceDao)
            val readings = adapter.infer(samples, sourceId)
            if (readings.isEmpty()) return ImmediateResult.NoSleepWindow
            readingDao.insertAll(readings)
            return ImmediateResult.Written(readings.size)
        }

        private suspend fun ensureSource(dao: com.bios.app.data.dao.DataSourceDao): String {
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
