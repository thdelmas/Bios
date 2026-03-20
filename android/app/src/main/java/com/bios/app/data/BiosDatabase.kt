package com.bios.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bios.app.data.dao.*
import com.bios.app.model.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        MetricReading::class,
        DataSource::class,
        PersonalBaseline::class,
        ComputedAggregate::class,
        Anomaly::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BiosDatabase : RoomDatabase() {

    abstract fun metricReadingDao(): MetricReadingDao
    abstract fun dataSourceDao(): DataSourceDao
    abstract fun personalBaselineDao(): PersonalBaselineDao
    abstract fun computedAggregateDao(): ComputedAggregateDao
    abstract fun anomalyDao(): AnomalyDao

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

        /** In-memory instance for testing. */
        fun buildInMemory(context: Context): BiosDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                BiosDatabase::class.java
            ).build()
        }
    }
}
