package com.bios.app.physiology

import android.content.Context

/**
 * Persists the owner's most recent FRAIL questionnaire (Morley 2012)
 * responses and propagates the resulting category into
 * [PhysiologyStateStore].
 *
 * Storage is shared-prefs (single most-recent assessment, owner can
 * re-take any time). Closes the unwired-`FRAILTY_FLAG` half of the
 * geriatrics/palliative audit (GERIATRICS_PALLIATIVE_POV.md §2.2).
 *
 * Manifesto guard:
 *  - The owner administers the questionnaire. Bios does not infer.
 *  - On a FRAIL result, [PhysiologyStateStore] is set to
 *    [PhysiologyState.FRAILTY_FLAG] — patterns that false-fire on the
 *    frail-elderly baseline (sleep_disruption, cardiovascular_stress,
 *    cardiorespiratory_deconditioning, recovery_deficit) are gated out.
 *  - On a ROBUST or PRE_FRAIL result, the physiology state is *only*
 *    cleared back to [PhysiologyState.STANDARD] if it was previously
 *    [PhysiologyState.FRAILTY_FLAG]. We never overwrite a pregnancy,
 *    postpartum, athlete, or paediatric state the owner has chosen.
 *  - No notification fires on the result. The owner sees their score on
 *    the assessment screen they navigated to; that is the only surface.
 */
class FrailAssessmentStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE,
    )

    private val physiologyStore = PhysiologyStateStore(context)

    /** Returns the most recently recorded responses, or null if none yet. */
    fun latest(): FrailResponses? {
        if (!prefs.contains(KEY_TIMESTAMP)) return null
        return FrailResponses(
            fatigueMostOfTime = prefs.getBoolean(KEY_FATIGUE, false),
            cannotClimbTenSteps = prefs.getBoolean(KEY_RESISTANCE, false),
            cannotWalkSeveralHundredYards = prefs.getBoolean(KEY_AMBULATION, false),
            illnessCount = prefs.getInt(KEY_ILLNESSES, 0).coerceIn(0, 11),
            unintentionalWeightLossOver5Percent = prefs.getBoolean(KEY_WEIGHT_LOSS, false),
        )
    }

    /** Unix-epoch millis of the most recent recording, or null if none. */
    fun latestTimestampMillis(): Long? =
        if (prefs.contains(KEY_TIMESTAMP)) prefs.getLong(KEY_TIMESTAMP, 0L) else null

    /**
     * Persists [responses], computes the FRAIL category, and propagates
     * to [PhysiologyStateStore]. Returns the resulting [FrailCategory]
     * for the caller to display.
     */
    fun record(
        responses: FrailResponses,
        nowMillis: Long = System.currentTimeMillis(),
    ): FrailCategory {
        prefs.edit()
            .putBoolean(KEY_FATIGUE, responses.fatigueMostOfTime)
            .putBoolean(KEY_RESISTANCE, responses.cannotClimbTenSteps)
            .putBoolean(KEY_AMBULATION, responses.cannotWalkSeveralHundredYards)
            .putInt(KEY_ILLNESSES, responses.illnessCount)
            .putBoolean(KEY_WEIGHT_LOSS, responses.unintentionalWeightLossOver5Percent)
            .putLong(KEY_TIMESTAMP, nowMillis)
            .apply()

        val category = FrailScore.category(responses)
        syncPhysiologyState(category)
        return category
    }

    /** Removes the stored assessment; clears FRAILTY_FLAG if it was set. */
    fun clear() {
        prefs.edit().clear().apply()
        if (physiologyStore.current() == PhysiologyState.FRAILTY_FLAG) {
            physiologyStore.set(PhysiologyState.STANDARD)
        }
    }

    private fun syncPhysiologyState(category: FrailCategory) {
        val current = physiologyStore.current()
        when (category) {
            FrailCategory.FRAIL -> {
                // Set FRAILTY_FLAG only if the owner is currently in
                // STANDARD or already-FRAILTY_FLAG. Don't clobber a
                // pregnancy / postpartum / athlete / paediatric state
                // they explicitly picked elsewhere.
                if (current == PhysiologyState.STANDARD || current == PhysiologyState.FRAILTY_FLAG) {
                    physiologyStore.set(PhysiologyState.FRAILTY_FLAG)
                }
            }
            FrailCategory.ROBUST, FrailCategory.PRE_FRAIL -> {
                // Only revert if FRAILTY_FLAG is what's currently set.
                if (current == PhysiologyState.FRAILTY_FLAG) {
                    physiologyStore.set(PhysiologyState.STANDARD)
                }
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "bios_frail_assessment"
        const val KEY_TIMESTAMP = "timestamp_millis"
        const val KEY_FATIGUE = "fatigue"
        const val KEY_RESISTANCE = "resistance"
        const val KEY_AMBULATION = "ambulation"
        const val KEY_ILLNESSES = "illness_count"
        const val KEY_WEIGHT_LOSS = "weight_loss"
    }
}
