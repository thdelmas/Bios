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
    val MIGRATION_23_24: Migration = object : Migration(23, 24) {
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

    /**
     * Optional clinical coding on `health_events` (#357). Three nullable
     * columns so a coded diagnosis (ICD-10 / SNOMED CT / other vocabulary)
     * can travel alongside the free-text title. Default null preserves all
     * existing rows untouched.
     */
    val MIGRATION_32_33: Migration = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE health_events ADD COLUMN codeSystem TEXT")
            db.execSQL("ALTER TABLE health_events ADD COLUMN code TEXT")
            db.execSQL("ALTER TABLE health_events ADD COLUMN codeDisplay TEXT")
        }
    }

    /**
     * Owner-recorded allergies and intolerances (#355). Audit gap surfaced
     * by the Sagrat Cor discharge — every clinical encounter starts with an
     * allergies line. Hard-delete is allowed for this table; no soft-delete
     * columns.
     */
    val MIGRATION_33_34: Migration = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS allergy_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    substance TEXT NOT NULL,
                    substanceCode TEXT,
                    substanceSource TEXT,
                    reactionType TEXT NOT NULL,
                    severity TEXT NOT NULL,
                    manifestation TEXT,
                    onsetDate INTEGER,
                    verifiedByClinician INTEGER NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_allergy_records_substance ON allergy_records(substance)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_allergy_records_verifiedByClinician ON allergy_records(verifiedByClinician)")
        }
    }

    /**
     * Owner-recorded imaging studies (#356). Stores radiology-report text
     * (technique / findings / conclusion) plus modality and body region.
     * DICOM blob storage is explicitly out of scope; this table is
     * text-report-only. Hard-delete allowed.
     */
    val MIGRATION_34_35: Migration = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imaging_studies (
                    uuid TEXT PRIMARY KEY NOT NULL,
                    modality TEXT NOT NULL,
                    bodyRegion TEXT NOT NULL,
                    studyDate INTEGER NOT NULL,
                    indication TEXT,
                    facility TEXT,
                    reportTechnique TEXT,
                    reportFindings TEXT,
                    reportConclusion TEXT,
                    reportLanguage TEXT,
                    ownerNote TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_imaging_studies_studyDate ON imaging_studies(studyDate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_imaging_studies_modality ON imaging_studies(modality)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_imaging_studies_bodyRegion ON imaging_studies(bodyRegion)")
        }
    }
}
