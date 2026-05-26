package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database schema migration for the owner-administered ESAS-r symptom
 * report (issue #185). Extracted from [BiosDatabase] to keep the
 * single-file size under the 500-line cap.
 *
 * Edmonton Symptom Assessment System — revised (Watanabe 2011
 * J Pain Symptom Manage 41(2):456-468). Nine 0-10 NRS items plus an
 * optional owner-named "other" symptom. Pull-side only; never inferred.
 *
 * Renumbered from 15→16 during merge with main: slot 15→16 was taken by
 * goals_of_care (#184), slot 16→17 by neurology (#207), so ESAS lands
 * at 17→18.
 */
internal object EsasMigrations {

    val MIGRATION_17_18: Migration = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS esas_reports (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    painScore INTEGER NOT NULL,
                    tirednessScore INTEGER NOT NULL,
                    drowsinessScore INTEGER NOT NULL,
                    nauseaScore INTEGER NOT NULL,
                    appetiteScore INTEGER NOT NULL,
                    shortnessOfBreathScore INTEGER NOT NULL,
                    depressionScore INTEGER NOT NULL,
                    anxietyScore INTEGER NOT NULL,
                    wellbeingScore INTEGER NOT NULL,
                    otherSymptomLabel TEXT,
                    otherSymptomScore INTEGER,
                    note TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_esas_reports_timestamp " +
                    "ON esas_reports (timestamp)"
            )
        }
    }
}
