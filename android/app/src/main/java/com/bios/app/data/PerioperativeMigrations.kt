package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration that creates the `perioperative_baseline` table (#205,
 * SURGICAL_POV §2.1). Stores the frozen pre-op baseline (RHR, HRV,
 * SkinTemp, RR) so post-op-windowed patterns can detect re-elevation
 * after normalisation. Wiped alongside everything else by [DataDestroyer].
 */
internal object PerioperativeMigrations {
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS perioperative_baseline (
                    id INTEGER NOT NULL PRIMARY KEY,
                    procedureName TEXT,
                    procedureDateMillis INTEGER,
                    restingHeartRate REAL,
                    heartRateVariability REAL,
                    skinTemperatureDeviation REAL,
                    respiratoryRate REAL,
                    frozenAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}
