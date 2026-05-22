package com.bios.app.physiology

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Owner-set [PhysiologyState] persisted in shared-prefs (#159).
 * Default is [PhysiologyState.STANDARD] until the owner picks something
 * else from Settings → Physiology state.
 *
 * Paediatric extension (#198): when the owner supplies a birth date, the
 * store also persists it (ISO `yyyy-MM-dd`) and the band is recomputed
 * daily by [PaediatricBandWorker] from that date. The owner can revoke
 * either the band or the birth date independently. Birth date is the
 * only owner-supplied input that drives automatic state selection;
 * Bios never infers paediatric band from biomarkers or wearables.
 */
class PhysiologyStateStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "bios_physiology_state", Context.MODE_PRIVATE,
    )

    fun current(): PhysiologyState = prefs.getString(KEY_STATE, null)
        ?.let { runCatching { PhysiologyState.valueOf(it) }.getOrNull() }
        ?: PhysiologyState.STANDARD

    fun set(state: PhysiologyState) {
        prefs.edit().putString(KEY_STATE, state.name).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_STATE).apply()
    }

    /**
     * Owner-supplied birth date, or `null` if none recorded. Persisted as
     * ISO `yyyy-MM-dd`; corrupt values are returned as `null`. Used by
     * [PaediatricBandWorker] to recompute the paediatric band when the
     * owner crosses an age boundary.
     */
    fun birthDate(): LocalDate? = prefs.getString(KEY_BIRTH_DATE, null)
        ?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /**
     * Record the owner's birth date. Pass `null` to clear it. Setting a
     * birth date does not by itself change the [PhysiologyState] — call
     * [PaediatricBandWorker.runOnce] or wait for the daily worker to
     * compute the band.
     */
    fun setBirthDate(date: LocalDate?) {
        val editor = prefs.edit()
        if (date == null) editor.remove(KEY_BIRTH_DATE)
        else editor.putString(KEY_BIRTH_DATE, date.toString())
        editor.apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_BIRTH_DATE = "birth_date"
    }
}
