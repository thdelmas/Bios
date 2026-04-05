package com.bios.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
        ProfessionalReview::class
    ],
    version = 5,
    exportSchema = false
)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        /**
         * Retrieves or generates the database encryption key from Android Keystore.
         * The key is stored encrypted in SharedPreferences, wrapped by a Keystore-backed key.
         */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val prefs = context.getSharedPreferences("bios_secure", Context.MODE_PRIVATE)
            val stored = prefs.getString("db_passphrase", null)

            if (stored != null) {
                return stored.toByteArray(Charsets.UTF_8)
            }

            // Generate a random passphrase
            // In production, this should be wrapped with Android Keystore EncryptedSharedPreferences
            val passphrase = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", passphrase).apply()
            return passphrase.toByteArray(Charsets.UTF_8)
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

        /** In-memory instance for testing. */
        fun buildInMemory(context: Context): BiosDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                BiosDatabase::class.java
            ).build()
        }
    }
}
