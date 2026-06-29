package com.bios.app.platform

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.bios.app.data.BiosDatabase
import com.bios.app.data.ReproductiveDatabase
import com.bios.app.ingest.OuraTokenStore
import com.bios.app.push.PushRegistrationManager
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

    /** cacheDir subfolder holding lab-report camera captures (see labocr/). */
    private const val LAB_SCAN_CACHE_DIR = "labocr"

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

        // 0.6. Unregister push and destroy push state
        PushRegistrationManager.destroyAll(context)

        // 0.7. Release every persistable URI permission we hold (lab-report
        // PDFs / photos attached to biomarker entries — see BiomarkerContext
        // .sourceUri). Done *before* the DB key is destroyed so the wipe is
        // observable from outside Bios too: source apps no longer see us in
        // their "shared with" list. The system would garbage-collect these
        // on next reboot anyway, but explicit release is the polite move.
        releaseAllPersistableUriPermissions(context)

        // 1. Destroy encryption key (makes DB unreadable even if file survives)
        destroyEncryptionKey(context)

        // 2. Delete database files
        destroyDatabase(context)

        // 3. Wipe API tokens
        destroyTokens(context)

        // 4. Delete export files from cache
        destroyExportFiles(context)

        // 4.5. Delete cached lab-report scan sources (camera captures the
        // owner photographed for OCR ingestion — see labocr/LabScanner). The
        // readings they produced die with the DB key above; this clears the
        // raw images too.
        destroyLabScanSources(context)

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
            // Destroy from encrypted store (current location)
            BiosDatabase.getEncryptedPrefs(context).edit().clear().commit()
            // Also clear plain store in case migration hadn't run yet
            context.getSharedPreferences("bios_secure", Context.MODE_PRIVATE)
                .edit().remove("db_passphrase").commit()
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
            // Withings (and any future provider stored in the generic
            // ApiTokenStore: WHOOP, Garmin, Dexcom) wipes in one call —
            // clearAll() drops every provider key in the encrypted prefs.
            com.bios.app.ingest.ApiTokenStore(context).clearAll()
            Log.d(TAG, "API tokens cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear API tokens", e)
        }
    }

    private fun destroyExportFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.filter { file ->
                val n = file.name
                // Every export artifact left in cache: the data dumps
                // (bios_export_*), FHIR bundles (bios_fhir_*), doctor PDFs
                // (bios_doctor_summary_*), and their encrypted ".zip" wrappers.
                // ".bios" is legacy (retired EncryptedExporter) — still purged if
                // a stray file from an older build lingers.
                (n.startsWith("bios_export_") || n.startsWith("bios_fhir_") ||
                    n.startsWith("bios_doctor_summary_")) &&
                    (n.endsWith(".json") || n.endsWith(".zip") ||
                        n.endsWith(".pdf") || n.endsWith(".bios"))
            }?.forEach { file ->
                file.delete()
            }
            Log.d(TAG, "Export files deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete export files", e)
        }
    }

    private fun destroyLabScanSources(context: Context) {
        try {
            File(context.cacheDir, LAB_SCAN_CACHE_DIR).deleteRecursively()
            Log.d(TAG, "Lab-scan source images deleted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete lab-scan sources", e)
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

    private fun releaseAllPersistableUriPermissions(context: Context) {
        try {
            val resolver = context.contentResolver
            val held = resolver.persistedUriPermissions
            for (perm in held) {
                val flags = (if (perm.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (perm.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                if (flags == 0) continue
                try {
                    resolver.releasePersistableUriPermission(perm.uri, flags)
                } catch (_: SecurityException) {
                    // Provider revoked the grant under us. Treat as already-released.
                }
            }
            Log.d(TAG, "Released ${held.size} persistable URI permission(s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release persistable URI permissions", e)
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
            // Clear plain SharedPreferences
            val plainPrefFiles = listOf(
                "bios_secure", "bios_settings", "bios_oura_credentials", "bios_api_credentials",
                "bios_repro_secure"
            )
            for (name in plainPrefFiles) {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            // Clear encrypted SharedPreferences (Keystore-backed)
            BiosDatabase.getEncryptedPrefs(context).edit().clear().commit()
            Log.d(TAG, "Preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear preferences", e)
        }
    }
}
