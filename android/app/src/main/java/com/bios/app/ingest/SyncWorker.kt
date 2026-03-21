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
            val ingestManager = IngestManager(healthConnect, db)

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
                try {
                    val engine = com.bios.app.engine.BaselineEngine(db)
                    engine.computeAllBaselines()
                    engine.computeDailyAggregates()
                } catch (e: Exception) {
                    Log.w(TAG, "Baseline computation failed, continuing to detection", e)
                }

                try {
                    val detector = com.bios.app.engine.AnomalyDetector(db)
                    val newAnomalies = detector.runDetection()

                    val alertManager = com.bios.app.alerts.AlertManager(applicationContext, db)
                    for (anomaly in newAnomalies) {
                        alertManager.sendNotification(anomaly)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Anomaly detection failed", e)
                }
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
