package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Sidecar file for Bios DB migrations that would push [BiosDatabase] over
 * the 500-line file cap. The migrations in this object are referenced
 * from [BiosDatabase.Companion.buildDatabase]'s `addMigrations` call.
 *
 * New migrations from this point forward should land here rather than in
 * [BiosDatabase] itself, until the existing pre-#192 migrations are
 * gradually consolidated.
 */
internal object BiosDatabaseMigrations {

    /**
     * Owner-recorded intervention events + treatment courses (#192).
     * Generic primitive for tradition-medicine and modern-clinical
     * session recording — acupuncture, OMM, cupping, Panchakarma,
     * cardiac rehab, etc. `InterventionEvent → TreatmentCourse` FK is
     * `SET NULL` on delete so removing a course preserves session rows.
     *
     * NOTE on version numbering: several audit-derived PRs are
     * concurrently bumping the DB version. This migration claims
     * 24→25; if a sibling PR lands first, rebase by renumbering both
     * the `@Database(version)` annotation and the `Migration(24, 25)`
     * constructor below to the next free slot. The schema body does
     * not depend on the version number.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS treatment_courses (
                    id TEXT NOT NULL PRIMARY KEY,
                    label TEXT NOT NULL,
                    tradition TEXT,
                    startedAt INTEGER NOT NULL,
                    expectedEndAt INTEGER,
                    actualEndAt INTEGER,
                    goal TEXT,
                    notes TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_treatment_courses_startedAt " +
                    "ON treatment_courses (startedAt)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_treatment_courses_actualEndAt " +
                    "ON treatment_courses (actualEndAt)"
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS intervention_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    subType TEXT,
                    practitionerTradition TEXT,
                    notes TEXT,
                    bodyRegion TEXT,
                    treatmentCourseId TEXT,
                    FOREIGN KEY (treatmentCourseId) REFERENCES treatment_courses(id) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_intervention_events_timestamp " +
                    "ON intervention_events (timestamp)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_intervention_events_category " +
                    "ON intervention_events (category)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_intervention_events_treatmentCourseId " +
                    "ON intervention_events (treatmentCourseId)"
            )
        }
    }
}
