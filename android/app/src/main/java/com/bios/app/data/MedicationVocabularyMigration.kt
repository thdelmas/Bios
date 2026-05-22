package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration for issue #194 — substance vocabulary columns on the
 * `medication_annotations` table. Lives outside [BiosDatabase] only so
 * that file stays under the 500-line project ceiling.
 *
 * Adds three nullable columns:
 *  - `substanceSource` — name of a [SubstanceSource] enum value, or null
 *    for the legacy free-text path.
 *  - `substanceCode` — owner-entered code drawn from that vocabulary; not
 *    validated against any external service.
 *  - `traditionalName` — owner's traditional / vernacular name in their
 *    preferred script (Devanagari, Hanzi, Hangul, Arabic, …).
 *
 * Existing rows back-fill to `NULL` on all three; readers map a null
 * `substanceSource` to [SubstanceSource.LOCAL_FREE_TEXT], which is the
 * default the pre-#194 free-text path used implicitly.
 */
internal object MedicationVocabularyMigration {

    val MIGRATION_19_20: Migration = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medication_annotations ADD COLUMN substanceSource TEXT")
            db.execSQL("ALTER TABLE medication_annotations ADD COLUMN substanceCode TEXT")
            db.execSQL("ALTER TABLE medication_annotations ADD COLUMN traditionalName TEXT")
        }
    }
}
