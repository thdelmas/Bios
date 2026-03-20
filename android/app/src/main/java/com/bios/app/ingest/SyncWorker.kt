package com.bios.app.ingest

import android.content.Context
import androidx.work.*
import com.bios.app.data.BiosDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically syncs Health Connect data
 * and runs the detection pipeline.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = BiosDatabase.getInstance(applicationContext)
            val healthConnect = HealthConnectAdapter(applicationContext)
            val ingestManager = IngestManager(healthConnect, db)

            // Sync recent data
            ingestManager.syncRecentData()

            // Run baseline + detection if enough data
            if (ingestManager.dataAgeDays.value >= MINIMUM_DATA_DAYS) {
                val engine = com.bios.app.engine.BaselineEngine(db)
                engine.computeAllBaselines()
                engine.computeDailyAggregates()

                val detector = com.bios.app.engine.AnomalyDetector(db)
                val newAnomalies = detector.runDetection()

                // Send notifications for significant alerts
                val alertManager = com.bios.app.alerts.AlertManager(applicationContext, db)
                for (anomaly in newAnomalies) {
                    alertManager.sendNotification(anomaly)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "bios_sync"
        const val MINIMUM_DATA_DAYS = 7

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
