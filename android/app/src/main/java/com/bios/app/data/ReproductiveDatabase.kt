package com.bios.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import com.bios.app.data.dao.ContraceptionEntryDao
import com.bios.app.data.dao.DataSourceDao
import com.bios.app.data.dao.GenderAffirmingCareEntryDao
import com.bios.app.data.dao.MenopauseStageEntryDao
import com.bios.app.data.dao.MetricReadingDao
import com.bios.app.model.ContraceptionEntry
import com.bios.app.model.DataSource
import com.bios.app.model.GenderAffirmingCareEntry
import com.bios.app.model.MenopauseStageEntry
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
    entities = [
        MetricReading::class,
        DataSource::class,
        ContraceptionEntry::class,
        MenopauseStageEntry::class,
        GenderAffirmingCareEntry::class,
    ],
    version = 4,
    exportSchema = false
)
abstract class ReproductiveDatabase : RoomDatabase() {

    abstract fun readingDao(): MetricReadingDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun contraceptionEntryDao(): ContraceptionEntryDao
    abstract fun menopauseStageEntryDao(): MenopauseStageEntryDao
    abstract fun genderAffirmingCareEntryDao(): GenderAffirmingCareEntryDao

    companion object {
        @Volatile
        private var INSTANCE: ReproductiveDatabase? = null

        private const val DB_NAME = "bios_repro.db"
        private const val PLAIN_PREFS_NAME = "bios_repro_secure"
        private const val ENCRYPTED_PREFS_NAME = "bios_repro_secure_encrypted"
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
            // Check encrypted store first, then plain (pre-migration)
            val encrypted = getEncryptedPrefs(context).getString(KEY_PASSPHRASE, null)
            if (encrypted != null) return true
            val plain = context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PASSPHRASE, null)
            return plain != null
        }

        /**
         * Initialize the reproductive database with a user-chosen passphrase.
         * Call this when the owner explicitly enables reproductive health tracking.
         * The passphrase is separate from the device PIN — an additional layer of protection.
         */
        fun initialize(context: Context, passphrase: String? = null) {
            val encryptedPrefs = getEncryptedPrefs(context)
            if (encryptedPrefs.getString(KEY_PASSPHRASE, null) == null) {
                val key = passphrase ?: java.util.UUID.randomUUID().toString()
                encryptedPrefs.edit().putString(KEY_PASSPHRASE, key).apply()
            }
        }

        /**
         * Destroy all reproductive health data irrecoverably.
         * 1. Destroy the encryption key from both encrypted and plain stores
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

            // Destroy key from encrypted store
            getEncryptedPrefs(context).edit().clear().commit()
            // Also clear plain store in case migration hadn't run yet
            context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()

            // Delete database files
            context.getDatabasePath(DB_NAME).delete()
            context.getDatabasePath("$DB_NAME-wal").delete()
            context.getDatabasePath("$DB_NAME-shm").delete()
            context.getDatabasePath("$DB_NAME-journal").delete()
        }

        /**
         * Returns the reading DAO when the reproductive DB is initialized
         * and openable, otherwise `null`. Use this from engines (baseline,
         * anomaly detection) that should evaluate reproductive metrics
         * when the owner has enabled BBT tracking but cleanly degrade to
         * "no reproductive data" when they haven't. Catches the open path
         * so a corrupted keystore can't crash background sync.
         */
        fun readingDaoOrNull(context: Context): MetricReadingDao? {
            if (!isAvailable(context)) return null
            return try {
                getInstance(context).readingDao()
            } catch (e: Exception) {
                Log.w("ReproductiveDatabase", "readingDaoOrNull: open failed", e)
                null
            }
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        // Mirrors BiosDatabase.MIGRATION_9_10: MetricReading gained an
        // owner-recall `note` column when biomarker context capture landed
        // (#103, commit 323aa6a). The main DB got its migration; this one
        // didn't, so on-device reproductive DBs persisted at v1 fail to open
        // against the v2 entity schema with an identity-hash mismatch
        // ("Expected …, found …"), silently breaking baseline + anomaly
        // detection for reproductive metrics (BBT, CYCLE_PHASE, CYCLE_DAY).
        // The column is owner-recall free-text only; no engine reads it, so
        // the migration is a pure ALTER TABLE with no backfill.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metric_readings ADD COLUMN note TEXT")
            }
        }

        // Mirrors BiosDatabase MetricReadingMigrations.MIGRATION_30_31: the
        // shared MetricReading entity declares a UNIQUE INDEX on
        // (sourceId, metricType, timestamp) so re-syncs collapse into
        // in-place updates instead of growing parallel rows. The main DB
        // got the index via v30_31; this DB didn't, so on-device repro DBs
        // persisted at v2 fail Room's identity-hash check on first open
        // ("Expected …, found …") and BiosSyncWorker can't read BBT /
        // CYCLE_PHASE / CYCLE_DAY for baselines.
        //
        // Same two-step shape as the main-DB migration: pick-best dedupe
        // (confidence DESC, createdAt DESC, id ASC) then UNIQUE INDEX. No
        // destructive fallback — reproductive readings (BBT especially)
        // are owner-entered and not re-fetchable from any wearable.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM metric_readings
                    WHERE id NOT IN (
                        SELECT r1.id
                        FROM metric_readings r1
                        WHERE NOT EXISTS (
                            SELECT 1 FROM metric_readings r2
                            WHERE r2.sourceId   = r1.sourceId
                              AND r2.metricType = r1.metricType
                              AND r2.timestamp  = r1.timestamp
                              AND (
                                  r2.confidence > r1.confidence
                                  OR (r2.confidence = r1.confidence AND r2.createdAt > r1.createdAt)
                                  OR (r2.confidence = r1.confidence AND r2.createdAt = r1.createdAt AND r2.id < r1.id)
                              )
                        )
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                        index_metric_readings_sourceId_metricType_timestamp
                    ON metric_readings (sourceId, metricType, timestamp)
                    """.trimIndent()
                )
            }
        }

        // Reproductive completeness (#209): three new owner-annotation tables
        // (contraception, menopause stage, gender-affirming care) join the
        // reproductive DB. They're owner-recorded, independent of the
        // MetricReading stream, so no foreign keys; the migration is three
        // CREATE TABLEs + their indices. All columns are nullable where
        // [data class] defaults allow null so Room schema validation
        // matches the entity hash.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS contraception_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        method TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        notes TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_contraception_entries_startDate " +
                        "ON contraception_entries(startDate)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS menopause_stage_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        stage TEXT NOT NULL,
                        recordedDate INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_menopause_stage_entries_recordedDate " +
                        "ON menopause_stage_entries(recordedDate)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gender_affirming_care_entries (
                        id TEXT NOT NULL PRIMARY KEY,
                        hormoneType TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        dose TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_gender_affirming_care_entries_startDate " +
                        "ON gender_affirming_care_entries(startDate)"
                )
            }
        }

        private fun getPassphrase(context: Context): String? {
            val encryptedPrefs = getEncryptedPrefs(context)

            // Check encrypted store first
            val encrypted = encryptedPrefs.getString(KEY_PASSPHRASE, null)
            if (encrypted != null) return encrypted

            // Migrate from plain SharedPreferences if present (existing installs)
            val plainPrefs = context.getSharedPreferences(PLAIN_PREFS_NAME, Context.MODE_PRIVATE)
            val plain = plainPrefs.getString(KEY_PASSPHRASE, null)
            if (plain != null) {
                encryptedPrefs.edit().putString(KEY_PASSPHRASE, plain).apply()
                plainPrefs.edit().remove(KEY_PASSPHRASE).commit()
                return plain
            }

            return null
        }

        private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /** Default retention for reproductive data: 90 days. */
        const val DEFAULT_RETENTION_DAYS = 90
    }
}
