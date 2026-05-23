package com.bios.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for the `phone_sleep_samples` table (#134 base,
 * #243 Cut 1 expansion). Extracted from [BiosDatabase] to keep the
 * host file under the 500-line cap; the original `CREATE TABLE` lives
 * inline as `MIGRATION_10_11` and is left there to preserve the
 * original chronology.
 */
internal object PhoneSleepSampleMigrations {

    // Tier-1 phone-sleep sensor-surface expansion (#243 Cut 1). Three
    // nullable columns added to phone_sleep_samples: stepDelta
    // (TYPE_STEP_COUNTER delta since previous sample), dndEnabled
    // (NotificationManager.currentInterruptionFilter), and
    // pairedBluetoothConnected (BluetoothManager getConnectedDevices
    // across HEADSET / A2DP profiles). Nullable so existing rows and
    // devices missing the underlying signal keep working unchanged;
    // the inference treats null as "ignore" until the Cut 2 decision-
    // side weave lands.
    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE phone_sleep_samples ADD COLUMN stepDelta INTEGER")
            db.execSQL("ALTER TABLE phone_sleep_samples ADD COLUMN dndEnabled INTEGER")
            db.execSQL("ALTER TABLE phone_sleep_samples ADD COLUMN pairedBluetoothConnected INTEGER")
        }
    }
}
