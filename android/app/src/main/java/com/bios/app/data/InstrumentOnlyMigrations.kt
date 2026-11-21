package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema steps of docs/specs/instrument-only.md: Bios measures and shows,
 * and stored rows stop carrying interpretation text.
 */
internal object InstrumentOnlyMigrations {

    /** Alert identifier of the one remaining kind of alert; mirrors AnomalyDetector.ML_PATTERN_ID. */
    const val DEVIATION_ALERT_ID = "ml_holistic_anomaly"

    const val DEVIATION_TITLE = "Outside your usual range"
    const val EARLIER_TITLE = "Earlier alert"
    const val EARLIER_EXPLANATION =
        "Recorded by an earlier version. Its interpretation text was removed; the measured values are kept."

    /**
     * 38 to 39. Drops the professional-review table. Alerts raised by a
     * removed pattern keep every measured column, the snapshot, the tier, the
     * acknowledgement and the journal; only the copied title, explanation and
     * next step are replaced. Deviation alerts keep their measured lines and
     * take the current title.
     */
    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS professional_reviews")
            db.execSQL(
                "UPDATE anomalies SET title = ?, explanation = ?, suggestedAction = NULL " +
                    "WHERE patternId IS NULL OR patternId != ?",
                arrayOf<Any>(EARLIER_TITLE, EARLIER_EXPLANATION, DEVIATION_ALERT_ID),
            )
            db.execSQL(
                "UPDATE anomalies SET title = ?, suggestedAction = NULL WHERE patternId = ?",
                arrayOf<Any>(DEVIATION_TITLE, DEVIATION_ALERT_ID),
            )
        }
    }

    /**
     * Tables of the main database that no entity maps any more. They hold
     * what the owner entered on screens that were removed, so they are left
     * in the file with their rows. Room does not validate tables it has no
     * entity for.
     */
    val KEPT_TABLES = listOf(
        "screening_entries", "risk_profile", "goals_of_care", "clinical_directive",
        "fast_stroke_events", "esas_reports", "emergency_contacts", "perioperative_baselines",
    )

    /** The same, for the reproductive database. */
    val KEPT_REPRODUCTIVE_TABLES = listOf("menopause_stage_entries", "gender_affirming_care_entries")

    /**
     * 39 to 40. Eight entities leave the schema; see [KEPT_TABLES]. The step
     * runs no statement: no table is dropped and no row is touched.
     */
    val MIGRATION_39_40 = schemaOnlyStep(39, 40)

    /**
     * Reproductive database, 5 to 6. Two entities leave the schema; see
     * [KEPT_REPRODUCTIVE_TABLES]. The step runs no statement.
     */
    val REPRODUCTIVE_MIGRATION_5_6 = schemaOnlyStep(5, 6)

    /** A version step that only changes which tables Room maps. */
    private fun schemaOnlyStep(from: Int, to: Int) = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }
}
