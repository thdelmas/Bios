package com.bios.app.physiology

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Frozen pre-op snapshot of an owner's vital-sign baselines, captured at
 * the moment they enter [PhysiologyState.PREHAB_WINDOW] (or, if they were
 * not in prehab, at the transition into [PhysiologyState.POD_0_30]).
 *
 * SURGICAL_POV.md §2.1 — the rolling 14-day
 * [com.bios.app.model.PersonalBaseline] is correct for a stable adult, but
 * it's the wrong reference for a post-operative owner: their RHR runs
 * 10-20 bpm above pre-op for 2-4 weeks (normative recovery), so the
 * rolling baseline drifts upward and any pattern using it will either
 * false-fire constantly or, if dialed down, miss the genuine re-elevation
 * at POD7-14 that flags a SSI.
 *
 * The peri-operative baseline is a **frozen reference**: captured once at
 * pre-op time, never updated, used by `ssi_surveillance`,
 * `anastomotic_leak_screen`, and `post_op_vte_screen` as the
 * stable-physiology anchor against which post-op deviations are evaluated.
 * The rolling [com.bios.app.model.PersonalBaseline] keeps rolling for
 * everything else.
 *
 * Captures the audit-listed primitives: RHR, HRV, skin temp, respiratory
 * rate, SpO2, sleep duration, sleep efficiency, steps, weight. Biomarkers
 * (HbA1c, hemoglobin, ferritin, albumin) live in the existing biomarker
 * schema as their own dated `MetricReading` rows — pre-op values are
 * recoverable by querying the latest pre-`procedureDate` reading. They
 * are intentionally NOT snapshotted here: biomarker values are dated by
 * their measurement, not by the procedure, and the FHIR-import path
 * already preserves them.
 *
 * Storage: single-row table in the main encrypted
 * [com.bios.app.data.BiosDatabase]. A second peri-op event (the owner
 * has another surgery) replaces the row — Bios does not currently maintain
 * a multi-procedure history, by design (manifesto-clean: the owner
 * annotates one procedure at a time).
 *
 * Manifesto guard: Bios never *infers* this snapshot. The owner enters
 * the procedure date via Settings → Identity → Surgical recovery, and the
 * snapshot is taken from existing baseline rows at that moment.
 */
@Entity(tableName = "perioperative_baselines")
data class PerioperativeBaseline(
    /**
     * Single-row table by convention (id = 1). A new peri-op event
     * replaces the row. If we later need per-procedure history, this
     * becomes an auto-increment with a procedureDate index.
     */
    @PrimaryKey val id: Int = SINGLETON_ID,

    /**
     * Owner-set procedure date (epoch millis, midnight local time). For
     * an upcoming operation this is the scheduled date; for a past
     * operation it is the actual date. The state-transition worker reads
     * this field to advance [PhysiologyState.POD_0_30] →
     * [PhysiologyState.POD_30_90] → [PhysiologyState.POD_90_PLUS] based
     * on days-since-procedure.
     */
    val procedureDate: Long,

    /**
     * Owner-supplied free-text procedure tag (e.g. "sigmoidectomy",
     * "total knee arthroplasty", "AAA repair"). Treated as a tag, not a
     * clinical classification — Bios does not branch its pattern selection
     * on the tag value. Provided as context for FHIR export and the
     * owner's own recall. Optional.
     */
    val procedureTag: String? = null,

    /** When this snapshot was taken (epoch millis). */
    val snapshotAt: Long = System.currentTimeMillis(),

    // -- Frozen pre-op vital-sign baseline means + SDs --
    // Nulls indicate the metric had no baseline at snapshot time
    // (insufficient pre-op data). Patterns that need a value MUST check
    // for null and skip the rule rather than dividing by a zero SD.

    val restingHeartRateMean: Double? = null,
    val restingHeartRateSd: Double? = null,

    val heartRateVariabilityMean: Double? = null,
    val heartRateVariabilitySd: Double? = null,

    val skinTemperatureMean: Double? = null,
    val skinTemperatureSd: Double? = null,

    val respiratoryRateMean: Double? = null,
    val respiratoryRateSd: Double? = null,

    val bloodOxygenMean: Double? = null,
    val bloodOxygenSd: Double? = null,

    val sleepDurationMean: Double? = null,
    val sleepDurationSd: Double? = null,

    val sleepEfficiencyMean: Double? = null,
    val sleepEfficiencySd: Double? = null,

    val stepsMean: Double? = null,
    val stepsSd: Double? = null,

    val bodyMassMean: Double? = null,
    val bodyMassSd: Double? = null,
) {

    /**
     * Compute a z-score for [value] against the frozen baseline for the
     * named metric. Returns 0.0 when the baseline is missing or has a
     * non-positive SD — mirrors [com.bios.app.model.PersonalBaseline.zScore].
     */
    fun zScore(metricKey: String, value: Double): Double {
        val (mean, sd) = when (metricKey) {
            "resting_heart_rate" -> restingHeartRateMean to restingHeartRateSd
            "heart_rate_variability" -> heartRateVariabilityMean to heartRateVariabilitySd
            "skin_temperature", "skin_temperature_deviation" ->
                skinTemperatureMean to skinTemperatureSd
            "respiratory_rate" -> respiratoryRateMean to respiratoryRateSd
            "blood_oxygen" -> bloodOxygenMean to bloodOxygenSd
            "sleep_duration" -> sleepDurationMean to sleepDurationSd
            "sleep_efficiency" -> sleepEfficiencyMean to sleepEfficiencySd
            "steps" -> stepsMean to stepsSd
            "body_mass" -> bodyMassMean to bodyMassSd
            else -> null to null
        }
        if (mean == null || sd == null || sd <= 0.0) return 0.0
        return (value - mean) / sd
    }

    /** Days since the recorded procedureDate, computed at [nowMillis]. */
    fun postOpDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val diff = nowMillis - procedureDate
        return diff / MILLIS_PER_DAY
    }

    /**
     * True when the snapshot has a usable (non-null mean + positive SD)
     * entry for the named metric. Mirrors the null-check logic in
     * [zScore] — callers need a separate "is the baseline present"
     * predicate because the z-score function returns 0.0 both when the
     * metric is missing AND when the value happens to equal the mean.
     */
    fun hasBaselineFor(metricKey: String): Boolean {
        val (mean, sd) = when (metricKey) {
            "resting_heart_rate" -> restingHeartRateMean to restingHeartRateSd
            "heart_rate_variability" -> heartRateVariabilityMean to heartRateVariabilitySd
            "skin_temperature", "skin_temperature_deviation" ->
                skinTemperatureMean to skinTemperatureSd
            "respiratory_rate" -> respiratoryRateMean to respiratoryRateSd
            "blood_oxygen" -> bloodOxygenMean to bloodOxygenSd
            "sleep_duration" -> sleepDurationMean to sleepDurationSd
            "sleep_efficiency" -> sleepEfficiencyMean to sleepEfficiencySd
            "steps" -> stepsMean to stepsSd
            "body_mass" -> bodyMassMean to bodyMassSd
            else -> null to null
        }
        return mean != null && sd != null && sd > 0.0
    }

    companion object {
        /** Single-row primary key. */
        const val SINGLETON_ID: Int = 1
        const val MILLIS_PER_DAY: Long = 24L * 3600L * 1000L
    }
}
