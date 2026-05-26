package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration that creates the `perioperative_baselines` table (#205,
 * SURGICAL_POV §2.1). Stores the frozen pre-op baseline (RHR/HRV/SkinTemp/
 * RR/SpO2/sleep/steps/body-mass means + SDs) so post-op-windowed patterns
 * can detect re-elevation after normalisation. Wiped alongside everything
 * else by [DataDestroyer].
 */
internal object PerioperativeMigrations {
    private const val CREATE_PERIOPERATIVE_BASELINES =
        """
        CREATE TABLE IF NOT EXISTS perioperative_baselines (
            id INTEGER NOT NULL PRIMARY KEY,
            procedureDate INTEGER NOT NULL,
            procedureTag TEXT,
            snapshotAt INTEGER NOT NULL,
            restingHeartRateMean REAL,
            restingHeartRateSd REAL,
            heartRateVariabilityMean REAL,
            heartRateVariabilitySd REAL,
            skinTemperatureMean REAL,
            skinTemperatureSd REAL,
            respiratoryRateMean REAL,
            respiratoryRateSd REAL,
            bloodOxygenMean REAL,
            bloodOxygenSd REAL,
            sleepDurationMean REAL,
            sleepDurationSd REAL,
            sleepEfficiencyMean REAL,
            sleepEfficiencySd REAL,
            stepsMean REAL,
            stepsSd REAL,
            bodyMassMean REAL,
            bodyMassSd REAL
        )
        """

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_PERIOPERATIVE_BASELINES.trimIndent())
        }
    }

    // Repairs installs that ran the original (broken) MIGRATION_22_23 — it
    // created `perioperative_baseline` (singular) with a stale schema that
    // never matched the entity. The DAO points at `perioperative_baselines`
    // (plural), so the old table was unreachable; dropping it loses nothing
    // an engine ever read. Creating the plural table makes Room's schema
    // validator happy on devices already past v23.
    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS perioperative_baseline")
            db.execSQL(CREATE_PERIOPERATIVE_BASELINES.trimIndent())
        }
    }
}
