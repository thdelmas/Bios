package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for the traditional-medicine-context surface (#193).
 * Extracted from [BiosDatabase] to keep that file under the 500-line cap.
 *
 * Eleven tradition audits converge on this surface (TCM, Ayurveda,
 * Siddha, Sowa Rigpa, Unani, Korean, Kampo, African Traditional,
 * Indigenous Americas, Oceanic/Arctic, Modern Non-Allopathic). The
 * table is multi-row — the owner may have several rows, one per
 * tradition they practice within. Pull-side only; Bios never
 * classifies within any tradition.
 *
 * Renumbered from 16→17 during merge with main: slots 16→17 (neurology),
 * 17→18 (ESAS) were taken before this PR landed; lands at 18→19.
 */
internal object TraditionalMedicineMigrations {

    val MIGRATION_18_19: Migration = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS traditional_medicine_context (
                    id TEXT NOT NULL PRIMARY KEY,
                    tradition TEXT NOT NULL,
                    traditionFreeText TEXT,
                    practitionerConsultedFrequency TEXT,
                    notes TEXT,
                    regionOfPractice TEXT,
                    vocabularyOverlayConsent INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_traditional_medicine_context_tradition " +
                    "ON traditional_medicine_context (tradition)"
            )
        }
    }
}
