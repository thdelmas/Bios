package com.bios.app.physiology

import com.bios.app.data.BiosDatabase

/**
 * Captures a frozen [PerioperativeBaseline] snapshot from the owner's
 * current rolling [com.bios.app.model.PersonalBaseline] rows
 * (#183, SURGICAL_POV §2.1).
 *
 * Called from the Settings UI when the owner enters their procedure
 * date — either upcoming (which puts them in [PhysiologyState.PREHAB_WINDOW])
 * or recently past (which puts them in [PhysiologyState.POD_0_30]). The
 * snapshot is taken from whatever the 14-day rolling baseline currently
 * holds, which captures the owner's stable-state physiology at that
 * moment.
 *
 * No inference: the snapshot is just a read of the existing baselines.
 * Bios does not estimate, interpolate, or judge — it freezes what is
 * there.
 */
class PerioperativeBaselineFreezer(private val db: BiosDatabase) {

    /**
     * Capture a snapshot and persist it. Replaces any prior snapshot
     * (single-row table by convention). Returns the persisted entity
     * for inspection / UI feedback.
     */
    suspend fun freeze(
        procedureDate: Long,
        procedureTag: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): PerioperativeBaseline {
        val baselineDao = db.personalBaselineDao()
        val snapshot = PerioperativeBaseline(
            procedureDate = procedureDate,
            procedureTag = procedureTag,
            snapshotAt = nowMillis,
            restingHeartRateMean = baselineDao.fetch("resting_heart_rate")?.mean,
            restingHeartRateSd = baselineDao.fetch("resting_heart_rate")?.stdDev,
            heartRateVariabilityMean = baselineDao.fetch("heart_rate_variability")?.mean,
            heartRateVariabilitySd = baselineDao.fetch("heart_rate_variability")?.stdDev,
            skinTemperatureMean = baselineDao.fetch("skin_temperature_deviation")?.mean
                ?: baselineDao.fetch("skin_temperature")?.mean,
            skinTemperatureSd = baselineDao.fetch("skin_temperature_deviation")?.stdDev
                ?: baselineDao.fetch("skin_temperature")?.stdDev,
            respiratoryRateMean = baselineDao.fetch("respiratory_rate")?.mean,
            respiratoryRateSd = baselineDao.fetch("respiratory_rate")?.stdDev,
            bloodOxygenMean = baselineDao.fetch("blood_oxygen")?.mean,
            bloodOxygenSd = baselineDao.fetch("blood_oxygen")?.stdDev,
            sleepDurationMean = baselineDao.fetch("sleep_duration")?.mean,
            sleepDurationSd = baselineDao.fetch("sleep_duration")?.stdDev,
            sleepEfficiencyMean = baselineDao.fetch("sleep_efficiency")?.mean,
            sleepEfficiencySd = baselineDao.fetch("sleep_efficiency")?.stdDev,
            stepsMean = baselineDao.fetch("steps")?.mean,
            stepsSd = baselineDao.fetch("steps")?.stdDev,
            bodyMassMean = baselineDao.fetch("body_mass")?.mean,
            bodyMassSd = baselineDao.fetch("body_mass")?.stdDev,
        )
        db.perioperativeBaselineDao().upsert(snapshot)
        return snapshot
    }

    /** Clear the frozen snapshot. Owner-initiated when they exit peri-op context. */
    suspend fun clear() {
        db.perioperativeBaselineDao().deleteAll()
    }
}
