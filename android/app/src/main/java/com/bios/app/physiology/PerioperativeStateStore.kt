package com.bios.app.physiology

import android.content.Context

/**
 * Owner-set peri-operative context — the procedure date and (optional)
 * procedure tag that drive [PhysiologyState] transitions across the
 * peri-op envelope (#183, SURGICAL_POV.md §2.1).
 *
 * Two persistence surfaces share the peri-op state machine:
 *
 *  - [PhysiologyStateStore] holds the currently-active [PhysiologyState].
 *    A WorkManager periodic worker reads this store's procedureDate and
 *    advances the state ([PhysiologyState.POD_0_30] →
 *    [PhysiologyState.POD_30_90] → [PhysiologyState.POD_90_PLUS]) based
 *    on days-since-procedure.
 *  - [PerioperativeBaseline] (encrypted Room row) holds the frozen
 *    pre-op vital-sign baseline. Captured once at PREHAB_WINDOW or
 *    POD_0_30 entry; never updated.
 *
 * This store holds the date + tag in standard SharedPreferences (not
 * encrypted), mirroring how [PhysiologyStateStore] persists the state
 * enum itself. The owner enters this surface explicitly via Settings →
 * Identity → Surgical recovery.
 *
 * Manifesto guard: Bios never *infers* a procedure. The owner picks the
 * date and (optionally) labels the procedure.
 */
class PerioperativeStateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE,
    )

    /** Owner-set procedure date (epoch millis at local midnight), or null. */
    fun procedureDate(): Long? =
        prefs.getLong(KEY_PROCEDURE_DATE, -1L).takeIf { it >= 0L }

    /** Owner-supplied procedure tag, or null. Free text; never used to branch logic. */
    fun procedureTag(): String? = prefs.getString(KEY_PROCEDURE_TAG, null)

    fun setProcedure(dateMillis: Long, tag: String? = null) {
        prefs.edit()
            .putLong(KEY_PROCEDURE_DATE, dateMillis)
            .apply {
                if (tag.isNullOrBlank()) remove(KEY_PROCEDURE_TAG) else putString(KEY_PROCEDURE_TAG, tag)
            }
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_PROCEDURE_DATE)
            .remove(KEY_PROCEDURE_TAG)
            .apply()
    }

    /**
     * Determine which [PhysiologyState] the owner should be in given the
     * stored procedureDate at [nowMillis]. Returns null when no procedure
     * is recorded.
     */
    fun expectedState(nowMillis: Long = System.currentTimeMillis()): PhysiologyState? {
        val date = procedureDate() ?: return null
        return stateForProcedureDate(date, nowMillis)
    }

    companion object {
        const val PREFS_NAME = "bios_perioperative_state"
        const val KEY_PROCEDURE_DATE = "procedure_date"
        const val KEY_PROCEDURE_TAG = "procedure_tag"
        const val MILLIS_PER_DAY: Long = 24L * 3600L * 1000L
        const val POD_0_30_DAYS: Long = 30L
        const val POD_30_90_DAYS: Long = 90L

        /**
         * Pure-function variant of [expectedState] — exposed for testing
         * the day-boundary mapping without needing a Context-backed
         * SharedPreferences.
         *
         * Mapping:
         *  - Before procedureDate: [PhysiologyState.PREHAB_WINDOW]
         *  - POD 0-30:             [PhysiologyState.POD_0_30]
         *  - POD 31-90:            [PhysiologyState.POD_30_90]
         *  - POD 91+:              [PhysiologyState.POD_90_PLUS]
         */
        fun stateForProcedureDate(
            procedureDateMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
        ): PhysiologyState {
            val diff = nowMillis - procedureDateMillis
            if (diff < 0L) return PhysiologyState.PREHAB_WINDOW
            val days = diff / MILLIS_PER_DAY
            return when {
                days <= POD_0_30_DAYS -> PhysiologyState.POD_0_30
                days <= POD_30_90_DAYS -> PhysiologyState.POD_30_90
                else -> PhysiologyState.POD_90_PLUS
            }
        }
    }
}
