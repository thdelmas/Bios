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
 *
 * Also holds the optional [CancerTherapyDrugClass] annotation (#201) for
 * owners in a `CANCER_TREATMENT` state. Default is [CancerTherapyDrugClass.NONE].
 * Drug class is independent of state — clearing the state clears the
 * class so a previous regimen doesn't bleed into a new context.
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
        // Leaving a cancer-treatment state clears the drug-class annotation
        // so a past regimen doesn't bleed into an unrelated future context.
        if (state !in PhysiologyState.CANCER_TREATMENT) {
            prefs.edit().remove(KEY_DRUG_CLASS).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_STATE).remove(KEY_DRUG_CLASS).apply()
    }

    /**
     * Owner-declared drug class while in a `CANCER_TREATMENT` state (#201).
     * Returns [CancerTherapyDrugClass.NONE] when no class is set or the
     * stored value is unknown to this build (forward-compat).
     */
    fun drugClass(): CancerTherapyDrugClass = prefs.getString(KEY_DRUG_CLASS, null)
        ?.let { runCatching { CancerTherapyDrugClass.valueOf(it) }.getOrNull() }
        ?: CancerTherapyDrugClass.NONE

    fun setDrugClass(drugClass: CancerTherapyDrugClass) {
        prefs.edit().putString(KEY_DRUG_CLASS, drugClass.name).apply()
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
        const val KEY_DRUG_CLASS = "drug_class"
    }
}
