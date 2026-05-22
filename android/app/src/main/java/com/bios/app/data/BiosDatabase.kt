package com.bios.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bios.app.data.dao.*
import com.bios.app.model.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MetricReading::class,
        DataSource::class,
        PersonalBaseline::class,
        ComputedAggregate::class,
        Anomaly::class,
        HealthEvent::class,
        ActionItem::class,
        UserFeedback::class,
        ProfessionalReview::class,
        CompanionGrant::class,
        LoggedEvent::class,
        EventPayloadField::class,
        PhoneSleepSample::class,
        MedicationAnnotation::class,
        ImmunizationRecord::class,
        ScreeningEntry::class,
        RiskProfile::class,
        GoalsOfCare::class,
        ClinicalDirective::class,
        MigraineAttack::class,
        HeadacheLog::class,
        FastStrokeEvent::class,
        EsasReport::class,
        TraditionalMedicineContext::class,
        GrowthMeasurement::class,
    ],
    version = 25,
    exportSchema = false
)
@androidx.room.TypeConverters(MigraineTriggerConverter::class)
abstract class BiosDatabase : RoomDatabase() {

    abstract fun metricReadingDao(): MetricReadingDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun computedAggregateDao(): ComputedAggregateDao
    abstract fun anomalyDao(): AnomalyDao
    abstract fun healthEventDao(): HealthEventDao
    abstract fun actionItemDao(): ActionItemDao
    abstract fun userFeedbackDao(): UserFeedbackDao
    abstract fun professionalReviewDao(): ProfessionalReviewDao
    abstract fun companionGrantDao(): CompanionGrantDao
    abstract fun loggedEventDao(): LoggedEventDao
    abstract fun eventPayloadFieldDao(): EventPayloadFieldDao
    abstract fun phoneSleepSampleDao(): PhoneSleepSampleDao
    abstract fun medicationAnnotationDao(): MedicationAnnotationDao
    abstract fun immunizationRecordDao(): ImmunizationRecordDao
    abstract fun screeningEntryDao(): ScreeningEntryDao
    abstract fun riskProfileDao(): RiskProfileDao
    abstract fun goalsOfCareDao(): GoalsOfCareDao
    abstract fun clinicalDirectiveDao(): ClinicalDirectiveDao
    abstract fun migraineAttackDao(): MigraineAttackDao
    abstract fun headacheLogDao(): HeadacheLogDao
    abstract fun fastStrokeEventDao(): FastStrokeEventDao
    abstract fun esasReportDao(): EsasReportDao
    abstract fun traditionalMedicineContextDao(): TraditionalMedicineContextDao
    abstract fun growthMeasurementDao(): GrowthMeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: BiosDatabase? = null

        fun getInstance(context: Context): BiosDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): BiosDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                BiosDatabase::class.java,
                "bios.db"
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MedicationVocabularyMigration.MIGRATION_19_20, MigrationsAnthropometry.MIGRATION_24_25)
                // Downgrades wipe (encrypted-prefs passphrase survives) rather than crash —
                // local DB is a cache; sources re-populate on next sync.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        private const val PREFS_NAME = "bios_secure"
        private const val ENCRYPTED_PREFS_NAME = "bios_secure_encrypted"
        private const val KEY_PASSPHRASE = "db_passphrase"

        /**
         * Retrieves or generates the database encryption key.
         * The key is stored in EncryptedSharedPreferences, backed by Android Keystore
         * (AES-256-GCM via hardware-backed MasterKey where available).
         *
         * On first run after upgrade, migrates the passphrase from plain SharedPreferences
         * to EncryptedSharedPreferences, then clears the plaintext copy.
         */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val encryptedPrefs = getEncryptedPrefs(context)

            // Check encrypted store first
            val encrypted = encryptedPrefs.getString(KEY_PASSPHRASE, null)
            if (encrypted != null) {
                return encrypted.toByteArray(Charsets.UTF_8)
            }

            // Migrate from plain SharedPreferences if present (existing installs)
            val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val plain = plainPrefs.getString(KEY_PASSPHRASE, null)
            if (plain != null) {
                encryptedPrefs.edit().putString(KEY_PASSPHRASE, plain).apply()
                plainPrefs.edit().remove(KEY_PASSPHRASE).commit()
                return plain.toByteArray(Charsets.UTF_8)
            }

            // Fresh install — generate a random passphrase
            val passphrase = java.util.UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            return passphrase.toByteArray(Charsets.UTF_8)
        }

        internal fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anomalies ADD COLUMN feedbackAt INTEGER")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN feltSick INTEGER")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN visitedDoctor INTEGER")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN diagnosis TEXT")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN symptoms TEXT")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE anomalies ADD COLUMN outcomeAccurate INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS health_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        anomalyId TEXT,
                        parentEventId TEXT
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_events_createdAt ON health_events(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_events_type ON health_events(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_events_status ON health_events(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_events_anomalyId ON health_events(anomalyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_events_parentEventId ON health_events(parentEventId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS action_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        healthEventId TEXT NOT NULL,
                        description TEXT NOT NULL,
                        dueAt INTEGER,
                        completed INTEGER NOT NULL,
                        completedAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_items_healthEventId ON action_items(healthEventId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_items_completed ON action_items(completed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_action_items_dueAt ON action_items(dueAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_feedback (
                        id TEXT NOT NULL PRIMARY KEY,
                        surface TEXT NOT NULL,
                        surfaceItemId TEXT,
                        rating INTEGER NOT NULL,
                        comment TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_feedback_surface ON user_feedback(surface)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_feedback_createdAt ON user_feedback(createdAt)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS companion_grants (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        state TEXT NOT NULL,
                        firstSeenAt INTEGER NOT NULL,
                        grantedAt INTEGER,
                        revokedAt INTEGER,
                        lastAccessAt INTEGER,
                        accessCount INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_companion_grants_state ON companion_grants(state)")
            }
        }

        // Existing rows back-fill to SENSOR: every pre-v7 source is either a
        // sensor adapter (Health Connect, Gadgetbridge, camera PPG) or a
        // companion writer. Companion writers will be re-tagged at next
        // ensureSourceFor() call (REPLACE strategy), so the SENSOR default is
        // only load-bearing until the first companion write after upgrade.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE data_sources ADD COLUMN readingKind TEXT NOT NULL DEFAULT 'SENSOR'"
                )
            }
        }

        // Parallel table for event-shaped data (substance use, falls,
        // symptom episodes). Schema mirrors the sketch in
        // docs/SELF_REPORTED_DATA_HOME.md decision 2. Existing data is
        // left alone — Smokeless's `MetricReading.value=1.0` rows stay
        // where they are; the migration of legacy data is its own PR
        // and depends on the still-open question in the doc.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS logged_events (
                        id TEXT NOT NULL PRIMARY KEY,
                        eventType TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        severity INTEGER,
                        durationMs INTEGER,
                        value REAL,
                        sourceId TEXT NOT NULL,
                        packageName TEXT,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (sourceId) REFERENCES data_sources(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_logged_events_eventType_timestamp " +
                        "ON logged_events(eventType, timestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_logged_events_timestamp " +
                        "ON logged_events(timestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_logged_events_sourceId " +
                        "ON logged_events(sourceId)"
                )
            }
        }

        // Adds owner-recall `note` column to metric_readings. Never read by
        // any engine — the manifesto's "instrument, not coach" stance scopes
        // evaluation to the owner. Used by the biomarker entry surface today;
        // available to any future surface where the owner annotates a reading.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metric_readings ADD COLUMN note TEXT")
            }
        }

        // Sample buffer behind PhoneSleepWorker (#134). Holds ~96 rows per
        // night so PhoneSleepInference can run morning-trigger inference
        // from the overnight trace. Pruned by the worker after each
        // successful inference.
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS phone_sleep_samples (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        screenOff INTEGER NOT NULL,
                        charging INTEGER NOT NULL,
                        ambientLightLux REAL,
                        accelMagnitudeVar REAL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_phone_sleep_samples_timestamp
                    ON phone_sleep_samples (timestamp)
                """.trimIndent())
            }
        }

        // Owner-recorded current medications (#154).
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS medication_annotations (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER,
                        note TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_annotations_startDate ON medication_annotations (startDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medication_annotations_endDate ON medication_annotations (endDate)")
            }
        }

        // Owner-recorded immunisation history (#156).
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS immunization_records (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        vaccineName TEXT NOT NULL,
                        cvxCode TEXT,
                        occurrenceDate INTEGER NOT NULL,
                        doseNumber INTEGER,
                        lotNumber TEXT,
                        note TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_immunization_records_occurrenceDate ON immunization_records (occurrenceDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_immunization_records_cvxCode ON immunization_records (cvxCode)")
            }
        }

        // Owner-recorded screening history (#155). Renamed from
        // MIGRATION_11_12 → MIGRATION_13_14 on rebase because #154 and
        // #156 took the 11→12 and 12→13 slots first.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS screening_entries (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        screeningKey TEXT NOT NULL,
                        performedDate INTEGER NOT NULL,
                        note TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screening_entries_screeningKey ON screening_entries (screeningKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_screening_entries_performedDate ON screening_entries (performedDate)")
            }
        }

        // Owner-recorded risk profile (#160). Single-row table — the
        // screening-cadence engine and pattern-explanation builder read
        // this surface to compute risk-adjusted cadences and contextualise
        // alerts. Pull-side only; never pushed.
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS risk_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        firstDegreeCadEarly INTEGER NOT NULL,
                        firstDegreeBreastOvarianCancer INTEGER NOT NULL,
                        firstDegreeColorectalCancer INTEGER NOT NULL,
                        firstDegreeDiabetes INTEGER NOT NULL,
                        firstDegreeOsteoporosisHipFracture INTEGER NOT NULL,
                        firstDegreeMelanoma INTEGER NOT NULL,
                        personalTobaccoYears INTEGER,
                        personalTobaccoPackYears INTEGER,
                        personalTobaccoQuitDate INTEGER,
                        personalCancerHistory TEXT,
                        personalCardiacEventHistory TEXT,
                        note TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Owner-declared goals of care + clinical-directive metadata
        // (#184) goals_of_care + clinical_directive: owner standing preferences
        // (CPR / hospitalisation / intervention level) + advance-directive metadata.
        // AlertManager reads these to short-circuit URGENT escalation under COMFORT_ONLY.
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goals_of_care (
                        id INTEGER NOT NULL PRIMARY KEY,
                        cprPreference TEXT NOT NULL,
                        hospitalizationPreference TEXT NOT NULL,
                        interventionLevel TEXT NOT NULL,
                        lastReviewedAt INTEGER NOT NULL,
                        documentLocation TEXT,
                        notes TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS clinical_directive (
                        id INTEGER NOT NULL PRIMARY KEY,
                        hasAdvanceDirective INTEGER NOT NULL,
                        hasPolst INTEGER NOT NULL,
                        hasHealthcareProxy INTEGER NOT NULL,
                        proxyContactName TEXT,
                        proxyContactPhone TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Sidecar table for composite event payloads. Each row is one field
        // of a structured event attached to a parent MetricReading row.
        // See docs/DATA_MODEL.md for the field-key vocabulary.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS event_payloads (
                        readingId TEXT NOT NULL,
                        fieldKey TEXT NOT NULL,
                        stringValue TEXT,
                        doubleValue REAL,
                        longValue INTEGER,
                        PRIMARY KEY (readingId, fieldKey),
                        FOREIGN KEY (readingId) REFERENCES metric_readings(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_event_payloads_readingId " +
                        "ON event_payloads(readingId)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS professional_reviews (
                        id TEXT NOT NULL PRIMARY KEY,
                        anomalyId TEXT NOT NULL,
                        requestedAt INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        shareMethod TEXT,
                        sharedMetrics TEXT,
                        sharedWindowDays INTEGER,
                        sharedExplanation INTEGER NOT NULL,
                        sharedBaselines INTEGER NOT NULL,
                        respondedAt INTEGER,
                        professionalNotes TEXT,
                        clinicallyRelevant INTEGER,
                        recommendation TEXT,
                        ownerFoundHelpful INTEGER,
                        FOREIGN KEY (anomalyId) REFERENCES anomalies(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_anomalyId ON professional_reviews(anomalyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_status ON professional_reviews(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_requestedAt ON professional_reviews(requestedAt)")
            }
        }

        // Owner-PRO migrations live in sibling files to keep this one under 500 lines.
        private val MIGRATION_16_17 = NeurologyMigrations.MIGRATION_16_17
        private val MIGRATION_17_18 = EsasMigrations.MIGRATION_17_18
        private val MIGRATION_18_19 = TraditionalMedicineMigrations.MIGRATION_18_19

        /** In-memory instance for testing. */
        fun buildInMemory(context: Context): BiosDatabase {
            return Room.inMemoryDatabaseBuilder(context.applicationContext, BiosDatabase::class.java).build()
        }
    }
}
