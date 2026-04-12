package com.bios.app.platform

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.bios.app.data.ReproductiveDatabase
import com.bios.app.ingest.OuraTokenStore
import com.bios.app.sync.p2p.P2PDiscovery
import java.io.File

/**
 * Irrecoverably destroys all Bios health data.
 *
 * Called by:
 * - LETHE wipe signals (burner mode, dead man's switch, panic, duress)
 * - "Delete all data" in Settings (standalone)
 *
 * Destruction order (fastest-to-destroy first):
 * 1. Destroy the database encryption key (renders DB unreadable instantly)
 * 2. Delete the database file
 * 3. Wipe OAuth tokens (Oura, future adapters)
 * 4. Delete any plaintext export files in app cache
 * 5. Cancel all pending background work
 * 6. Clear all notifications
 * 7. Clear app SharedPreferences
 *
 * After this method returns, no health data is recoverable on this device.
 */
object DataDestroyer {

    private const val TAG = "BiosDataDestroyer"

    /**
     * Destroy only reproductive health data.
     * Called by LETHE duress PIN as the fastest path to protecting the most dangerous data.
     */
    fun destroyReproductiveData(context: Context) {
        val startTime = System.currentTimeMillis()
        ReproductiveDatabase.destroy(context)
        val elapsed = System.currentTimeMillis() - startTime
        Log.w(TAG, "Reproductive data destroyed in ${elapsed}ms")
    }

    fun destroyAll(context: Context) {
        val startTime = System.currentTimeMillis()

        // 0. Reproductive data first (highest priority — most dangerous if exposed)
        ReproductiveDatabase.destroy(context)

        // 0.5. Destroy P2P sync identity, documents, and pairing data
        destroyP2PData(context)

        // 1. Destroy encryption key (makes DB unreadable even if file survives)
        destroyEncryptionKey(context)

        // 2. Delete database files
        destroyDatabase(context)

        // 3. Wipe API tokens
        destroyTokens(context)

        // 4. Delete export files from cache
        destroyExportFiles(context)

        // 5. Cancel all background work
        cancelAllWork(context)

        // 6. Clear notifications
        clearNotifications(context)

        // 7. Clear all preferences
        clearPreferences(context)

        val elapsed = System.currentTimeMillis() - startTime
        Log.w(TAG, "All Bios data destroyed in ${elapsed}ms")
    }

    private fun destroyEncryptionKey(context: Context) {
        try {
            val prefs = context.getSharedPreferences("bios_secure", Context.MODE_PRIVATE)
            prefs.edit().remove("db_passphrase").commit() // commit() is intentional — synchronous
            Log.d(TAG, "Encryption key destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy encryption key", e)
        }
    }

    private fun destroyDatabase(context: Context) {
        try {
            val dbFile = context.getDatabasePath("bios.db")
            val walFile = context.getDatabasePath("bios.db-wal")
            val shmFile = context.getDatabasePath("bios.db-shm")
            val journalFile = context.getDatabasePath("bios.db-journal")

            dbFile.delete()
            walFile.delete()
            shmFile.delete()
            journalFile.delete()

            Log.d(TAG, "Database files deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete database files", e)
        }
    }

    private fun destroyTokens(context: Context) {
        try {
            OuraTokenStore(context).clearToken()
            // Future adapters: WHOOP, Garmin, Withings, Dexcom token stores
            Log.d(TAG, "API tokens cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API tokens", e)
        }
    }

    private fun destroyExportFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter { file ->
                file.name.startsWith("bios_export_") &&
                    (file.name.endsWith(".json") || file.name.endsWith(".zip") || file.name.endsWith(".bios"))
            }?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "Export files deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete export files", e)
        }
    }

    private fun cancelAllWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWork()
            Log.d(TAG, "All background work cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel background work", e)
        }
    }

    private fun clearNotifications(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            notificationManager.cancelAll()
            Log.d(TAG, "Notifications cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear notifications", e)
        }
    }

    private fun destroyP2PData(context: Context) {
        try {
            // Destroy Iroh node data directory (identity, documents, blobs)
            val irohDir = File(context.filesDir, "iroh")
            irohDir.deleteRecursively()

            // Clear P2P sync timestamps and pairing data
            context.getSharedPreferences("bios_p2p_sync", Context.MODE_PRIVATE)
                .edit().clear().commit()
            context.getSharedPreferences("bios_p2p_devices", Context.MODE_PRIVATE)
                .edit().clear().commit()
            context.getSharedPreferences("bios_p2p_settings", Context.MODE_PRIVATE)
                .edit().clear().commit()

            Log.d(TAG, "P2P sync data destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy P2P data", e)
        }
    }

    private fun clearPreferences(context: Context) {
        try {
            val prefFiles = listOf("bios_secure", "bios_settings", "bios_oura_credentials", "bios_api_credentials")
            for (name in prefFiles) {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            Log.d(TAG, "Preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear preferences", e)
        }
    }
}
