package com.bios.app.ingest

import android.content.Context
import android.util.Log
import androidx.work.*
import com.bios.app.data.BiosDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically syncs Health Connect data
 * and runs the detection pipeline.
 *
 * Battery strategy:
 * - Runs only when battery is not low
 * - Uses exponential backoff with a max of 3 retries
 * - Isolates pipeline stages so one failure doesn't block others
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRIES) {
            Log.w(TAG, "Max retries ($MAX_RETRIES) reached, giving up until next period")
            return Result.failure()
        }

        return try {
            val db = BiosDatabase.getInstance(applicationContext)
            val healthConnect = HealthConnectAdapter(applicationContext)
            val tokenStore = OuraTokenStore(applicationContext)
            val ouraAdapter = if (tokenStore.hasToken()) OuraApiAdapter(tokenStore) else null
            val apiTokenStore = ApiTokenStore(applicationContext)
            val withings = if (apiTokenStore.hasToken(WithingsApiAdapter.PROVIDER_KEY)) {
                WithingsApiAdapter(apiTokenStore)
            } else null
            val phoneSensor = PhoneSensorAdapter(applicationContext)
            val gadgetbridge = GadgetbridgeAdapter(applicationContext)
            val directSensor = DirectSensorAdapter(applicationContext)
            val ingestManager = IngestManager(
                healthConnect, db, ouraAdapter, phoneSensor,
                gadgetbridgeAdapter = gadgetbridge,
                directSensorAdapter = directSensor,
                withingsAdapter = withings
            )

            // Stage 1: Sync recent data
            ingestManager.syncRecentData()

            // Stage 2: Prune readings older than retention window
            try {
                val retentionMillis = RETENTION_DAYS.toLong() * 24 * 3600 * 1000
                db.metricReadingDao().deleteBefore(System.currentTimeMillis() - retentionMillis)
            } catch (e: Exception) {
                Log.w(TAG, "Retention pruning failed, continuing", e)
            }

            // Stage 3: Run baseline + detection if enough data
            if (ingestManager.dataAgeDays.value >= MINIMUM_DATA_DAYS) {
                val reproductiveReadingDao =
                    com.bios.app.data.ReproductiveDatabase.readingDaoOrNull(applicationContext)
                try {
                    val engine = com.bios.app.engine.BaselineEngine(
                        db, reproductiveReadingDao = reproductiveReadingDao
                    )
                    engine.computeAllBaselines()
                    engine.computeDailyAggregates()
                } catch (e: Exception) {
                    Log.w(TAG, "Baseline computation failed, continuing to detection", e)
                }

                try {
                    val mlModel = com.bios.app.engine.TFLiteAnomalyModel.load(applicationContext)
                    val physiologyState = com.bios.app.physiology.PhysiologyStateStore(applicationContext).current()
                    val ownerConditions = com.bios.app.physiology.OwnerConditionStore(applicationContext).current()
                    val regionConfig = com.bios.app.config.RegionConfigProvider.forCurrentLocale()
                    val detector = com.bios.app.engine.AnomalyDetector(
                        db, mlModel,
                        reproductiveReadingDao = reproductiveReadingDao,
                        physiologyState = physiologyState,
                        regionConfig = regionConfig,
                        ownerConditions = ownerConditions,
                    )
                    val newAnomalies = detector.runDetection()

                    val alertManager = com.bios.app.alerts.AlertManager(applicationContext, db)
                    for (anomaly in newAnomalies) {
                        alertManager.sendNotification(anomaly)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Anomaly detection failed", e)
                }
            }

            // Stage 4: System-state push — surface previously-active ingest
            // adapters that have gone silent past the per-source-type
            // staleness window. See alerts/DisconnectDetector.kt for the
            // category-3-push framing.
            try {
                val notifier = com.bios.app.alerts.DisconnectNotifier(applicationContext)
                val disconnectDetector = com.bios.app.alerts.DisconnectDetector(db)
                val alerts = disconnectDetector.findSourcesToPush(
                    lastPushedAtFor = { notifier.lastPushedAt(it) },
                    ownerEnabled = notifier.isEnabled(),
                )
                for (alert in alerts) {
                    notifier.notifyAndRecord(alert)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect-push stage failed", e)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BiosSyncWorker"
        const val WORK_NAME = "bios_sync"
        const val MINIMUM_DATA_DAYS = 7
        const val RETENTION_DAYS = 90
        const val MAX_RETRIES = 3
        const val STALE_THRESHOLD_HOURS = 2

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
