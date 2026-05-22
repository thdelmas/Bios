package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Out-of-line migrations referenced by [BiosDatabase]. The primary
 * `BiosDatabase` file is approaching the 500-line cap; #191 adds the
 * hereditary-syndrome columns to `risk_profile`, so the migration
 * definition lives here to keep both files within budget.
 */
internal object BiosDatabaseMigrationsExtras {

    /**
     * Owner-declared hereditary cancer syndromes + IHS diabetic AI/AN
     * status (#191). Extends the `risk_profile` single-row table so the
     * screening-cadence engine can gate Lynch / FAP / Li-Fraumeni /
     * Cowden / Peutz-Jeghers / VHL / MEN1 / MEN2A / MEN2B / HDGC / IHS
     * HbA1c cadences against owner-asserted genetic-test outcomes.
     *
     * All new columns default to 0 (false) so the previous row keeps
     * its existing risk flags intact across the upgrade.
     */
    val MIGRATION_25_26: Migration = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val newBooleans = listOf(
                "lynchSyndrome",
                "liFraumeni",
                "fapFamilialAdenomatousPolyposis",
                "cowdenSyndrome",
                "peutzJeghersSyndrome",
                "vhlVonHippelLindau",
                "men1",
                "men2A",
                "men2B",
                "hdgcHereditaryDiffuseGastric",
                "ihsDiabeticAianStatus",
            )
            for (col in newBooleans) {
                db.execSQL(
                    "ALTER TABLE risk_profile ADD COLUMN $col INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
