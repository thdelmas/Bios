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
}
