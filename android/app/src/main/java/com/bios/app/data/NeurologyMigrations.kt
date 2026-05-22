package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database schema migrations for the neurology owner-symptom-logging
 * tables (issue #207). Extracted from [BiosDatabase] to keep the
 * single-file size under the 500-line cap.
 *
 * Three structured tables, all owner-administered:
 *  - **migraine_attacks** — closes audit gap §2.6. Migraine-specific
 *    shape (aura, triggers, peak intensity, acute treatment).
 *  - **headache_logs** — closes audit gap §2.5. Generic shape for
 *    tension / cluster / unclassified headache entries.
 *  - **fast_stroke_events** — closes audit gap §2.15. Owner-input only
 *    AHA/ASA FAST screen records.
 *
 * The medication-overuse-headache evaluator reads migraine_attacks and
 * headache_logs over a rolling 90-day window; the FHIR exporter ships
 * positive fast_stroke_events as Flag observations. No automated
 * inference path writes to any of these tables.
 */
internal object NeurologyMigrations {

    val MIGRATION_16_17: Migration = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS migraine_attacks (
                    id TEXT NOT NULL PRIMARY KEY,
                    onsetTimestamp INTEGER NOT NULL,
                    endTimestamp INTEGER,
                    peakIntensity INTEGER NOT NULL,
                    aura INTEGER NOT NULL,
                    triggers TEXT NOT NULL,
                    medicationTaken TEXT,
                    notes TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_migraine_attacks_onsetTimestamp " +
                    "ON migraine_attacks (onsetTimestamp)"
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS headache_logs (
                    id TEXT NOT NULL PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    intensity INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    durationMinutes INTEGER,
                    medicationTaken TEXT,
                    notes TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_headache_logs_timestamp " +
                    "ON headache_logs (timestamp)"
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS fast_stroke_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    facialDrooping INTEGER NOT NULL,
                    armWeakness INTEGER NOT NULL,
                    speechDifficulty INTEGER NOT NULL,
                    notes TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_fast_stroke_events_timestamp " +
                    "ON fast_stroke_events (timestamp)"
            )
        }
    }
}
