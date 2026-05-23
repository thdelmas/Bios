package com.bios.app.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent log of phone-state observations that
 * [com.bios.app.engine.PhoneSleepInference] consumes once per morning to
 * derive `SLEEP_DURATION` + `SLEEP_STAGE` readings (issue #134).
 *
 * Rows are tiny and the periodic worker writes ~96/day (every 15 min);
 * old rows get pruned by [com.bios.app.data.dao.PhoneSleepSampleDao]
 * after inference runs. Indexed by `timestamp` because every read fetches
 * an overnight window.
 *
 * The schema mirrors [com.bios.app.engine.PhoneSleepInference.Sample]
 * 1:1 so the worker maps row→Sample without translation logic. The
 * Tier-1 expansion fields (#243 Cut 1) are nullable for backward-
 * compatible reads of rows written before the migration landed.
 */
@Entity(
    tableName = "phone_sleep_samples",
    indices = [Index("timestamp")],
)
data class PhoneSleepSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val screenOff: Boolean,
    val charging: Boolean,
    val ambientLightLux: Float?,
    val accelMagnitudeVar: Float?,
    // #243 Cut 1 — Tier-1 sensor-surface expansion. All nullable so a
    // sample row from a device missing the underlying signal can still
    // be persisted; the inference treats null as "ignore."
    val stepDelta: Int? = null,
    val dndEnabled: Boolean? = null,
    val pairedBluetoothConnected: Boolean? = null,
)
