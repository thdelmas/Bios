package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 31 → 32 — per-(source, metric) ingest toggle table. Adds a new table
// only; existing rows are not touched. Row absence = default (enabled),
// so the migration is invisible to any owner who never opens the new
// settings surface.
internal object SourceMetricToggleMigrations {

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS source_metric_toggles (
                    sourceTypeKey TEXT NOT NULL,
                    metricTypeKey TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    PRIMARY KEY (sourceTypeKey, metricTypeKey)
                )
                """.trimIndent()
            )
        }
    }
}
