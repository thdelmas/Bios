package com.bios.app.sync.p2p

import android.content.Context
import android.util.Log
import androidx.work.*
import com.bios.app.data.BiosDatabase
import com.bios.app.sync.SyncProtocol
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic P2P sync via Iroh/Willow.
 *
 * Runs every 15 minutes (matching the existing SyncWorker cadence).
 * For each paired device, performs a delta sync of all entity types.
 *
 * Battery-aware: requires network connectivity but runs regardless
 * of battery state. The Iroh node starts on-demand for each sync
 * cycle and the operation is lightweight (only deltas transmitted).
 */
class P2PSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // Check if P2P sync is enabled
        val p2pPrefs = context.getSharedPreferences("bios_p2p_settings", Context.MODE_PRIVATE)
        if (!p2pPrefs.getBoolean("enabled", false)) {
            return Result.success()
        }

        // Check if sync key is available
        val syncPrefs = context.getSharedPreferences("bios_sync", Context.MODE_PRIVATE)
        val passkey = syncPrefs.getString("sync_passkey", null) ?: return Result.success()
        val salt = syncPrefs.getString("sync_salt", null)?.toByteArray(Charsets.UTF_8)
            ?: return Result.success()
        val syncKey = SyncProtocol.deriveSyncKey(passkey.toByteArray(Charsets.UTF_8), salt)

        // Select best available transport: Iroh (cross-network) > Local (same WiFi)
        val irohNode = IrohNode(context)
        val transport: P2PTransport = if (irohNode.isAvailable) {
            irohNode
        } else {
            LocalNetworkTransport(context)
        }

        val db = BiosDatabase.getInstance(context)
        val adapter = WillowSyncAdapter(context, db, transport)
        val discovery = P2PDiscovery(context, transport)

        try {
            transport.start()

            val pairedDevices = discovery.getPairedDevices()
            if (pairedDevices.isEmpty()) {
                Log.d(TAG, "No paired devices, skipping P2P sync")
                return Result.success()
            }

            for (device in pairedDevices) {
                Log.i(TAG, "Syncing with ${device.displayName} (${device.documentId})")
                try {
                    adapter.syncAll(device.documentId, syncKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed with ${device.displayName}", e)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "P2P sync worker failed", e)
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } finally {
            syncKey.fill(0)
            transport.stop()
        }
    }

    companion object {
        private const val TAG = "P2PSyncWorker"
        private const val WORK_NAME = "bios_p2p_sync"
        private const val MAX_RETRIES = 3

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<P2PSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
