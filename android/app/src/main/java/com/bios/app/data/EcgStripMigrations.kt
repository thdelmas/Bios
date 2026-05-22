package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration that creates the `ecg_strips` table (#188, CARDIOLOGY_POV
 * §2.8). Owner-captured single-lead ECG waveform + vendor classification.
 * BLOB-inline so the waveform stays inside Bios's single encryption envelope.
 */
internal object EcgStripMigrations {
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ecg_strips (
                    id TEXT NOT NULL PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    durationSeconds INTEGER NOT NULL,
                    samplingRateHz INTEGER NOT NULL,
                    leadPlacement TEXT NOT NULL,
                    samples BLOB NOT NULL,
                    voltageScale REAL NOT NULL,
                    voltageOffset REAL NOT NULL,
                    sampleEncoding TEXT NOT NULL,
                    classification TEXT,
                    sourceVendor TEXT NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_ecg_strips_timestamp ON ecg_strips (timestamp)")
        }
    }
}
