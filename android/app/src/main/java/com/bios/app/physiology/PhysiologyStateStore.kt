package com.bios.app.physiology

import android.content.Context

/**
 * Owner-set [PhysiologyState] persisted in shared-prefs (#159).
 * Default is [PhysiologyState.STANDARD] until the owner picks something
 * else from Settings → Physiology state.
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

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_DRUG_CLASS = "drug_class"
    }
}
