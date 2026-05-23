package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object ProfessionalReviewMigrations {
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS professional_reviews (
                    id TEXT NOT NULL PRIMARY KEY,
                    anomalyId TEXT NOT NULL,
                    requestedAt INTEGER NOT NULL,
                    status INTEGER NOT NULL,
                    shareMethod TEXT,
                    sharedMetrics TEXT,
                    sharedWindowDays INTEGER,
                    sharedExplanation INTEGER NOT NULL,
                    sharedBaselines INTEGER NOT NULL,
                    respondedAt INTEGER,
                    professionalNotes TEXT,
                    clinicallyRelevant INTEGER,
                    recommendation TEXT,
                    ownerFoundHelpful INTEGER,
                    FOREIGN KEY (anomalyId) REFERENCES anomalies(id) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_anomalyId ON professional_reviews(anomalyId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_status ON professional_reviews(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_professional_reviews_requestedAt ON professional_reviews(requestedAt)")
        }
    }
}
