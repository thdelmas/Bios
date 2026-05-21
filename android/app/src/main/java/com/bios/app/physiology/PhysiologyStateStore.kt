package com.bios.app.physiology

import android.content.Context

/**
 * Owner-set [PhysiologyState] persisted in shared-prefs (#159).
 * Default is [PhysiologyState.STANDARD] until the owner picks something
 * else from Settings → Physiology state.
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

    private companion object {
        const val KEY_STATE = "state"
    }
}
