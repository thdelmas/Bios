package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migration that created the `emergency_contacts` table. No entity
 * maps the table any more; it stays in the file with its rows.
 */
internal object EmergencyContactMigrations {
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS emergency_contacts (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    relationship TEXT NOT NULL,
                    phoneNumber TEXT NOT NULL,
                    minTierLevel INTEGER NOT NULL,
                    acknowledgementTimeoutMinutes INTEGER,
                    createdAt INTEGER NOT NULL,
                    note TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_emergency_contacts_createdAt ON emergency_contacts (createdAt)",
            )
        }
    }
}
