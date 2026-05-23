package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations that pertain to anthropometry / paediatric growth tracking
 * (#199, audit gap §2.7).
 *
 * Lives outside [BiosDatabase] so the main database file stays under the
 * 500-line cap as new migrations land. The migration registry in
 * `BiosDatabase.buildDatabase` references these by name; the migration
 * object is otherwise indistinguishable from one defined inline.
 */
internal object MigrationsAnthropometry {

    /**
     * Adds the [com.bios.app.model.GrowthMeasurement] table. Stores
     * anthropometric measurement events (height + weight + head
     * circumference + derived BMI + lean / fat mass) so the growth-chart
     * percentile engine can plot WHO / CDC curves and the sarcopenia /
     * cachexia screens can detect declining trajectories. Mainline
     * encrypted DB — anthropometry is not cycle-linked and stays alongside
     * the rest of the owner's data.
     */
    val MIGRATION_25_26: Migration = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS growth_measurements (
                    id TEXT NOT NULL PRIMARY KEY,
                    timestamp INTEGER NOT NULL,
                    ageInDays INTEGER NOT NULL,
                    heightCm REAL,
                    weightKg REAL,
                    headCircumferenceCm REAL,
                    bmiKgPerM2 REAL,
                    leanBodyMassKg REAL,
                    fatMassKg REAL,
                    growthChartReference TEXT,
                    note TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_growth_measurements_timestamp " +
                    "ON growth_measurements (timestamp)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_growth_measurements_ageInDays " +
                    "ON growth_measurements (ageInDays)"
            )
        }
    }
}
