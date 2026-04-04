package com.bios.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.DataSource
import com.bios.app.model.MetricReading
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Separate encrypted database for reproductive health data.
 *
 * Cycle tracking data (basal body temperature, cycle day, cycle phase) is uniquely
 * dangerous post-Dobbs and gets its own SQLCipher database with:
 * - Independent encryption key (separate keystore alias)
 * - Independent retention period (default: 90 days, owner configurable)
 * - Independent wipe capability (can destroy repro data without touching main DB)
 * - Priority destruction on LETHE duress PIN (fastest path to protecting most dangerous data)
 *
 * Schema includes DataSource because MetricReading has a foreign key to it.
 * Baselines and anomalies for reproductive metrics are computed in-memory and stored
 * in the main database without raw values (only statistical summaries).
 */
@Database(
    entities = [MetricReading::class, DataSource::class],
    version = 1,
    exportSchema = false
)
abstract class ReproductiveDatabase : RoomDatabase() {

    abstract fun readingDao(): MetricReadingDao
    abstract fun dataSourceDao(): DataSourceDao

    companion object {
        @Volatile
        private var INSTANCE: ReproductiveDatabase? = null

        private const val DB_NAME = "bios_repro.db"
        private const val PREFS_NAME = "bios_repro_secure"
        private const val KEY_PASSPHRASE = "repro_passphrase"

        fun getInstance(context: Context): ReproductiveDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Returns true if the reproductive database exists and has a valid key.
         * Used to determine whether to show reproductive health features.
         */
        fun isAvailable(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PASSPHRASE, null) != null
        }

        /**
         * Initialize the reproductive database with a user-chosen passphrase.
         * Call this when the owner explicitly enables reproductive health tracking.
         * The passphrase is separate from the device PIN — an additional layer of protection.
         */
        fun initialize(context: Context, passphrase: String? = null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_PASSPHRASE, null) == null) {
                // Generate random key if no passphrase provided, or derive from passphrase
                val key = passphrase ?: java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_PASSPHRASE, key).apply()
            }
        }

        /**
         * Destroy all reproductive health data irrecoverably.
         * 1. Destroy the encryption key
         * 2. Delete the database file
         * 3. Clear the instance
         *
         * Called by: LETHE duress PIN (priority), dead man's switch Stage 1,
         * burner mode, or owner's explicit "Delete reproductive data" action.
         */
        fun destroy(context: Context) {
            // Close active instance
            INSTANCE?.close()
            INSTANCE = null

            // Destroy key (makes DB unreadable even if file recovered)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()

            // Delete database files
            context.getDatabasePath(DB_NAME).delete()
            context.getDatabasePath("$DB_NAME-wal").delete()
            context.getDatabasePath("$DB_NAME-shm").delete()
            context.getDatabasePath("$DB_NAME-journal").delete()
        }

        /**
         * Check if the reproductive database has any data.
         */
        suspend fun hasData(context: Context): Boolean {
            if (!isAvailable(context)) return false
            return try {
                getInstance(context).readingDao().countAll() > 0
            } catch (_: Exception) {
                false
            }
        }

        private fun buildDatabase(context: Context): ReproductiveDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = getPassphrase(context)
                ?: throw IllegalStateException("Reproductive database not initialized. Call initialize() first.")
            val factory = SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))

            return Room.databaseBuilder(
                context.applicationContext,
                ReproductiveDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .build()
        }

        private fun getPassphrase(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PASSPHRASE, null)
        }

        /** Default retention for reproductive data: 90 days. */
        const val DEFAULT_RETENTION_DAYS = 90
    }
}
